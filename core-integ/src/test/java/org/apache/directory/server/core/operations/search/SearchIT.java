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
package org.apache.directory.server.core.operations.search;


import static org.apache.directory.server.core.integ.IntegrationUtils.getSchemaContext;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.integ.CiRunner;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.exception.LdapSizeLimitExceededException;
import org.apache.directory.shared.ldap.exception.LdapTimeLimitExceededException;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
 

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
    protected void createData( LdapContext sysRoot ) throws Exception
    {
        /*
         * create ou=testing00,ou=system
         */
        Attributes attributes = new BasicAttributes( true );
        Attribute attribute = new BasicAttribute( "objectClass" );
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
        attributes = new BasicAttributes( true );
        attribute = new BasicAttribute( "objectClass" );
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
        attributes = new BasicAttributes( true );
        attribute = new BasicAttribute( "objectClass" );
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

        attributes = new BasicAttributes( true );
        attribute = new BasicAttribute( "objectClass" );
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
        Attributes heather = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
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
            Attribute disabled = new BasicAttribute( "m-disabled" );
            ModificationItem[] mods = new ModificationItem[] {
                new ModificationItem( DirContext.REMOVE_ATTRIBUTE, disabled ) };
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


    private DirContext addNisPosixGroup( String name, int gid ) throws Exception
    {
        Attributes attrs = new BasicAttributes( "objectClass", "top", true );
        attrs.get( "objectClass" ).add( "posixGroup" );
        attrs.put( "cn", name );
        attrs.put( "gidNumber", String.valueOf( gid ) );
        return getSystemContext( service ).createSubcontext( "cn="+name+",ou=groups", attrs );
    }


    @Test
    public void testSearchOneLevel() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String,Attributes> map = new HashMap<String,Attributes>();

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=*)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 6, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
    }


    @Test
    public void testSearchSubTreeLevel() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=*)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchSubTreeLevelNoAttributes() throws Exception
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing02)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect", 1, map.size() );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
        Attributes attrs = map.get( "ou=testing02,ou=system" );
        
        assertEquals( 0, attrs.size() );
    }


    @Test
    public void testSearchSubstringSubTreeLevel() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );

        HashMap<String, Attributes> map = new HashMap<String, Attributes>();
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(objectClass=organ*)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        // 13 because it also matches organizationalPerson which the admin is
        assertEquals( "Expected number of results returned was incorrect", 13, map.size() );
        assertTrue( map.containsKey( "ou=system" ) );
        assertTrue( map.containsKey( "ou=configuration,ou=system" ) );
        assertTrue( map.containsKey( "ou=interceptors,ou=configuration,ou=system" ) );
        assertTrue( map.containsKey( "ou=partitions,ou=configuration,ou=system" ) );
        assertTrue( map.containsKey( "ou=services,ou=configuration,ou=system" ) );
        assertTrue( map.containsKey( "ou=groups,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=subtest,ou=testing01,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing02,ou=system" ) );
        assertTrue( map.containsKey( "ou=users,ou=system" ) );
        assertTrue( map.containsKey( "prefNodeName=sysPrefRoot,ou=system" ) );
        assertTrue( map.containsKey( "uid=admin,ou=system" ) );
    }


    /**
     * Tests to make sure undefined attributes in filter assertions are pruned and do not
     * result in exceptions.
     */
    @Test
    public void testBogusAttributeInSearchFilter() throws Exception
    {
        boolean oldSetAllowAnnonymousAccess = service.isAllowAnonymousAccess();
        service.setAllowAnonymousAccess( true );

        LdapContext sysRoot = getSystemContext( service );
        SearchControls cons = new SearchControls();
        NamingEnumeration<SearchResult> e = sysRoot.search( "", "(bogusAttribute=abc123)", cons );
        assertNotNull( e );
        
        e = sysRoot.search( "", "(!(bogusAttribute=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );
        
        e = sysRoot.search( "", "(|(bogusAttribute=abc123)(bogusAttribute=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );
        
        e = sysRoot.search( "", "(|(bogusAttribute=abc123)(ou=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );

        e = sysRoot.search( "", "(OBJECTclass=*)", cons );
        assertNotNull( e );
        assertTrue( e.hasMore() );

        e = sysRoot.search( "", "(objectclass=*)", cons );
        assertNotNull( e );
        
        service.setAllowAnonymousAccess( oldSetAllowAnnonymousAccess );
    }


    @Test
    public void testSearchFilterArgs() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(|(ou={0})(ou={1}))", new Object[]
            { "testing00", "testing01" }, controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 2, map.size() );
        assertTrue( map.containsKey( "ou=testing00,ou=system" ) );
        assertTrue( map.containsKey( "ou=testing01,ou=system" ) );
    }


    @Test
    @Ignore ( "TODO - fix me" )
    public void testSearchSizeLimit() throws Exception
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=*)", controls );

        try
        {
            while ( list.hasMore() )
            {
                SearchResult result = list.next();
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
    @Ignore ( "TODO - fix me" )
    public void testSearchTimeLimit() throws Exception, InterruptedException
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=*)", controls );

        try
        {
            while ( list.hasMore() )
            {
                SearchResult result = ( SearchResult ) list.next();
                
                // leep 201 ms before fetching the next element ...
            	Thread.sleep( 201 );
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(name=testing00)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(name=*)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(|(name=testing00)(name=testing01))", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(name=testing*)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
        NamingEnumeration<SearchResult> list = sysRoot.search( "", filter, controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing00)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing00)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
        Attributes attributes = new BasicAttributes( true );
        Attribute attribute = new BasicAttribute( "objectClass" );
        attribute.add( "top" );
        attribute.add( "person" );
        attributes.put( attribute );
        attributes.put( "cn", cn );
        attributes.put( "sn", sn );

        return attributes;
    }


    @Test
    public void testBinaryAttributesInFilter() throws Exception
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
        NamingEnumeration<SearchResult> enm = sysRoot.search( "", "(cn=Kate Bush)", controls );
        assertTrue( enm.hasMore() );
        SearchResult sr = enm.next();
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
    public void testSearchOperationalAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchUserAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchUserAttrAndOpAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchUserAttrAndNoAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchNoAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchAllAttr() throws Exception
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

        NamingEnumeration<SearchResult> list = sysRoot.search( "", "(ou=testing01)", controls );
        

        while ( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public void testSearchFetchNonExistingAttributeOption() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "sn;unknownOption", "badAttr" } );

        NamingEnumeration<SearchResult> result = sysRoot.search( RDN, FILTER, ctls );

        if ( result.hasMore() )
        {
            SearchResult entry = result.next();
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
    public void testSearchFetchTwiceSameAttribute() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "cn" } );

        NamingEnumeration<SearchResult> result = sysRoot.search( RDN, FILTER, ctls );

        if ( result.hasMore() )
        {
            SearchResult entry = result.next();
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
    public Set<String> searchGroups( String filter, SearchControls controls ) throws Exception
    {
        if ( controls == null )
        {
            controls = new SearchControls();
        }

        Set<String> results = new HashSet<String>();
        NamingEnumeration<SearchResult> list = getSystemContext( service ).search( "ou=groups", filter, controls );

        while( list.hasMore() )
        {
            SearchResult result = list.next();
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
    public Set<String> searchGroups( String filter ) throws Exception
    {
        return searchGroups( filter, null );
    }


    @Test
    public void testSetup() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set<String> results = searchGroups( "(objectClass=posixGroup)" );
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

        Set<String> results = searchGroups( "(gidNumber<=5)" );
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

        Set<String> results = searchGroups( "(gidNumber>=0)" );
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
    public void testNotOperator() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        Set<String> results = searchGroups( "(!(gidNumber=4))" );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testNotOperatorSubtree() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        Set<String> results = searchGroups( "(!(gidNumber=4))", controls );
        assertTrue( results.contains( "cn=testGroup0,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup1,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup2,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup3,ou=groups,ou=system" ) );
        assertFalse( results.contains( "cn=testGroup4,ou=groups,ou=system" ) );
        assertTrue( results.contains( "cn=testGroup5,ou=groups,ou=system" ) );
    }


    @Test
    public void testSearchWithEscapedCharsInFilter() throws Exception
    {
        // Create an entry with special chars in the description attribute
        LdapContext sysRoot = getSystemContext( service );
        // Create entry cn=Sid Vicious, ou=system
        Attributes vicious = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        vicious.put( ocls );
        vicious.put( "cn", "Sid Vicious" );
        vicious.put( "sn", "Vicious" );
        vicious.put( "description", "(sex*pis\\tols)" );
        DirContext ctx = sysRoot.createSubcontext( "cn=Sid Vicious", vicious );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "cn=Sid Vicious" );
        assertNotNull( ctx );
        
        Attributes attributes = ctx.getAttributes( "" );
        
        assertEquals( "(sex*pis\\tols)", attributes.get( "description" ).get() );

        // Now, search for the description
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );

        controls.setReturningAttributes( new String[]
                    { "*" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES, AliasDerefMode.NEVER_DEREF_ALIASES
                     .getJndiValue() );
        HashMap<String, Attributes> map = new HashMap<String, Attributes>();

        NamingEnumeration<SearchResult> list = sysRoot
            .search( "", "(description=\\28sex\\2Apis\\5Ctols\\29)", controls );

        while ( list.hasMore() )
        {
            SearchResult result = list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = map.get( "cn=Sid Vicious,ou=system" );

        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "cn" ) );
    }
    
    
    @Test
    public void testSubstringSearchWithEscapedCharsInFilter() throws Exception
    {
        // Create an entry with special chars in the description attribute
        LdapContext sysRoot = getSystemContext( service );
        // Create entry cn=Sid Vicious, ou=system
        Attributes vicious = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        vicious.put( ocls );
        vicious.put( "cn", "Sid Vicious" );
        vicious.put( "sn", "Vicious" );
        vicious.put( "description", "(sex*pis\\tols)" );
        DirContext ctx = sysRoot.createSubcontext( "cn=Sid Vicious", vicious );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( "cn=Sid Vicious" );
        assertNotNull( ctx );

        Attributes attributes = ctx.getAttributes( "" );

        assertEquals( "(sex*pis\\tols)", attributes.get( "description" ).get() );

        // Now, search for the description
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[]
            { "*" } );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES, AliasDerefMode.NEVER_DEREF_ALIASES
            .getJndiValue() );

        String[] filters = new String[]
            { "(description=*\\28*)", "(description=*\\29*)", "(description=*\\2A*)", "(description=*\\5C*)" };
        for ( String filter : filters )
        {
            HashMap<String, Attributes> map = new HashMap<String, Attributes>();
            NamingEnumeration<SearchResult> list = sysRoot.search( "", filter, controls );

            while ( list.hasMore() )
            {
                SearchResult result = list.next();
                map.put( result.getName(), result.getAttributes() );
            }

            assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

            Attributes attrs = map.get( "cn=Sid Vicious,ou=system" );

            assertNotNull( attrs.get( "objectClass" ) );
            assertNotNull( attrs.get( "cn" ) );
        }
    }


    @Test
    public void testSubstringSearchWithEscapedAsterisksInFilter_DIRSERVER_1181() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        Attributes vicious = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        vicious.put( ocls );
        vicious.put( "cn", "x*y*z*" );
        vicious.put( "sn", "x*y*z*" );
        sysRoot.createSubcontext( "cn=x*y*z*", vicious );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setReturningAttributes( new String[]
            { "cn" } );
        NamingEnumeration<SearchResult> res;

        res = sysRoot.search( "", "(cn=*x\\2Ay\\2Az\\2A*)", controls );
        assertTrue( res.hasMore() );
        assertEquals( "x*y*z*", res.next().getAttributes().get( "cn" ).get() );
        assertFalse( res.hasMore() );

        res = sysRoot.search( "", "(cn=*{0}*)", new String[]
            { "x*y*z*" }, controls );
        assertTrue( res.hasMore() );
        assertEquals( "x*y*z*", res.next().getAttributes().get( "cn" ).get() );
        assertFalse( res.hasMore() );
    }


    /**
     * Test a search with a bad filter : there is a missing closing ')'
     */
    @Test
    public void testBadFilter() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setDerefLinkFlag( false );
        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
                AliasDerefMode.NEVER_DEREF_ALIASES.getJndiValue() );
        
        try
        {
            sysRoot.search( "", "(|(name=testing00)(name=testing01)", controls );
            fail();
        }
        catch ( InvalidSearchFilterException isfe )
        {
            assertTrue( true );
        }
    }


    /**
     * Search operation with a base DN with quotes
     * Commented as it's not valid by RFC 5514
    @Test
    public void testSearchWithQuotesInBase() throws NamingException 
    {
        LdapContext sysRoot = getSystemContext( service );
        createData( sysRoot );

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        String filter = "(cn=Tori Amos)";
        ctls.setReturningAttributes( new String[]
            { "cn", "cn" } );

        // Search for cn="Tori Amos" (with quotes)
        String base = "cn=\"Tori Amos\"";

        try {
            // Check entry
            NamingEnumeration<SearchResult> result = sysRoot.search( base, filter, ctls );
            assertTrue( result.hasMore() );
            
            while ( result.hasMore() ) 
            {
                SearchResult sr = result.next();
                Attributes attrs = sr.getAttributes();
                Attribute sn = attrs.get( "cn" );
                assertNotNull(sn);
                assertTrue( sn.contains( "Amos" ) );
            }
        } catch (Exception e) 
        {
            fail( e.getMessage() );
        }
    }
    */
}
