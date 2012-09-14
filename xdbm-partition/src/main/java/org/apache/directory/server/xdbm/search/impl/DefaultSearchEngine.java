/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * 
 */
package org.apache.directory.server.xdbm.search.impl;


import java.util.HashSet;
import java.util.Set;

import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.IndexCursorAdaptor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.server.xdbm.search.Optimizer;
import org.apache.directory.server.xdbm.search.PartitionSearchResult;
import org.apache.directory.server.xdbm.search.SearchEngine;
import org.apache.directory.shared.ldap.model.cursor.Cursor;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.model.filter.AndNode;
import org.apache.directory.shared.ldap.model.filter.BranchNode;
import org.apache.directory.shared.ldap.model.filter.ExprNode;
import org.apache.directory.shared.ldap.model.filter.ScopeNode;
import org.apache.directory.shared.ldap.model.message.AliasDerefMode;
import org.apache.directory.shared.ldap.model.message.SearchScope;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.apache.directory.shared.ldap.model.schema.SchemaManager;


/**
 * Given a search filter and a scope the search engine identifies valid
 * candidate entries returning their ids.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DefaultSearchEngine implements SearchEngine
{
    /** the Optimizer used by this DefaultSearchEngine */
    private final Optimizer optimizer;

    /** the Database this DefaultSearchEngine operates on */
    private final Store db;

    /** creates Cursors over entries satisfying filter expressions */
    private final CursorBuilder cursorBuilder;

    /** creates evaluators which check to see if candidates satisfy a filter expression */
    private final EvaluatorBuilder evaluatorBuilder;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a DefaultSearchEngine for searching a Database without setting
     * up the database.
     * @param db the btree based partition
     * @param cursorBuilder an expression cursor builder
     * @param evaluatorBuilder an expression evaluator builder
     * @param optimizer an optimizer to use during search
     */
    public DefaultSearchEngine( Store db, CursorBuilder cursorBuilder,
        EvaluatorBuilder evaluatorBuilder, Optimizer optimizer )
    {
        this.db = db;
        this.optimizer = optimizer;
        this.cursorBuilder = cursorBuilder;
        this.evaluatorBuilder = evaluatorBuilder;
    }


    /**
     * Gets the optimizer for this DefaultSearchEngine.
     *
     * @return the optimizer
     */
    public Optimizer getOptimizer()
    {
        return optimizer;
    }


    /**
     * {@inheritDoc}
     */
    public PartitionSearchResult computeResult( SchemaManager schemaManager, SearchOperationContext searchContext )
        throws Exception
    {
        SearchScope scope = searchContext.getScope();
        Dn baseDn = searchContext.getDn();
        AliasDerefMode aliasDerefMode = searchContext.getAliasDerefMode();
        ExprNode filter = searchContext.getFilter();

        // Compute the UUID of the baseDN entry
        String baseId = db.getEntryId( baseDn );

        // Prepare the instance containing the search result
        PartitionSearchResult searchResult = new PartitionSearchResult( schemaManager );
        Set<IndexEntry<String, String>> resultSet = new HashSet<IndexEntry<String, String>>();

        // Check that we have an entry, otherwise we can immediately get out
        if ( baseId == null )
        {
            if ( ( ( Partition ) db ).getSuffixDn().equals( baseDn ) )
            {
                // The context entry is not created yet, return an empty result
                searchResult.setResultSet( resultSet );

                return searchResult;
            }
            else
            {
                // The search base doesn't exist
                throw new LdapNoSuchObjectException( I18n.err( I18n.ERR_648, baseDn ) );
            }
        }

        // --------------------------------------------------------------------
        // Determine the effective base with aliases
        // --------------------------------------------------------------------
        String aliasedBase = db.getAliasIndex().reverseLookup( baseId );
        Dn effectiveBase = baseDn;
        String effectiveBaseId = baseId;

        if ( ( aliasedBase != null ) && aliasDerefMode.isDerefFindingBase() )
        {
            /*
             * If the base is an alias and alias dereferencing does occur on
             * finding the base, or always then we set the effective base to the alias target
             * got from the alias index.
             */
            effectiveBase = new Dn( schemaManager, aliasedBase );
            effectiveBaseId = db.getEntryId( effectiveBase );
        }

        // --------------------------------------------------------------------
        // Specifically Handle Object Level Scope
        // --------------------------------------------------------------------
        if ( scope == SearchScope.OBJECT )
        {
            IndexEntry<String, String> indexEntry = new IndexEntry<String, String>();
            indexEntry.setId( effectiveBaseId );
            optimizer.annotate( filter );
            Evaluator<? extends ExprNode> evaluator = evaluatorBuilder.build( filter );

            // Fetch the entry, as we have only one
            Entry entry = db.lookup( indexEntry.getId(), effectiveBase );

            indexEntry.setEntry( entry );
            resultSet.add( indexEntry );

            searchResult.setEvaluator( evaluator );
            searchResult.setResultSet( resultSet );

            return searchResult;
        }

        // This is not a BaseObject scope search.

        // Add the scope node using the effective base to the filter
        BranchNode root = new AndNode();
        ExprNode node = new ScopeNode( aliasDerefMode, effectiveBase, effectiveBaseId, scope );
        root.getChildren().add( node );
        root.getChildren().add( filter );

        // Annotate the node with the optimizer and return search enumeration.
        optimizer.annotate( root );
        Evaluator<? extends ExprNode> evaluator = evaluatorBuilder.build( root );

        Set<String> uuidSet = new HashSet<String>();
        searchResult.setAliasDerefMode( aliasDerefMode );
        searchResult.setCandidateSet( uuidSet );

        long nbResults = cursorBuilder.build( root, searchResult );

        if ( nbResults < Long.MAX_VALUE )
        {
            for ( String uuid : uuidSet )
            {
                IndexEntry<String, String> indexEntry = new IndexEntry<String, String>();
                indexEntry.setId( uuid );
                resultSet.add( indexEntry );
            }
        }
        else
        {
            // Full scan : use the MasterTable
            Cursor<IndexEntry<String, String>> cursor = new IndexCursorAdaptor( db.getMasterTable().cursor(), true );

            while ( cursor.next() )
            {
                IndexEntry<String, String> indexEntry = cursor.get();

                // Here, the indexEntry contains a <UUID, Entry> tuple. Convert it to <UUID, UUID> 
                IndexEntry<String, String> forwardIndexEntry = new IndexEntry<String, String>();
                forwardIndexEntry.setKey( indexEntry.getKey() );
                forwardIndexEntry.setId( indexEntry.getKey() );
                forwardIndexEntry.setEntry( null );

                resultSet.add( forwardIndexEntry );
            }
        }

        searchResult.setEvaluator( evaluator );
        searchResult.setResultSet( resultSet );

        return searchResult;
    }


    /**
     * @see SearchEngine#evaluator(ExprNode)
     */
    public Evaluator<? extends ExprNode> evaluator( ExprNode filter ) throws Exception
    {
        return evaluatorBuilder.build( filter );
    }
}
