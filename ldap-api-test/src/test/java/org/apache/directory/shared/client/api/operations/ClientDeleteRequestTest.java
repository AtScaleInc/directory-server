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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.client.api.LdapConnection;
import org.apache.directory.shared.ldap.client.api.exception.LdapException;
import org.apache.directory.shared.ldap.client.api.listeners.DeleteListener;
import org.apache.directory.shared.ldap.client.api.messages.DeleteRequest;
import org.apache.directory.shared.ldap.client.api.messages.DeleteResponse;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case for client delete operation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
@RunWith(FrameworkRunner.class)
@CreateLdapServer ( 
    transports = 
    {
        @CreateTransport( protocol = "LDAP" ), 
        @CreateTransport( protocol = "LDAPS" ) 
    })
@ApplyLdifs( {
    "dn: cn=parent,ou=system",
    "objectClass: person",
    "cn: parent_cn",
    "sn: parent_sn", 
    
    "",
    
    "dn: cn=child1,cn=parent,ou=system",
    "objectClass: person",
    "cn: child1_cn",
    "sn: child1_sn", 
    
    "",
    
    "dn: cn=child2,cn=parent,ou=system",
    "objectClass: person",
    "cn: child2_cn",
    "sn: child2_sn", 
    
    "",
    
    "dn: cn=grand_child11,cn=child1,cn=parent,ou=system",
    "objectClass: person",
    "cn: grand_child11_cn",
    "sn: grand_child11_sn", 
    
    "",
    
    "dn: cn=grand_child12,cn=child1,cn=parent,ou=system",
    "objectClass: person",
    "cn: grand_child12_cn",
    "sn: grand_child12_sn"
})
public class ClientDeleteRequestTest extends AbstractLdapTestUnit
{
    private LdapConnection connection;
    
    private CoreSession session;
    
    @Before
    public void setup() throws Exception
    {
        connection = new LdapConnection( "localhost", ldapServer.getPort() );

        LdapDN bindDn = new LdapDN( "uid=admin,ou=system" );
        connection.bind( bindDn.getName(), "secret" );
        
        session = ldapServer.getDirectoryService().getAdminSession();
    }
    
    @After
    public void clean() throws Exception
    {
        connection.close();
    }
    
    
    @Test
    public void testDeleteLeafNode() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=grand_child12,cn=child1,cn=parent,ou=system" );
        
        assertTrue( session.exists( dn ) );
        
        DeleteResponse response = connection.delete( dn.getName() );
        assertNotNull( response );
        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
        
        assertFalse( session.exists( dn ) );
    }
    
    
    @Test
    public void testDeleteNonLeafFailure() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=child1,cn=parent,ou=system" ); // has children
        assertTrue( session.exists( dn ) );
 
        DeleteResponse response = connection.delete( dn.getName() );
        assertNotNull( response );
        assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_NON_LEAF, response.getLdapResult().getResultCode() );
        
        assertTrue( session.exists( dn ) );
    }
    

    @Test
    public void testDeleteWithCascadeControl() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=parent,ou=system" );
        
        assertTrue( session.exists( dn ) );
        
        DeleteResponse response = connection.deleteTree( dn );
        assertNotNull( response );
        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
        
        assertFalse( session.exists( dn ) );
    }
    
    
    /**
     * this method uses reflection to test deleteChildren method without using the
     * convenient method delete( dn, true ), cause the convenient method checks 
     * whether the server supports the CascadeControl.
     * 
     * Cause ADS supports this control, delete(dn, true) will never call the method
     * deleteChildren() (which has private scope) 
     * To test the manual deletion of the entries in the absence of this CascadeControl
     * reflection was used to invoke the private method deleteChildren().
     * 
     */
    @Test
    public void testDeleteWithoutCascadeControl() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=parent,ou=system" );
        
        assertTrue( session.exists( dn ) );

        Method deleteChildrenMethod = connection.getClass().getDeclaredMethod( "deleteRecursive", LdapDN.class, Map.class, DeleteListener.class );
        deleteChildrenMethod.setAccessible( true );
        
        DeleteResponse response = ( DeleteResponse ) deleteChildrenMethod.invoke( connection, dn, null, null );
        assertNotNull( response );
        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
        
        assertFalse( session.exists( dn ) );
    }

    
    /**
     * @see #testDeleteWithoutCascadeControl()
     */
    @Test
    public void testDeleteAsyncWithoutCascadeControl() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=parent,ou=system" );
        
        assertTrue( session.exists( dn ) );

        Method deleteChildrenMethod = connection.getClass().getDeclaredMethod( "deleteRecursive", LdapDN.class, Map.class, DeleteListener.class );
        deleteChildrenMethod.setAccessible( true );
    
        final AtomicInteger count = new AtomicInteger();
        
        DeleteListener listener = new DeleteListener()
        {
            public void entryDeleted( LdapConnection connection, DeleteResponse response ) throws LdapException
            {
                assertNotNull( response );
                assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
                count.incrementAndGet();
            }
        };
        
        DeleteResponse response = ( DeleteResponse ) deleteChildrenMethod.invoke( connection, dn, null, listener );
        
        assertNull( response );
        
        int numDNs = 5; // total number of entries expected to be deleted
        while( count.get() != numDNs )
        {
            Thread.sleep( 1000 );
        }

        assertFalse( session.exists( dn ) );
    }

    
    @Test
    public void testDeleteAsync() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=grand_child12,cn=child1,cn=parent,ou=system" );
        
        assertTrue( session.exists( dn ) );

        final Semaphore lock = new Semaphore(1);
        lock.acquire();
        
        DeleteResponse response = connection.delete( new DeleteRequest( dn ), new DeleteListener()
        {
            public void entryDeleted( LdapConnection connection, DeleteResponse response ) throws LdapException
            {
                assertNotNull( response );
                assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
                lock.release();
            }
        });
        
        lock.acquire();
        assertNull( response );
        assertFalse( session.exists( dn ) );
    }
}
