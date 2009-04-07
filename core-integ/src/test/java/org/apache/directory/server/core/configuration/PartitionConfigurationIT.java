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
package org.apache.directory.server.core.configuration;


import junit.framework.Assert;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.integ.CiRunner;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.jndi.CoreContextFactory;
import org.apache.directory.server.core.partition.jdbm.JdbmPartition;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import java.util.Hashtable;


/**
 * Tests dynamic partition addition and removal.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( CiRunner.class )
public class PartitionConfigurationIT
{
    public static DirectoryService service;


    @Test
    public void testAddAndRemove() throws Exception
    {
        JdbmPartition partition = new JdbmPartition();
        partition.setId( "removable" );
        partition.setSuffix( "ou=removable" );
        
        // Test AddContextPartition
        service.addPartition( partition );
        
        LdapDN suffixDn = new LdapDN( "ou=removable" );
        suffixDn.normalize( service.getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
        ServerEntry ctxEntry = new DefaultServerEntry( service.getRegistries(), suffixDn );
        ctxEntry.put( "objectClass", "top" );
        ctxEntry.get( "objectClass" ).add( "organizationalUnit" );
        ctxEntry.put( "ou", "removable" );
        partition.add( new AddOperationContext( service.getAdminSession(), ctxEntry ) );
        
        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CoreContextFactory.class.getName() );
        env.put( DirectoryService.JNDI_KEY, service );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        Context ctx = new InitialContext( env );
        Assert.assertNotNull( ctx.lookup( "ou=removable" ) );

        // Test removeContextPartition
        service.removePartition( partition );
        ctx = new InitialContext( env );
        try
        {
            ctx.lookup( "ou=removable" );
            Assert.fail( "NameNotFoundException should be thrown." );
        }
        catch ( NameNotFoundException e )
        {
            // Partition is removed.
        }
    }
}
