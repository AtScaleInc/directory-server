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
package org.apache.directory.server.core.authz;


import static org.apache.directory.server.core.authz.AutzIntegUtils.addUserToGroup;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createUser;
import static org.apache.directory.server.core.authz.AutzIntegUtils.getContextAs;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.DirContext;

import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Some tests to make sure users in the cn=Administrators,ou=groups,ou=system 
 * group behave as admin like users will full access rights.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( FrameworkRunner.class )
public class AdministratorsGroupIT extends AbstractLdapTestUnit
{
    
    @Before
    public void setService()
    {
       AutzIntegUtils.service = service;
    }
    
    
    boolean canReadAdministrators( DirContext ctx ) throws NamingException
    {
        try
        {
            ctx.getAttributes( "cn=Administrators,ou=groups" );
            return true;
        }
        catch ( NoPermissionException e )
        {
            return false;
        }
    }


    /**
     * Checks to make sure a non-admin user which is not in the Administrators 
     * group cannot access entries under ou=groups,ou=system.  Also check that 
     * after adding that user to the group they see those groups.  This test 
     * does NOT use the DefaultAuthorizationInterceptor but uses the one based on
     * ACI.
     * 
     * @throws Exception on failures
     */
    @Test
    @CreateDS ( enableAccessControl=true, name="testNonAdminReadAccessToGroups-method" )
    public void testNonAdminReadAccessToGroups() throws Exception
    {
        // this is required cause the new service is at method level
        AutzIntegUtils.service = service;
        
        Name billydDn = createUser( "billyd", "s3kr3t" );
        
        // this should fail with a no permission exception because we
        // are not allowed to browse ou=system without an ACI 
        try
        {
            getContextAs( billydDn, "s3kr3t" );
            fail( "Should not get here since we cannot browse ou=system" );
        }
        catch( NoPermissionException e )
        {
        }
        
        // add billyd to administrators and try again
        addUserToGroup( "billyd", "Administrators" );

        // billyd should now be able to read ou=system and the admin group
        DirContext ctx = getContextAs( billydDn, "s3kr3t" );
        assertTrue( canReadAdministrators( ctx ) );
    }


    /**
     * Checks to make sure a non-admin user which is not in the Administrators
     * group cannot access entries under ou=groups,ou=system.  Also check that
     * after adding that user to the group they see those groups.
     *
     * @throws Exception on failure
     */
    @Test
    @CreateDS ( name="testNonAdminReadAccessToGroups-method" )
    public void testDefaultNonAdminReadAccessToGroups() throws Exception
    {
        // this is required cause the new service is at method level
        AutzIntegUtils.service = service;

        Name billydDn = createUser( "billyd", "s3kr3t" );
        assertFalse( service.isAccessControlEnabled() );
        DirContext ctx = getContextAs( billydDn, "s3kr3t" );

        // billyd should not be able to read the admin group
        assertFalse( canReadAdministrators( ctx ) );

        // add billyd to administrators and try again
        addUserToGroup( "billyd", "Administrators" );

        // billyd should now be able to read the admin group
        assertTrue( canReadAdministrators( ctx ) );
    }
}
