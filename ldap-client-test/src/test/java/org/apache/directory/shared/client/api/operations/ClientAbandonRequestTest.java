/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.shared.client.api.operations;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.ldap.client.api.future.SearchFuture;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.client.api.LdapApiIntegrationUtils;
import org.apache.directory.shared.ldap.model.constants.SchemaConstants;
import org.apache.directory.shared.ldap.model.cursor.Cursor;
import org.apache.directory.shared.ldap.model.entry.DefaultEntry;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.message.AliasDerefMode;
import org.apache.directory.shared.ldap.model.message.Response;
import org.apache.directory.shared.ldap.model.message.SearchRequest;
import org.apache.directory.shared.ldap.model.message.SearchResultDone;
import org.apache.directory.shared.ldap.model.message.SearchScope;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * A test class for ClientAbandonRequest.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports =
    { @CreateTransport(protocol = "LDAP"), @CreateTransport(protocol = "LDAPS") })
public class ClientAbandonRequestTest extends AbstractLdapTestUnit
{

    private LdapNetworkConnection connection;


    @Before
    public void setup() throws Exception
    {
        connection = LdapApiIntegrationUtils.getPooledAdminConnection( getLdapServer() );
    }


    @After
    public void shutdown() throws Exception
    {
        LdapApiIntegrationUtils.releasePooledAdminConnection( connection, getLdapServer() );
    }


    @Test
    public void testAbandonSearch() throws Exception
    {
        // injecting some values to keep the
        // followed search operation to run for a while
        final int numEntries = 100;

        for ( int i = 0; i < numEntries; i++ )
        {
            String s = String.valueOf( i );
            Dn dn = new Dn( "cn=" + s + ",ou=system" );
            Entry entry = new DefaultEntry( dn );
            entry.add( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.PERSON_OC );
            entry.add( SchemaConstants.CN_AT, s );
            entry.add( SchemaConstants.SN_AT, s );

            connection.add( entry );
        }

        SearchRequest sr = new org.apache.directory.shared.ldap.model.message.SearchRequestImpl();
        sr.setFilter( "(cn=*)" );
        sr.setBase( new Dn( "ou=system" ) );
        sr.setScope( SearchScope.ONELEVEL );
        sr.setDerefAliases( AliasDerefMode.NEVER_DEREF_ALIASES );

        // Launch the search now
        SearchFuture searchFuture = connection.searchAsync( sr );

        Response searchResponse = null;
        int count = 0;

        do
        {
            searchResponse = searchFuture.get();
            count++;

            if ( count > 10 )
            {
                searchFuture.cancel( true );
                break;
            }
        }
        while ( !( searchResponse instanceof SearchResultDone ) );

        assertTrue( numEntries > count );
        assertTrue( searchFuture.isCancelled() );

        // Now do a simple synchronous search
        Cursor<Response> results = connection.search( sr );

        int n = -1;

        while ( results.next() )
        {
            results.get();
            n++;
        }
        results.close();

        assertEquals( numEntries, n );
    }
}
