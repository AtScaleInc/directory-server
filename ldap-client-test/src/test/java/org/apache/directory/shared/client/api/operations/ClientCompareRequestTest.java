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
package org.apache.directory.shared.client.api.operations;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.directory.ldap.client.api.LdapAsyncConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.ldap.client.api.future.CompareFuture;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.CompareRequest;
import org.apache.directory.shared.ldap.message.CompareResponse;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the compare operation
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports =
    { @CreateTransport(protocol = "LDAP"), @CreateTransport(protocol = "LDAPS") })
public class ClientCompareRequestTest extends AbstractLdapTestUnit
{
    private LdapAsyncConnection connection;

    private CoreSession session;


    @Before
    public void setup() throws Exception
    {
        connection = new LdapNetworkConnection( "localhost", ldapServer.getPort() );
        DN bindDn = new DN( "uid=admin,ou=system" );
        connection.bind( bindDn.getName(), "secret" );

        session = ldapServer.getDirectoryService().getSession();
    }


    /**
     * Close the LdapConnection
     */
    @After
    public void shutdown()
    {
        try
        {
            if ( connection != null )
            {
                connection.close();
            }
        }
        catch ( Exception ioe )
        {
            fail();
        }
    }


    @Test
    public void testCompare() throws Exception
    {
        DN dn = new DN( "uid=admin,ou=system" );

        CompareResponse response = connection.compare( dn, SchemaConstants.UID_AT, "admin" );
        assertNotNull( response );
        assertTrue( response.isTrue() );

        response = connection.compare( dn.getName(), SchemaConstants.USER_PASSWORD_AT, "secret".getBytes() );
        assertNotNull( response );
        assertTrue( response.isTrue() );
    }


    @Test
    public void testCompareAsync() throws Exception
    {
        DN dn = new DN( "uid=admin,ou=system" );

        CompareRequest compareRequest = new org.apache.directory.shared.ldap.codec.message.CompareRequestImpl();
        compareRequest.setName( dn );
        compareRequest.setAttributeId( SchemaConstants.UID_AT );
        compareRequest.setAssertionValue( "admin" );

        connection.compare( compareRequest );

        assertTrue( session.exists( dn ) );

        CompareFuture compareFuture = connection.compareAsync( compareRequest );

        try
        {
            CompareResponse compareResponse = compareFuture.get( 1000, TimeUnit.MILLISECONDS );

            assertNotNull( compareResponse );
            assertEquals( ResultCodeEnum.COMPARE_TRUE, compareResponse.getLdapResult().getResultCode() );
        }
        catch ( TimeoutException toe )
        {
            fail();
        }
    }
}
