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


import java.net.InetAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.directory.server.core.configuration.MutablePartitionConfiguration;
import org.apache.directory.server.core.configuration.PartitionConfiguration;
import org.apache.directory.server.ldap.LdapConfiguration;
import org.apache.directory.server.ntp.NtpConfiguration;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.mina.util.AvailablePortFinder;


/**
 * An {@link AbstractServerTest} testing the Network Time Protocol (NTP).
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NtpITest extends AbstractServerTest
{
    private DirContext ctx = null;


    /**
     * Set up a partition for EXAMPLE.COM and enable the NTP service.  The LDAP service is disabled.
     */
    public void setUp() throws Exception
    {
        configuration.setAllowAnonymousAccess( false );

        LdapConfiguration ldapConfig = configuration.getLdapConfiguration();
        ldapConfig.setEnabled( false );

        NtpConfiguration ntpConfig = configuration.getNtpConfiguration();
        ntpConfig.setEnabled( true );

        port = AvailablePortFinder.getNextAvailable( 10123 );
        ntpConfig.setIpPort( port );

        Attributes attrs;
        Set<PartitionConfiguration> pcfgs = new HashSet<PartitionConfiguration>();

        MutablePartitionConfiguration pcfg;

        // Add partition 'example'
        pcfg = new MutablePartitionConfiguration();
        pcfg.setId( "example" );
        pcfg.setSuffix( "dc=example,dc=com" );

        Set<Object> indexedAttrs = new HashSet<Object>();
        indexedAttrs.add( "ou" );
        indexedAttrs.add( "dc" );
        indexedAttrs.add( "objectClass" );
        pcfg.setIndexedAttributes( indexedAttrs );

        attrs = new AttributesImpl( true );
        Attribute attr = new AttributeImpl( "objectClass" );
        attr.add( "top" );
        attr.add( "domain" );
        attrs.put( attr );
        attr = new AttributeImpl( "dc" );
        attr.add( "example" );
        attrs.put( attr );
        pcfg.setContextEntry( attrs );

        pcfgs.add( pcfg );
        configuration.setPartitionConfigurations( pcfgs );

        doDelete( configuration.getWorkingDirectory() );
        configuration.setShutdownHookEnabled( false );
        setContexts( "uid=admin,ou=system", "secret" );

        // Get a context.
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "org.apache.directory.server.core.jndi.CoreContextFactory" );
        env.put( Context.PROVIDER_URL, "dc=example,dc=com" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );

        ctx = new InitialDirContext( env );
    }


    /**
     * Tests to make sure NTP works when enabled in the server.
     * 
     * @throws Exception 
     */
    public void testNtp() throws Exception
    {
        long currentTime = System.currentTimeMillis();

        InetAddress host = InetAddress.getByName( null );

        NTPUDPClient ntp = new NTPUDPClient();
        ntp.setDefaultTimeout( 5000 );

        TimeInfo timeInfo = ntp.getTime( host, port );

        long returnTime = timeInfo.getReturnTime();
        assertTrue( currentTime - returnTime < 1000 );

        timeInfo.computeDetails();

        assertTrue( 0 < timeInfo.getOffset() && timeInfo.getOffset() < 1000 );
        assertTrue( 0 < timeInfo.getDelay() && timeInfo.getDelay() < 1000 );
    }


    /**
     * Tear down.
     */
    public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;
        super.tearDown();
    }
}
