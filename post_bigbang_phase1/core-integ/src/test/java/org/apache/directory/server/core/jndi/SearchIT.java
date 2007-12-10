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
package org.apache.directory.server.core.jndi;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.integ.CiRunner;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSchemaContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.exception.LdapSizeLimitExceededException;
import org.apache.directory.shared.ldap.exception.LdapTimeLimitExceededException;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;


/**
 * Tests the search() methods of the provider.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( CiRunner.class )
public class SearchIT
{
    private static final String RDN = "cn=Heather Nova";
    private static final String FILTER = "(objectclass=*)";

    public static DirectoryService service;

    /**
     * @todo put this into ldif and use ldif annotation to import
     *
     * @param sysRoot the system root to add entries to
     * @throws NamingException on errors
     */
    protected void createData( LdapContext sysRoot ) throws NamingException
    {
        /*
         * create ou=testing00,ou=system
         */
        Attributes attributes = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        attributes.put( attribute );
        attributes.put( "ou", "testing00" );

        DirContext ctx = sysRoot.createSubcontext( "ou=testing00", attributes );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "ou=testing00" );
        assertNotNull( ctx );
        attributes = ctx.getAttributes( "" );
        assertNotNull( attributes );
        assertEquals( "testing00", attributes.get( "ou" ).get() );
        attribute = attributes.get( "objectClass" );
        assertNotNull( attribute );
        assertTrue( attribute.contains( "top" ) );
        assertTrue( attribute.contains( "organizationalUnit" ) );

        /*
         * create ou=testing01,ou=system
         */
        attributes = new AttributesImpl( true );
        attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        attributes.put( attribute );
        attributes.put( "ou", "testing01" );

        ctx = sysRoot.createSubcontext( "ou=testing01", attributes );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "ou=testing01" );
        assertNotNull( ctx );
        attributes = ctx.getAttributes( "" );
        assertNotNull( attributes );
        assertEquals( "testing01", attributes.get( "ou" ).get() );
        attribute = attributes.get( "objectClass" );
        assertNotNull( attribute );
        assertTrue( attribute.contains( "top" ) );
        assertTrue( attribute.contains( "organizationalUnit" ) );

        /*
         * create ou=testing02,ou=system
         */
        attributes = new AttributesImpl( true );
        attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        attributes.put( attribute );
        attributes.put( "ou", "testing02" );
        ctx = sysRoot.createSubcontext( "ou=testing02", attributes );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "ou=testing02" );
        assertNotNull( ctx );

        attributes = ctx.getAttributes( "" );
        assertNotNull( attributes );
        assertEquals( "testing02", attributes.get( "ou" ).get() );

        attribute = attributes.get( "objectClass" );
        assertNotNull( attribute );
        assertTrue( attribute.contains( "top" ) );
        assertTrue( attribute.contains( "organizationalUnit" ) );

        /*
         * create ou=subtest,ou=testing01,ou=system
         */
        ctx = ( DirContext ) sysRoot.lookup( "ou=testing01" );

        attributes = new AttributesImpl( true );
        attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        attributes.put( attribute );
        attributes.put( "ou", "subtest" );

        ctx = ctx.createSubcontext( "ou=subtest", attributes );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "ou=subtest,ou=testing01" );
        assertNotNull( ctx );

        attributes = ctx.getAttributes( "" );
        assertNotNull( attributes );
        assertEquals( "subtest", attributes.get( "ou" ).get() );

        attribute = attributes.get( "objectClass" );
        assertNotNull( attribute );
        assertTrue( attribute.contains( "top" ) );
        assertTrue( attribute.contains( "organizationalUnit" ) );

        // Create entry cn=Heather Nova, ou=system
        Attributes heather = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        heather.put( ocls );
        heather.put( "cn", "Heather Nova" );
        heather.put( "sn", "Nova" );
        ctx = sysRoot.createSubcontext( RDN, heather );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( RDN );
        assertNotNull( ctx );


        // -------------------------------------------------------------------
        // Enable the nis schema
        // -------------------------------------------------------------------

        // check if nis is disabled
        LdapContext schemaRoot = getSchemaContext( service );
        Attributes nisAttrs = schemaRoot.getAttributes( "cn=nis" );
        boolean isNisDisabled = false;
        if ( nisAttrs.get( "m-disabled" ) != null )
        {
            isNisDisabled = ( ( String ) nisAttrs.get( "m-disabled" ).get() ).equalsIgnoreCase( "TRUE" );
        }

        // if nis is disabled then enable it
        if ( isNisDisabled )
        {
            Attribute disabled = new AttributeImpl( "m-disabled" );
            ModificationItemImpl[] mods = new ModificationItemImpl[] {
                new ModificationItemImpl( DirContext.REMOVE_ATTRIBUTE, disabled ) };
            schemaRoot.modifyAttributes( "cn=nis", mods );
        }

        // -------------------------------------------------------------------
        // Add a bunch of nis groups
        // -------------------------------------------------------------------

        addNisPosixGroup( "testGroup0", 0 );
        addNisPosixGroup( "testGroup1", 1 );
        addNisPosixGroup( "testGroup2", 2 );
        addNisPosixGroup( "testGroup4", 4 );
        addNisPosixGroup( "testGroup5", 5 );
    }


    private DirContext addNisPosixGroup( String name, int gid ) throws NamingException
    {
        Attributes attrs = new AttributesImpl( "objectClass", "top", true );
        attrs.get( "objectClass" ).add( "posixGroup" );
        attrs.put( "cn", name );
        attrs.put( "gidNumber", String.valueOf( gid ) );
        return getSystemContext( service ).createSubcontext( "cn="+name+",ou=groups", attrs );
    }


    @Test
    public void testSearchOneLevel() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String,Attributes> map = new HashMap<String,Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=*)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 6, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
    }


    @Test
    public void testSearchSubTreeLevel() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(ou=*)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect", 11, map.size() );
        assertTrue( map.containsKey( "ou=system" ) );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
        assertTrue( map.containsKey( "ou=subtest,ou=testing01,ou=system" ) );
    }


    @Test
    public void testSearchSubTreeLevelNoAttributes() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[]{ "1.1" } );
        
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(ou=testing02)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect", 1, map.size() );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
        Attributes attrs = map.get( "ou=testing02,ou=system" );
        
        assertEquals( 0, attrs.size() );
    }


    @Test
    public void testSearchSubstringSubTreeLevel() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(objectClass=organ*)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        // 13 because it also matches organizationalPerson which the admin is
        assertEquals( "Expected number of results returned was incorrect", 12, map.size() );
        assertTrue( map.containsKey( "ou=system" ) );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
        assertTrue( map.containsKey( "ou=subtest,ou=testing01,ou=system" ) );
    }


    @Test
    public void testSearchFilterArgs() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(|(ou={0})(ou={1}))", new Object[]
            { "testing00", "testing01" }, controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 2, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
    }


    @Test
    public void testSearchSizeLimit() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setCountLimit( 7 );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(ou=*)", controls );

        try
        {
            while ( list.hasMore() )
            {
                SearchResult result = ( SearchResult ) list.next();
                map.put( result.getName(), result.getAttributes() );
            }
            fail( "Should not get here due to a SizeLimitExceededException" );
        }
        catch ( LdapSizeLimitExceededException e )
        {
        }
        assertEquals( "Expected number of results returned was incorrect", 7, map.size() );
    }


    @Test
    public void testSearchTimeLimit() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setTimeLimit( 200 );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(ou=*)", controls );
        SearchResultFilteringEnumeration srfe = ( SearchResultFilteringEnumeration ) list;
        srfe.addResultFilter( new SearchResultFilter()
        {
            public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
                throws NamingException
            {
                try
                {
                    Thread.sleep( 201 );
                }
                catch ( InterruptedException e )
                {
                    e.printStackTrace();
                }
                return true;
            }
        } );

        try
        {
            while ( list.hasMore() )
            {
                SearchResult result = ( SearchResult ) list.next();
                map.put( result.getName(), result.getAttributes() );
            }
            fail( "Should not get here due to a TimeLimitExceededException" );
        }
        catch ( LdapTimeLimitExceededException e )
        {
        }
        assertEquals( "Expected number of results returned was incorrect", 1, map.size() );
    }
    

    @Test
    public void testFilterExpansion0() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(name=testing00)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }
        assertEquals( "size of results", 1, map.size() );
        assertTrue( "contains ou=testing00,ou=system", map.containsKey( "ou=testing00,ou=system" ) ); 
    }
    

    @Test
    public void testFilterExpansion1() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(name=*)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }
        assertEquals( "size of results", 19, map.size() );
        assertTrue( "contains ou=testing00,ou=system", map.containsKey( "ou=testing00,ou=system" ) ); 
        assertTrue( "contains ou=testing01,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
        assertTrue( "contains ou=testing02,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
        assertTrue( "contains ou=configuration,ou=system", map.containsKey( "ou=configuration,ou=system" ) ); 
        assertTrue( "contains ou=groups,ou=system", map.containsKey( "ou=groups,ou=system" ) ); 
        assertTrue( "contains ou=interceptors,ou=configuration,ou=system", map.containsKey( "ou=interceptors,ou=configuration,ou=system" ) ); 
        assertTrue( "contains ou=partitions,ou=configuration,ou=system", map.containsKey( "ou=partitions,ou=configuration,ou=system" ) ); 
        assertTrue( "contains ou=services,ou=configuration,ou=system", map.containsKey( "ou=services,ou=configuration,ou=system" ) ); 
        assertTrue( "contains ou=subtest,ou=testing01,ou=system", map.containsKey( "ou=subtest,ou=testing01,ou=system" ) ); 
        assertTrue( "contains ou=system", map.containsKey( "ou=system" ) ); 
        assertTrue( "contains ou=users,ou=system", map.containsKey( "ou=users,ou=system" ) ); 
        assertTrue( "contains uid=admin,ou=system", map.containsKey( "uid=admin,ou=system" ) ); 
        assertTrue( "contains cn=administrators,ou=groups,ou=system", map.containsKey( "cn=Administrators,ou=groups,ou=system" ) ); 
    }
    
    
    @Test
    public void testFilterExpansion2() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(|(name=testing00)(name=testing01))", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }
        assertEquals( "size of results", 2, map.size() );
        assertTrue( "contains ou=testing00,ou=system", map.containsKey( "ou=testing00,ou=system" ) ); 
        assertTrue( "contains ou=testing01,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
    }


    @Test
    public void testFilterExpansion4() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration list = sysRoot.search( "", "(name=testing*)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }
        assertEquals( "size of results", 3, map.size() );
        assertTrue( "contains ou=testing00,ou=system", map.containsKey( "ou=testing00,ou=system" ) ); 
        assertTrue( "contains ou=testing01,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
        assertTrue( "contains ou=testing02,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
    }


    @Test
    public void testFilterExpansion5() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        String filter = "(|(2.5.4.11.1=testing*)(2.5.4.54=testing*)(2.5.4.10=testing*)" +
            "(2.5.4.6=testing*)(2.5.4.43=testing*)(2.5.4.7.1=testing*)(2.5.4.10.1=testing*)" +
            "(2.5.4.44=testing*)(2.5.4.11=testing*)(2.5.4.4=testing*)(2.5.4.8.1=testing*)" +
            "(2.5.4.12=testing*)(1.3.6.1.4.1.18060.0.4.1.2.3=testing*)" +
            "(2.5.4.7=testing*)(2.5.4.3=testing*)(2.5.4.8=testing*)(2.5.4.42=testing*))";
        NamingEnumeration list = sysRoot.search( "", filter, controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }
        assertEquals( "size of results", 3, map.size() );
        assertTrue( "contains ou=testing00,ou=system", map.containsKey( "ou=testing00,ou=system" ) ); 
        assertTrue( "contains ou=testing01,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
        assertTrue( "contains ou=testing02,ou=system", map.containsKey( "ou=testing01,ou=system" ) ); 
    }
    

    @Test
    public void testOpAttrDenormalizationOff() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "creatorsName" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing00)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        Attributes attrs = map.get( "ou=testing00,ou=system" );
        assertEquals( "normalized creator's name", "0.9.2342.19200300.100.1.1=admin,2.5.4.11=system", 
            attrs.get( "creatorsName" ).get() );
    }


    @Test
    public void testOpAttrDenormalizationOn() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        service.setDenormalizeOpAttrsEnabled( true );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "creatorsName" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing00)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        Attributes attrs = map.get( "ou=testing00,ou=system" );
        assertEquals( "normalized creator's name", "uid=admin,ou=system", 
            attrs.get( "creatorsName" ).get() );
    }

    
    /**
     * Creation of required attributes of a person entry.
     *
     * @param cn the commonName of the person
     * @param sn the surName of the person
     * @return the attributes of a new person entry
     */
    protected Attributes getPersonAttributes( String sn, String cn )
    {
        Attributes attributes = new AttributesImpl();
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "person" );
        attributes.put( attribute );
        attributes.put( "cn", cn );
        attributes.put( "sn", sn );

        return attributes;
    }


    @Test
    public void testBinaryAttributesInFilter() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        byte[] certData = new byte[] { 0x34, 0x56, 0x4e, 0x5f };
        
        // First let's add a some binary data representing a userCertificate
        Attributes attrs = getPersonAttributes( "Bush", "Kate Bush" );
        attrs.put( "userCertificate", certData );

        Attribute objectClasses = attrs.get( "objectClass" );
        objectClasses.add( "strongAuthenticationUser" );

        sysRoot.createSubcontext( "cn=Kate Bush", attrs );

        // Search for kate by cn first
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        NamingEnumeration enm = sysRoot.search( "", "(cn=Kate Bush)", controls );
        assertTrue( enm.hasMore() );
        SearchResult sr = ( SearchResult ) enm.next();
        assertNotNull( sr );
        assertFalse( enm.hasMore() );
        assertEquals( "cn=Kate Bush,ou=system", sr.getName() );

        enm = sysRoot.search( "", "(userCertificate=\\34\\56\\4E\\5F)", controls );
        assertTrue( enm.hasMore() );
        sr = ( SearchResult ) enm.next();
        assertNotNull( sr );
        assertFalse( enm.hasMore() );
        assertEquals( "cn=Kate Bush,ou=system", sr.getName() );
    }


    @Test
    public void testSearchOperationalAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "+" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNotNull( attrs.get( "createTimestamp" ) );
        assertNotNull( attrs.get( "creatorsName" ) );
        assertNull( attrs.get( "objectClass" ) );
        assertNull( attrs.get( "ou" ) );
    }


    @Test
    public void testSearchUserAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "*" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
        assertNull( attrs.get( "createTimestamp" ) );
        assertNull( attrs.get( "creatorsName" ) );
    }


    @Test
    public void testSearchUserAttrAndOpAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "*", "creatorsName" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
        assertNotNull( attrs.get( "creatorsName" ) );
        assertNull( attrs.get( "createTimestamp" ) );
    }


    @Test
    public void testSearchUserAttrAndNoAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "1.1", "ou" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
        assertNull( attrs.get( "creatorsName" ) );
        assertNull( attrs.get( "createTimestamp" ) );
    }


    @Test
    public void testSearchNoAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "1.1" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNull( attrs.get( "objectClass" ) );
        assertNull( attrs.get( "ou" ) );
        assertNull( attrs.get( "creatorsName" ) );
        assertNull( attrs.get( "createTimestamp" ) );
    }


    @Test
    public void testSearchAllAttr() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "+", "*" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );

        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "ou=testing01,ou=system" );

        assertNotNull( attrs.get( "createTimestamp" ) );
        assertNotNull( attrs.get( "creatorsName" ) );
        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
    }


    /**
     * Search an entry and fetch an attribute with unknown option
     * @throws NamingException if there are errors
     */
    @Test
    public void testSearchFetchNonExistingAttributeOption() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "sn;unknownOption" } );

        NamingEnumeration result = sysRoot.search( RDN, FILTER, ctls );

        if ( result.hasMore() )
        {
            SearchResult entry = ( SearchResult ) result.next();
            Attributes attrs = entry.getAttributes();
            Attribute cn = attrs.get( "cn" );

            assertNotNull( cn );
            assertEquals( "Heather Nova", cn.get().toString() );

            Attribute sn = attrs.get( "sn" );
            assertNull( sn );
        }
        else
        {
            fail( "entry " + RDN + " not found" );
        }

        result.close();
    }


    /**
     * Search an entry and fetch an attribute with twice the same attributeType
     * @throws NamingException if there are errors
     */
    @Test
    public void testSearchFetchTwiceSameAttribute() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "cn" } );

        NamingEnumeration result = sysRoot.search( RDN, FILTER, ctls );

        if ( result.hasMore() )
        {
            SearchResult entry = ( SearchResult ) result.next();
            Attributes attrs = entry.getAttributes();
            Attribute cn = attrs.get( "cn" );

            assertNotNull( cn );
            assertEquals( "Heather Nova", cn.get().toString() );
        }
        else
        {
            fail( "entry " + RDN + " not found" );
        }

        result.close();
    }


    // this one is failing because it returns the admin user twice: count = 15
