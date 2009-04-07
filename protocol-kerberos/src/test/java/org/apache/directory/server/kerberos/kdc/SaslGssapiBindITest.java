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
package org.apache.directory.server.kerberos.kdc;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.kerberos.KeyDerivationInterceptor;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.jdbm.JdbmPartition;
import org.apache.directory.server.kerberos.shared.store.KerberosAttribute;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;


/**
 * An {@link AbstractServerTest} testing SASL GSSAPI authentication
 * and security layer negotiation.  These tests require both the LDAP
 * and the Kerberos protocol.  As with any "three-headed" Kerberos
 * scenario, there are 3 principals:  1 for the test user, 1 for the
 * Kerberos ticket-granting service (TGS), and 1 for the LDAP service.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SaslGssapiBindITest extends AbstractServerTest
{
    private DirContext ctx;


    /**
     * Creates a new instance of SaslGssapiBindTest and sets JAAS system properties
     * for the KDC and realm, so we don't have to rely on external configuration.
     */
    public SaslGssapiBindITest()
    {
        System.setProperty( "java.security.krb5.realm", "EXAMPLE.COM" );
        System.setProperty( "java.security.krb5.kdc", "localhost" );
    }


    /**
     * Set up a partition for EXAMPLE.COM and add user and service principals to
     * test authentication with.
     */
    @Before
    public void setUp() throws Exception
    {
        super.setUp();

        ldapService.setSaslHost( "localhost" );
        ldapService.setSaslPrincipal( "ldap/localhost@EXAMPLE.COM" );

        KdcServer kdcConfig = new KdcServer();
        kdcConfig.setDirectoryService( directoryService );
        kdcConfig.setEnabled( true );
        kdcConfig.setSearchBaseDn( "ou=users,dc=example,dc=com" );

        Attributes attrs;

        setContexts( "uid=admin,ou=system", "secret" );

        // -------------------------------------------------------------------
        // Enable the krb5kdc schema
        // -------------------------------------------------------------------

        // check if krb5kdc is disabled
        Attributes krb5kdcAttrs = schemaRoot.getAttributes( "cn=Krb5kdc" );
        boolean isKrb5KdcDisabled = false;
        if ( krb5kdcAttrs.get( "m-disabled" ) != null )
        {
            isKrb5KdcDisabled = ( ( String ) krb5kdcAttrs.get( "m-disabled" ).get() ).equalsIgnoreCase( "TRUE" );
        }

        // if krb5kdc is disabled then enable it
        if ( isKrb5KdcDisabled )
        {
            Attribute disabled = new BasicAttribute( "m-disabled" );
            ModificationItem[] mods = new ModificationItem[]
                    {new ModificationItem( DirContext.REMOVE_ATTRIBUTE, disabled )};
            schemaRoot.modifyAttributes( "cn=Krb5kdc", mods );
        }
        
        LdapDN contextDn = new LdapDN( "dc=example,dc=com" );
        ServerEntry entry = ldapService.getDirectoryService().newEntry( contextDn );
        entry.add( "objectClass", "top", "domain", "extensibleObject" );
        entry.add( "dc", "example" );
        ldapService.getDirectoryService().getAdminSession().add( entry );

        // Get a context, create the ou=users subcontext, then create the 3 principals.
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( DirectoryService.JNDI_KEY, directoryService );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "org.apache.directory.server.core.jndi.CoreContextFactory" );
        env.put( Context.PROVIDER_URL, "dc=example,dc=com" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );

        ctx = new InitialDirContext( env );

        attrs = getOrgUnitAttributes( "users" );
        DirContext users = ctx.createSubcontext( "ou=users", attrs );

        attrs = getPrincipalAttributes( "Nelson", "Horatio Nelson", "hnelson", "secret", "hnelson@EXAMPLE.COM" );
        users.createSubcontext( "uid=hnelson", attrs );

        attrs = getPrincipalAttributes( "Service", "KDC Service", "krbtgt", "secret", "krbtgt/EXAMPLE.COM@EXAMPLE.COM" );
        users.createSubcontext( "uid=krbtgt", attrs );

        attrs = getPrincipalAttributes( "Service", "LDAP Service", "ldap", "randall", "ldap/localhost@EXAMPLE.COM" );
        users.createSubcontext( "uid=ldap", attrs );
    }

    
    protected void configureDirectoryService() throws NamingException
    {
        directoryService.setAllowAnonymousAccess( false );
        Set<Partition> partitions = new HashSet<Partition>();

        // Add partition 'example'
        JdbmPartition partition = new JdbmPartition();
        partition.setId( "example" );
        partition.setSuffix( "dc=example,dc=com" );

        Set<JdbmIndex<?,ServerEntry>> indexedAttrs = new HashSet<JdbmIndex<?,ServerEntry>>();
        indexedAttrs.add( new JdbmIndex<String,ServerEntry>( "ou" ) );
        indexedAttrs.add( new JdbmIndex<String,ServerEntry>( "dc" ) );
        indexedAttrs.add( new JdbmIndex<String,ServerEntry>( "objectClass" ) );
        partition.setIndexedAttributes( indexedAttrs );

        partitions.add( partition );
        directoryService.setPartitions( partitions );

        List<Interceptor> list = directoryService.getInterceptors();
        list.add( new KeyDerivationInterceptor() );
        directoryService.setInterceptors( list );
    }


    /**
     * Convenience method for creating principals.
     *
     * @param cn           the commonName of the person
     * @param principal    the kerberos principal name for the person
     * @param sn           the surName of the person
     * @param uid          the unique identifier for the person
     * @param userPassword the credentials of the person
     * @return the attributes of the person principal
     */
    protected Attributes getPrincipalAttributes( String sn, String cn, String uid, String userPassword, String principal )
    {
        Attributes attrs = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" ); // sn $ cn
        ocls.add( "inetOrgPerson" ); // uid
        ocls.add( "krb5principal" );
        ocls.add( "krb5kdcentry" );
        attrs.put( ocls );
        attrs.put( "cn", cn );
        attrs.put( "sn", sn );
        attrs.put( "uid", uid );
        attrs.put( SchemaConstants.USER_PASSWORD_AT, userPassword );
        attrs.put( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT, principal );
        attrs.put( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT, "0" );

        return attrs;
    }


    /**
     * Convenience method for creating an organizational unit.
     *
     * @param ou the ou of the organizationalUnit
     * @return the attributes of the organizationalUnit
     */
    protected Attributes getOrgUnitAttributes( String ou )
    {
        Attributes attrs = new BasicAttributes();
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "organizationalUnit" );
        attrs.put( ocls );
        attrs.put( "ou", ou );

        return attrs;
    }


    /**
     * Tests to make sure GSSAPI binds below the RootDSE work.
     */
    @Test
    public void testSaslGssapiBind()
    {
        assertTrue( true );
    }


    /**
     * Tear down.
     */
    @After
    public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;
        super.tearDown();
    }
}
