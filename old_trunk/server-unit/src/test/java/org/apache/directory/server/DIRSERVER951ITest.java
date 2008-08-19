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
package org.apache.directory.server;


import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Oid;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.DateUtils;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.HashSet;
import java.util.Set;


/**
 * A set of tests to make sure the negation operator is working 
 * properly when included in search filters. Created in response
 * to JIRA issue 
 * <a href="https://issues.apache.org/jira/browse/DIRSERVER-951">DIRSERVER-951</a>.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DIRSERVER951ITest extends AbstractServerTest
{
    private LdapContext ctx;


    /**
     * Create context and entries for tests.
     */
    public void setUp() throws Exception
    {
        super.setUp();
        super.loadTestLdif( true );
        ctx = getWiredContext();
        assertNotNull( ctx );
    }


    @Override
    protected void configureDirectoryService() throws NamingException
    {
        JdbmPartition systemCfg = new JdbmPartition();
        systemCfg.setId( "system" );

        // @TODO need to make this configurable for the system partition
        systemCfg.setCacheSize( 500 );

        systemCfg.setSuffix( "ou=system" );

        // Add indexed attributes for system partition
        Set<Index> indexedAttrs = new HashSet<Index>();
        indexedAttrs.add( new JdbmIndex( Oid.ALIAS ) );
        indexedAttrs.add( new JdbmIndex( Oid.EXISTANCE ) );
        indexedAttrs.add( new JdbmIndex( Oid.HIERARCHY ) );
        indexedAttrs.add( new JdbmIndex( Oid.NDN ) );
        indexedAttrs.add( new JdbmIndex( Oid.ONEALIAS ) );
        indexedAttrs.add( new JdbmIndex( Oid.SUBALIAS ) );
        indexedAttrs.add( new JdbmIndex( Oid.UPDN ) );
        indexedAttrs.add( new JdbmIndex( "objectClass" ) );
        indexedAttrs.add( new JdbmIndex( "ou" ) );
        systemCfg.setIndexedAttributes( indexedAttrs );

        // Add context entry for system partition
        LdapDN systemDn = new LdapDN( "ou=system" );
        ServerEntry systemEntry = new DefaultServerEntry( directoryService.getRegistries(), systemDn );
        
        systemEntry.put( "objectClass", "top", "account" );
        systemEntry.put( "creatorsName", "uid=admin,ou=system" );
        systemEntry.put( "createTimestamp", DateUtils.getGeneralizedTime() );
        systemEntry.put( "ou", "system" );
        systemEntry.put( "uid", "testUid" );
        systemCfg.setContextEntry( systemEntry );

        directoryService.setSystemPartition( systemCfg );
    }

    /**
     * Closes context and destroys server.
     */
    public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;
        super.tearDown();
    }
    

    /**
     * Tests to make sure a negated search for OU of "test1" returns
     * those entries that do not have the OU attribute or do not have
     * a "test1" value for OU if the attribute exists.
     * 
     * @throws Exception on failure to search
     */
    public void testSearchNotOU() throws Exception
    {
        Set<SearchResult> results = getResults( "(!(ou=test1))" );
        assertFalse( contains( "uid=test1,ou=test,ou=system", results ) );
        assertTrue( contains( "uid=test2,ou=test,ou=system", results ) );
        assertTrue( contains( "uid=testNoOU,ou=test,ou=system", results ) );
    }

    
    boolean contains( String dn, Set<SearchResult> results )
    {
        for ( SearchResult result : results )
        {
            if ( result.getNameInNamespace().equals( dn ) )
            {
                return true;
            }
        }
        
        return false;
    }
    
    
    Set<SearchResult> getResults( String filter ) throws NamingException
    {
        Set<SearchResult> results = new HashSet<SearchResult>();
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        NamingEnumeration<SearchResult> namingEnumeration = ctx.search( "ou=system", filter, controls );
        while( namingEnumeration.hasMore() )
        {
            results.add( namingEnumeration.next() );
        }
        
        return results;
    }
}