//    public void testFilterExpansion3() throws Exception
//    {
//        SearchControls controls = new SearchControls();
//        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
//        controls.setDerefLinkFlag( false );
//        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES, AliasDerefMode.NEVER_DEREF_ALIASES );
//        
//        List map = new ArrayList();
//        NamingEnumeration list = sysRoot.search( "", "(name=*)", controls );
//        while ( list.hasMore() )
//        {
//            SearchResult result = ( SearchResult ) list.next();
//            map.add( result.getName() );
//        }
//        assertEquals( "size of results", 14, map.size() );
//        assertTrue( "contains ou=testing00,ou=system", map.contains( "ou=testing00,ou=system" ) ); 
//        assertTrue( "contains ou=testing01,ou=system", map.contains( "ou=testing01,ou=system" ) ); 
//        assertTrue( "contains ou=testing02,ou=system", map.contains( "ou=testing01,ou=system" ) ); 
//        assertTrue( "contains uid=akarasulu,ou=users,ou=system", map.contains( "uid=akarasulu,ou=users,ou=system" ) ); 
//        assertTrue( "contains ou=configuration,ou=system", map.contains( "ou=configuration,ou=system" ) ); 
//        assertTrue( "contains ou=groups,ou=system", map.contains( "ou=groups,ou=system" ) ); 
//        assertTrue( "contains ou=interceptors,ou=configuration,ou=system", map.contains( "ou=interceptors,ou=configuration,ou=system" ) ); 
//        assertTrue( "contains ou=partitions,ou=configuration,ou=system", map.contains( "ou=partitions,ou=configuration,ou=system" ) ); 
//        assertTrue( "contains ou=services,ou=configuration,ou=system", map.contains( "ou=services,ou=configuration,ou=system" ) ); 
//        assertTrue( "contains ou=subtest,ou=testing01,ou=system", map.contains( "ou=subtest,ou=testing01,ou=system" ) ); 
//        assertTrue( "contains ou=system", map.contains( "ou=system" ) ); 
//        assertTrue( "contains ou=users,ou=system", map.contains( "ou=users,ou=system" ) ); 
//        assertTrue( "contains uid=admin,ou=system", map.contains( "uid=admin,ou=system" ) ); 
//        assertTrue( "contains cn=administrators,ou=groups,ou=system", map.contains( "cn=administrators,ou=groups,ou=system" ) ); 
//    }



    /**
     *  Convenience method that performs a one level search using the
     *  specified filter returning their DNs as Strings in a set.
     *
     * @param controls the search controls
     * @param filter the filter expression
     * @return the set of groups
     * @throws NamingException if there are problems conducting the search
     */
    public Set<String> searchGroups( String filter, SearchControls controls ) throws NamingException
    {
        if ( controls == null )
        {
            controls = new SearchControls();
        }

        Set<String> results = new HashSet<String>();
        NamingEnumeration list = getSystemContext( service ).search( "ou=groups", filter, controls );

        while( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            results.add( result.getName() );
        }

        return results;
    }


    /**
     *  Convenience method that performs a one level search using the
     *  specified filter returning their DNs as Strings in a set.
     *
     * @param filter the filter expression
     * @return the set of group names
     * @throws NamingException if there are problems conducting the search
     */
    public Set<String> searchGroups( String filter ) throws NamingException
    {
        return searchGroups( filter, null );
    }


    @Test
    public void testSetup() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set results = searchGroups( "(objectClass=posixGroup)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testLessThanSearch() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set results = searchGroups( "(gidNumber<=5)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber<=4)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber<=3)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber<=0)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber<=-1)" );
        assertFalse( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testGreaterThanSearch() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set results = searchGroups( "(gidNumber>=0)" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber>=1)" );
        assertFalse( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber>=3)" );
        assertFalse( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );

        results = searchGroups( "(gidNumber>=6)" );
        assertFalse( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testNotOperator() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set results = searchGroups( "(!(gidNumber=4))" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testNotOperatorSubtree() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        Set results = searchGroups( "(!(gidNumber=4))", controls );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }
}
