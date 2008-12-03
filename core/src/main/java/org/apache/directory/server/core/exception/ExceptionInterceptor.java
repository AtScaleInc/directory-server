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
package org.apache.directory.server.core.exception;


import org.apache.commons.collections.map.LRUMap;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.cursor.EmptyCursor;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetMatchedNameOperationContext;
import org.apache.directory.server.core.interceptor.context.GetSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapAttributeInUseException;
import org.apache.directory.shared.ldap.exception.LdapContextNotEmptyException;
import org.apache.directory.shared.ldap.exception.LdapNameAlreadyBoundException;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.OidNormalizer;

import java.util.List;
import java.util.Map;


/**
 * An {@link org.apache.directory.server.core.interceptor.Interceptor} that detects any operations that breaks integrity
 * of {@link Partition} and terminates the current invocation chain by
 * throwing a {@link Exception}. Those operations include when an entry
 * already exists at a DN and is added once again to the same DN.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ExceptionInterceptor extends BaseInterceptor
{
    private PartitionNexus nexus;
    private DirectoryService directoryService;
    private LdapDN subschemSubentryDn;

    
    /**
     * The OIDs normalizer map
     */
    private Map<String, OidNormalizer> normalizerMap;
    
    /**
     * A cache to store entries which are not aliases. 
     * It's a speedup, we will be able to avoid backend lookups.
     * 
     * Note that the backend also use a cache mechanism, but for performance gain, it's good 
     * to manage a cache here. The main problem is that when a user modify the parent, we will
     * have to update it at three different places :
     * - in the backend,
     * - in the partition cache,
     * - in this cache.
     * 
     * The update of the backend and partition cache is already correctly handled, so we will
     * just have to offer an access to refresh the local cache. This should be done in 
     * delete, modify and move operations.
     * 
     * We need to be sure that frequently used DNs are always in cache, and not discarded.
     * We will use a LRU cache for this purpose. 
     */ 
    private final LRUMap notAliasCache = new LRUMap( DEFAULT_CACHE_SIZE );

    /** Declare a default for this cache. 100 entries seems to be enough */
    private static final int DEFAULT_CACHE_SIZE = 100;

    
    /**
     * Creates an interceptor that is also the exception handling service.
     */
    public ExceptionInterceptor()
    {
    }


    public void init( DirectoryService directoryService ) throws Exception
    {
        this.directoryService = directoryService;
        nexus = directoryService.getPartitionNexus();
        normalizerMap = directoryService.getRegistries().getAttributeTypeRegistry().getNormalizerMapping();
        Value<?> attr = nexus.getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        subschemSubentryDn = new LdapDN( ( String ) attr.get() );
        subschemSubentryDn.normalize( normalizerMap );
    }


    public void destroy()
    {
    }

    /**
     * In the pre-invocation state this interceptor method checks to see if the entry to be added already exists.  If it
     * does an exception is raised.
     */
    public void add( NextInterceptor nextInterceptor, AddOperationContext opContext )
        throws Exception
    {
        LdapDN name = opContext.getDn();
        
        if ( subschemSubentryDn.getNormName().equals( name.getNormName() ) )
        {
            throw new LdapNameAlreadyBoundException( 
                "The global schema subentry cannot be added since it exists by default." );
        }
        
        // check if the entry already exists
        if ( nextInterceptor.hasEntry( new EntryOperationContext( opContext.getSession(), name ) ) )
        {
            LdapNameAlreadyBoundException ne = new LdapNameAlreadyBoundException( name.getUpName() + " already exists!" );
            ne.setResolvedName( new LdapDN( name.getUpName() ) );
            throw ne;
        }
        
        LdapDN suffix = nexus.getSuffix( new GetSuffixOperationContext( this.directoryService.getAdminSession(), 
            name ) );
        
        // we're adding the suffix entry so just ignore stuff to mess with the parent
        if ( suffix.getNormName().equals( name.getNormName() ) )
        {
            nextInterceptor.add( opContext );
            return;
        }
        
        LdapDN parentDn = ( LdapDN ) name.clone();
        parentDn.remove( name.size() - 1 );
        
        // check if we're trying to add to a parent that is an alias
        boolean notAnAlias;
        
        synchronized( notAliasCache )
        {
            notAnAlias = notAliasCache.containsKey( parentDn.getNormName() );
        }
        
        if ( ! notAnAlias )
        {
            // We don't know if the parent is an alias or not, so we will launch a 
            // lookup, and update the cache if it's not an alias
            ClonedServerEntry attrs;
            
            try
            {
                attrs = opContext.lookup( parentDn, ByPassConstants.LOOKUP_BYPASS );
            }
            catch ( Exception e )
            {
                LdapNameNotFoundException e2 = new LdapNameNotFoundException( "Parent " + parentDn.getUpName() 
                    + " not found" );
                e2.setResolvedName( new LdapDN( nexus.getMatchedName( 
                    new GetMatchedNameOperationContext( opContext.getSession(), parentDn ) ).getUpName() ) );
                throw e2;
            }
            
            EntryAttribute objectClass = attrs.getOriginalEntry().get( SchemaConstants.OBJECT_CLASS_AT );
            
            if ( objectClass.contains( SchemaConstants.ALIAS_OC ) )
            {
                String msg = "Attempt to add entry to alias '" + name.getUpName() + "' not allowed.";
                ResultCodeEnum rc = ResultCodeEnum.ALIAS_PROBLEM;
                LdapNamingException e = new LdapNamingException( msg, rc );
                e.setResolvedName( new LdapDN( parentDn.getUpName() ) );
                throw e;
            }
            else
            {
                synchronized ( notAliasCache )
                {
                    notAliasCache.put( parentDn.getNormName(), parentDn );
                }
            }
        }

        nextInterceptor.add( opContext );
    }


    /**
     * Checks to make sure the entry being deleted exists, and has no children, otherwise throws the appropriate
     * LdapException.
     */
    public void delete( NextInterceptor nextInterceptor, DeleteOperationContext opContext ) throws Exception
    {
        LdapDN name = opContext.getDn();
        
        if ( name.getNormName().equalsIgnoreCase( subschemSubentryDn.getNormName() ) )
        {
            throw new LdapOperationNotSupportedException( 
                "Can not allow the deletion of the subschemaSubentry (" + 
                subschemSubentryDn + ") for the global schema.",
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        // check if entry to delete exists
        String msg = "Attempt to delete non-existant entry: ";
        assertHasEntry( nextInterceptor, opContext, msg, name );

        // check if entry to delete has children (only leaves can be deleted)
        boolean hasChildren = false;
        EntryFilteringCursor list = nextInterceptor.list( new ListOperationContext( opContext.getSession(), name ) );
        
        if ( list.next() )
        {
            hasChildren = true;
        }

        list.close();
        
        if ( hasChildren )
        {
            LdapContextNotEmptyException e = new LdapContextNotEmptyException();
            e.setResolvedName( new LdapDN( name.getUpName() ) );
            throw e;
        }

        synchronized( notAliasCache )
        {
            if ( notAliasCache.containsKey( name.getNormName() ) )
            {
                notAliasCache.remove( name.getNormName() );
            }
        }
        
        nextInterceptor.delete( opContext );
    }


    /**
     * Checks to see the base being searched exists, otherwise throws the appropriate LdapException.
     */
    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws Exception
    {
        if ( opContext.getDn().getNormName().equals( subschemSubentryDn.getNormName() ) )
        {
            // there is nothing under the schema subentry
            return new BaseEntryFilteringCursor( new EmptyCursor<ServerEntry>(), opContext );
        }
        
        // check if entry to search exists
        String msg = "Attempt to search under non-existant entry: ";
        assertHasEntry( nextInterceptor, opContext, msg, opContext.getDn() );

        return nextInterceptor.list( opContext );
    }


    /**
     * Checks to see the base being searched exists, otherwise throws the appropriate LdapException.
     */
    public ClonedServerEntry lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws Exception
    {
        if ( opContext.getDn().getNormName().equals( subschemSubentryDn.getNormName() ) )
        {
            return nexus.getRootDSE( null );
        }
        
        // check if entry to lookup exists
        String msg = "Attempt to lookup non-existant entry: ";
        assertHasEntry( nextInterceptor, opContext, msg, opContext.getDn() );

        return nextInterceptor.lookup( opContext );
    }


    /**
     * Checks to see the entry being modified exists, otherwise throws the appropriate LdapException.
     */
    public void modify( NextInterceptor nextInterceptor, ModifyOperationContext opContext )
        throws Exception
    {
        // check if entry to modify exists
        String msg = "Attempt to modify non-existant entry: ";

        // handle operations against the schema subentry in the schema service
        // and never try to look it up in the nexus below
        if ( opContext.getDn().getNormName().equalsIgnoreCase( subschemSubentryDn.getNormName() ) )
        {
            nextInterceptor.modify( opContext );
            return;
        }
        
        assertHasEntry( nextInterceptor, opContext, msg, opContext.getDn() );

        ServerEntry entry = opContext.lookup( opContext.getDn(), ByPassConstants.LOOKUP_BYPASS );
        List<Modification> items = opContext.getModItems();

        for ( Modification item : items )
        {
            if ( item.getOperation() == ModificationOperation.ADD_ATTRIBUTE )
            {
                EntryAttribute modAttr = (ServerAttribute)item.getAttribute();
                EntryAttribute entryAttr = entry.get( modAttr.getId() );

                if ( entryAttr != null )
                {
                    for ( Value<?> value:modAttr )
                    {
                        if ( entryAttr.contains( value ) )
                        {
                            throw new LdapAttributeInUseException( "Trying to add existing value '" + value
                                    + "' to attribute " + modAttr.getId() );
                        }
                    }
                }
            }
        }

        // Let's assume that the new modified entry may be an alias,
        // but we don't want to check that now...
        // We will simply remove the DN from the NotAlias cache.
        // It would be smarter to check the modified attributes, but
        // it would also be more complex.
        synchronized( notAliasCache )
        {
            if ( notAliasCache.containsKey( opContext.getDn().getNormName() ) )
            {
                notAliasCache.remove( opContext.getDn().getNormName() );
            }
        }

        nextInterceptor.modify( opContext );
    }

    /**
     * Checks to see the entry being renamed exists, otherwise throws the appropriate LdapException.
     */
    public void rename( NextInterceptor nextInterceptor, RenameOperationContext opContext )
        throws Exception
    {
        LdapDN dn = opContext.getDn();
        
        if ( dn.getNormName().equalsIgnoreCase( subschemSubentryDn.getNormName() ) )
        {
            throw new LdapOperationNotSupportedException( 
                "Can not allow the renaming of the subschemaSubentry (" + 
                subschemSubentryDn + ") for the global schema: it is fixed at " + subschemSubentryDn,
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        // check if entry to rename exists
        String msg = "Attempt to rename non-existant entry: ";
        assertHasEntry( nextInterceptor, opContext, msg, dn );

        // check to see if target entry exists
        LdapDN newDn = ( LdapDN ) dn.clone();
        newDn.remove( dn.size() - 1 );
        newDn.add( opContext.getNewRdn() );
        newDn.normalize( normalizerMap );
        
        if ( nextInterceptor.hasEntry( new EntryOperationContext( opContext.getSession(), newDn ) ) )
        {
            LdapNameAlreadyBoundException e;
            e = new LdapNameAlreadyBoundException( "target entry " + newDn.getUpName() + " already exists!" );
            e.setResolvedName( new LdapDN( newDn.getUpName() ) );
            throw e;
        }

        // Remove the previous entry from the notAnAlias cache
        synchronized( notAliasCache )
        {
            if ( notAliasCache.containsKey( dn.getNormName() ) )
            {
                notAliasCache.remove( dn.getNormName() );
            }
        }

        nextInterceptor.rename( opContext );
    }


    /**
     * Checks to see the entry being moved exists, and so does its parent, otherwise throws the appropriate
     * LdapException.
     */
    public void move( NextInterceptor nextInterceptor, MoveOperationContext opContext ) throws Exception
    {
        LdapDN oriChildName = opContext.getDn();
        LdapDN newParentName = opContext.getParent();
        
        if ( oriChildName.getNormName().equalsIgnoreCase( subschemSubentryDn.getNormName() ) )
        {
            throw new LdapOperationNotSupportedException( 
                "Can not allow the move of the subschemaSubentry (" + 
                subschemSubentryDn + ") for the global schema: it is fixed at " + subschemSubentryDn,
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        // check if child to move exists
        String msg = "Attempt to move to non-existant parent: ";
        assertHasEntry( nextInterceptor, opContext, msg, oriChildName );

        // check if parent to move to exists
        msg = "Attempt to move to non-existant parent: ";
        assertHasEntry( nextInterceptor, opContext, msg, newParentName );

        // check to see if target entry exists
        String rdn = oriChildName.get( oriChildName.size() - 1 );
        LdapDN target = ( LdapDN ) newParentName.clone();
        target.add( rdn );
        
        if ( nextInterceptor.hasEntry( new EntryOperationContext( opContext.getSession(), target ) ) )
        {
            // we must calculate the resolved name using the user provided Rdn value
            String upRdn = new LdapDN( oriChildName.getUpName() ).get( oriChildName.size() - 1 );
            LdapDN upTarget = ( LdapDN ) newParentName.clone();
            upTarget.add( upRdn );

            LdapNameAlreadyBoundException e;
            e = new LdapNameAlreadyBoundException( "target entry " + upTarget.getUpName() + " already exists!" );
            e.setResolvedName( new LdapDN( upTarget.getUpName() ) );
            throw e;
        }

        // Remove the original entry from the NotAlias cache, if needed
        synchronized( notAliasCache )
        {
            if ( notAliasCache.containsKey( oriChildName.getNormName() ) )
            {
                notAliasCache.remove( oriChildName.getNormName() );
            }
        }
                
        nextInterceptor.move( opContext );
    }


    /**
     * Checks to see the entry being moved exists, and so does its parent, otherwise throws the appropriate
     * LdapException.
     */
    public void moveAndRename( NextInterceptor nextInterceptor, MoveAndRenameOperationContext opContext ) throws Exception
    {
        LdapDN oriChildName = opContext.getDn();
        LdapDN parent = opContext.getParent();

        if ( oriChildName.getNormName().equalsIgnoreCase( subschemSubentryDn.getNormName() ) )
        {
            throw new LdapOperationNotSupportedException( 
                "Can not allow the move of the subschemaSubentry (" + 
                subschemSubentryDn + ") for the global schema: it is fixed at " + subschemSubentryDn,
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        // check if child to move exists
        String msg = "Attempt to move to non-existant parent: ";
        assertHasEntry( nextInterceptor, opContext, msg, oriChildName );

        // check if parent to move to exists
        msg = "Attempt to move to non-existant parent: ";
        assertHasEntry( nextInterceptor, opContext, msg, parent );

        // check to see if target entry exists
        LdapDN target = ( LdapDN ) parent.clone();
        target.add( opContext.getNewRdn() );

        if ( nextInterceptor.hasEntry( new EntryOperationContext( opContext.getSession(), target ) ) )
        {
            // we must calculate the resolved name using the user provided Rdn value
            LdapDN upTarget = ( LdapDN ) parent.clone();
            upTarget.add( opContext.getNewRdn() );

            LdapNameAlreadyBoundException e;
            e = new LdapNameAlreadyBoundException( "target entry " + upTarget.getUpName() + " already exists!" );
            e.setResolvedName( new LdapDN( upTarget.getUpName() ) );
            throw e;
        }

        // Remove the original entry from the NotAlias cache, if needed
        synchronized( notAliasCache )
        {
            if ( notAliasCache.containsKey( oriChildName.getNormName() ) )
            {
                notAliasCache.remove( oriChildName.getNormName() );
            }
        }
        
        nextInterceptor.moveAndRename( opContext );
    }


    /**
     * Checks to see the entry being searched exists, otherwise throws the appropriate LdapException.
     */
    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws Exception
    {
        LdapDN base = opContext.getDn();

        try
        {
            EntryFilteringCursor cursor =  nextInterceptor.search( opContext );
            
            if ( ! cursor.next() )
            {
                if ( !base.isEmpty() && !( subschemSubentryDn.toNormName() ).equalsIgnoreCase( base.toNormName() ) )
                {
                    // We just check that the entry exists only if we didn't found any entry
                    assertHasEntry( nextInterceptor, opContext, "Attempt to search under non-existant entry:" , base );
                }
            }

            return cursor;
        }
        catch ( Exception ne )
        {
            String msg = "Attempt to search under non-existant entry: ";
            assertHasEntry( nextInterceptor, opContext, msg, base );
            throw ne;
        }
    }


    /**
     * Asserts that an entry is present and as a side effect if it is not, creates a LdapNameNotFoundException, which is
     * used to set the before exception on the invocation - eventually the exception is thrown.
     *
     * @param msg        the message to prefix to the distinguished name for explanation
     * @param dn         the distinguished name of the entry that is asserted
     * @throws Exception if the entry does not exist
     * @param nextInterceptor the next interceptor in the chain
     */
    private void assertHasEntry( NextInterceptor nextInterceptor, OperationContext opContext, 
        String msg, LdapDN dn ) throws Exception
    {
        if ( subschemSubentryDn.getNormName().equals( dn.getNormName() ) )
        {
            return;
        }
        
        if ( ! opContext.hasEntry( dn, ByPassConstants.HAS_ENTRY_BYPASS ) )
        {
            LdapNameNotFoundException e;

            if ( msg != null )
            {
                e = new LdapNameNotFoundException( msg + dn.getUpName() );
            }
            else
            {
                e = new LdapNameNotFoundException( dn.getUpName() );
            }

            e.setResolvedName( 
                new LdapDN( 
                    opContext.getSession().getDirectoryService().getOperationManager().getMatchedName( 
                        new GetMatchedNameOperationContext( opContext.getSession(), dn ) ).getUpName() ) );
            throw e;
        }
    }
}
