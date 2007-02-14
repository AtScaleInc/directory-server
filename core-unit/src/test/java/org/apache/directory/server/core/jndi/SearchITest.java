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


import java.util.HashMap;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.unit.AbstractAdminTestCase;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeIdentifierException;
import org.apache.directory.shared.ldap.message.LockableAttributeImpl;
import org.apache.directory.shared.ldap.message.LockableAttributesImpl;
import org.apache.directory.shared.ldap.message.DerefAliasesEnum;


/**
 * Tests the search() methods of the provider.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 493916 $
 */
public class SearchITest extends AbstractAdminTestCase
{
    private static final String rdn = "cn=Heather Nova";
    private static final String filter = "(objectclass=*)";


    protected void setUp() throws Exception
    {
        if ( this.getName().equals( "testOpAttrDenormalizationOn" ) )
        {
            super.configuration.setDenormalizeOpAttrsEnabled( true );
        }
        
        super.setUp();

        /*
         * create ou=testing00,ou=system
         */
        Attributes attributes = new LockableAttributesImpl( true );
        Attribute attribute = new LockableAttributeImpl( "objectClass" );
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
        attributes = new LockableAttributesImpl( true );
        attribute = new LockableAttributeImpl( "objectClass" );
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
        attributes = new LockableAttributesImpl( true );
        attribute = new LockableAttributeImpl( "objectClass" );
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

        attributes = new LockableAttributesImpl( true );
        attribute = new LockableAttributeImpl( "objectClass" );
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
        Attributes heather = new LockableAttributesImpl();
        Attribute ocls = new LockableAttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        heather.put( ocls );
        heather.put( "cn", "Heather Nova" );
        heather.put( "sn", "Nova" );
        ctx = ( DirContext ) sysRoot.createSubcontext( rdn, heather );
        assertNotNull( ctx );

        ctx = ( DirContext ) sysRoot.lookup( rdn );
        assertNotNull( ctx );
    }


    public void testSearchOperationalAttr() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "+" } );
        sysRoot.addToEnvironment( DerefAliasesEnum.JNDI_PROP, DerefAliasesEnum.NEVERDEREFALIASES.getName() );
        HashMap map = new HashMap();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = (Attributes)map.get( "ou=testing01,ou=system" );
        
        assertNotNull( attrs.get( "createTimestamp" ) );
        assertNotNull( attrs.get( "creatorsName" ) );
        assertNull( attrs.get( "objectClass" ) );
        assertNull( attrs.get( "ou" ) );
    }

    public void testSearchUserAttr() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "*" } );
        sysRoot.addToEnvironment( DerefAliasesEnum.JNDI_PROP, DerefAliasesEnum.NEVERDEREFALIASES.getName() );
        HashMap map = new HashMap();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );
        
        Attributes attrs = (Attributes)map.get( "ou=testing01,ou=system" );
        
        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
        assertNull( attrs.get( "createTimestamp" ) );
        assertNull( attrs.get( "creatorsName" ) );
    }


    public void testSearchAllAttr() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        controls.setDerefLinkFlag( false );
        controls.setReturningAttributes( new String[] { "+", "*" } );
        sysRoot.addToEnvironment( DerefAliasesEnum.JNDI_PROP, DerefAliasesEnum.NEVERDEREFALIASES.getName() );
        HashMap map = new HashMap();

        NamingEnumeration list = sysRoot.search( "", "(ou=testing01)", controls );
        
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            map.put( result.getName(), result.getAttributes() );
        }

        assertEquals( "Expected number of results returned was incorrect!", 1, map.size() );

        Attributes attrs = (Attributes)map.get( "ou=testing01,ou=system" );
        
        assertNotNull( attrs.get( "createTimestamp" ) );
        assertNotNull( attrs.get( "creatorsName" ) );
        assertNotNull( attrs.get( "objectClass" ) );
        assertNotNull( attrs.get( "ou" ) );
    }
    

    /**
     * Search an entry and fetch an unknown attribute
     */
    public void testSearchFetchNonExistingAttribute() throws NamingException
    {
        SearchControls ctls = new SearchControls();

        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "unknownAttribute" } );
        NamingEnumeration result = null;
        try
        {
            result = sysRoot.search( rdn, filter, ctls );
            fail( "should never get here due to an invalid attribute exception" );
        }
        catch ( LdapInvalidAttributeIdentifierException e )
        {
        }
        finally
        {
            if ( result != null )
            {
                result.close();
            }
        }
    }

    
    /**
     * Search an entry and fetch an attribute with unknown option
     */
    public void testSearchFetchNonExistingAttributeOption() throws NamingException
    {
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "sn;unknownOption" } );
        NamingEnumeration result = null;
        try
        {
            result = sysRoot.search( rdn, filter, ctls );
            fail( "should never get here due to an invalid attribute exception" );
        }
        catch ( LdapInvalidAttributeIdentifierException e )
        {
        }
        finally
        {
            if ( result != null )
            {
                result.close();
            }
        }
    }

    /**
     * Search an entry and fetch an attribute with twice the same attributeType
     */
    public void testSearchFetchTwiceSameAttribute() throws NamingException
    {
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );
        ctls.setReturningAttributes( new String[]
            { "cn", "cn" } );

        NamingEnumeration result = sysRoot.search( rdn, filter, ctls );

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
            fail( "entry " + rdn + " not found" );
        }

        result.close();
    }
}
