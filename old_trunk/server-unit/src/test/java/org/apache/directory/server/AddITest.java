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


import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.SchemaViolationException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPAttributeSet;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPException;

import org.apache.directory.server.unit.AbstractServerFastTest;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;


/**
 * Various add scenario tests.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 569879 $
 */
public class AddITest extends AbstractServerFastTest
{
    private static final String RDN = "cn=The Person";

    /**
     * Create an entry for a person.
     */
    @Before
    public void setUp() throws Exception
    {
        // Create a person
        Attributes attributes = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "person" );
        attributes.put( attribute );
        attributes.put( "cn", "The Person" );
        attributes.put( "sn", "Person" );
        attributes.put( "description", "this is a person" );
        DirContext person = ctx.createSubcontext( RDN, attributes );

        assertNotNull( person );
    }
    
    @After
    public void cleanUp() throws NamingException
    {
        ctx.destroySubcontext( RDN );
    }


    /**
     * Just a little test to check wether the person is created correctly after
     * setup.
     * 
     * @throws NamingException
     */
    @Test
    public void testSetUpTearDown() throws NamingException
    {
        DirContext person = ( DirContext ) ctx.lookup( RDN );
        assertNotNull( person );

        // Check object classes

        Attributes attributes = person.getAttributes( "" );
        Attribute ocls = attributes.get( "objectClass" );

        String[] expectedOcls =
            { "top", "person" };
        for ( int i = 0; i < expectedOcls.length; i++ )
        {
            String name = expectedOcls[i];
            assertTrue( "object class " + name + " is NOT present when it should be!", ocls.contains( name ) );
        }
    }


    /**
     * This is the original defect as in JIRA DIREVE-216.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddObjectClasses() throws NamingException
    {

        // modify object classes, add two more
        Attributes attributes = new AttributesImpl( true );
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "organizationalPerson" );
        ocls.add( "inetOrgPerson" );
        attributes.put( ocls );

        DirContext person = ( DirContext ) ctx.lookup( RDN );
        person.modifyAttributes( "", DirContext.ADD_ATTRIBUTE, attributes );

        // Read again from directory
        person = ( DirContext ) ctx.lookup( RDN );
        attributes = person.getAttributes( "" );
        Attribute newOcls = attributes.get( "objectClass" );

        String[] expectedOcls =
            { "top", "person", "organizationalPerson", "inetOrgPerson" };
        for ( int i = 0; i < expectedOcls.length; i++ )
        {
            String name = expectedOcls[i];
            assertTrue( "object class " + name + " is present", newOcls.contains( name ) );
        }
    }


    /**
     * This changes a single attribute value. Just as a reference.
     * 
     * @throws NamingException
     */
    @Test
    public void testModifyDescription() throws NamingException
    {
        String newDescription = "More info on the user ...";

        // modify object classes, add two more
        Attributes attributes = new AttributesImpl( true );
        Attribute desc = new AttributeImpl( "description", newDescription );
        attributes.put( desc );

        DirContext person = ( DirContext ) ctx.lookup( RDN );
        person.modifyAttributes( "", DirContext.REPLACE_ATTRIBUTE, attributes );

        // Read again from directory
        person = ( DirContext ) ctx.lookup( RDN );
        attributes = person.getAttributes( "" );
        Attribute newDesc = attributes.get( "description" );

        assertTrue( "new Description", newDesc.contains( newDescription ) );
    }


    /**
     * Try to add entry with required attribute missing.
     * 
     * @throws NamingException 
     */
    @Test
    public void testAddWithMissingRequiredAttributes() throws NamingException
    {
        // person without sn
        Attributes attrs = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        attrs.put( ocls );
        attrs.put( "cn", "Fiona Apple" );

        try
        {
            ctx.createSubcontext( "cn=Fiona Apple", attrs );
            fail( "creation of entry should fail" );
        }
        catch ( SchemaViolationException e )
        {
            // expected
        }
    }
    
    

    static final String HOST = "localhost";
    static final String USER = "uid=admin,ou=system";
    static final String PASSWORD = "secret";
    static final String BASE = "ou=system";


    /**
     * Testcase to demonstrate DIRSERVER-643 ("Netscape SDK: Adding an entry with
     * two description attributes does not combine values."). Uses Sun ONE Directory
     * SDK for Java 4.1 , or comparable (Netscape, Mozilla).
     * 
     * @throws LDAPException 
     */
    @Test
    public void testAddEntryWithTwoDescriptions() throws LDAPException
    {
        LDAPConnection con = new LDAPConnection();
        con.connect( 3, HOST, port, USER, PASSWORD );
        LDAPAttributeSet attrs = new LDAPAttributeSet();
        LDAPAttribute ocls = new LDAPAttribute( "objectclass", new String[]
            { "top", "person" } );
        attrs.add( ocls );
        attrs.add( new LDAPAttribute( "sn", "Bush" ) );
        attrs.add( new LDAPAttribute( "cn", "Kate Bush" ) );

        String descr[] =
            { "a British singer-songwriter with an expressive four-octave voice",
                "one of the most influential female artists of the twentieth century" };

        attrs.add( new LDAPAttribute( "description", descr ) );

        String dn = "cn=Kate Bush," + BASE;
        LDAPEntry kate = new LDAPEntry( dn, attrs );

        con.add( kate );

        // Analyze entry and description attribute
        LDAPEntry kateReloaded = con.read( dn );
        assertNotNull( kateReloaded );
        LDAPAttribute attr = kateReloaded.getAttribute( "description" );
        assertNotNull( attr );
        assertEquals( 2, attr.getStringValueArray().length );

        // Remove entry
        con.delete( dn );
        con.disconnect();
    }


    /**
     * Testcase to demonstrate DIRSERVER-643 ("Netscape SDK: Adding an entry with
     * two description attributes does not combine values."). Uses Sun ONE Directory
     * SDK for Java 4.1 , or comparable (Netscape, Mozilla).
     * 
     * @throws LDAPException 
     */
    @Test
    public void testAddEntryWithTwoDescriptionsVariant() throws LDAPException
    {
        LDAPConnection con = new LDAPConnection();
        con.connect( 3, HOST, port, USER, PASSWORD );
        LDAPAttributeSet attrs = new LDAPAttributeSet();
        LDAPAttribute ocls = new LDAPAttribute( "objectclass", new String[]
            { "top", "person" } );
        attrs.add( ocls );
        attrs.add( new LDAPAttribute( "sn", "Bush" ) );
        attrs.add( new LDAPAttribute( "cn", "Kate Bush" ) );

        String descr[] =
            { "a British singer-songwriter with an expressive four-octave voice",
                "one of the most influential female artists of the twentieth century" };

        attrs.add( new LDAPAttribute( "description", descr[0] ) );
        attrs.add( new LDAPAttribute( "description", descr[1] ) );

        String dn = "cn=Kate Bush," + BASE;
        LDAPEntry kate = new LDAPEntry( dn, attrs );

        con.add( kate );

        // Analyze entry and description attribute
        LDAPEntry kateReloaded = con.read( dn );
        assertNotNull( kateReloaded );
        LDAPAttribute attr = kateReloaded.getAttribute( "description" );
        assertNotNull( attr );
        assertEquals( 2, attr.getStringValueArray().length );

        // Remove entry
        con.delete( dn );
        con.disconnect();
    }


    /**
     * Testcase to demonstrate DIRSERVER-643 ("Netscape SDK: Adding an entry with
     * two description attributes does not combine values."). Uses Sun ONE Directory
     * SDK for Java 4.1 , or comparable (Netscape, Mozilla).
     * 
     * @throws LDAPException 
     */
    @Test
    public void testAddEntryWithTwoDescriptionsSecondVariant() throws LDAPException
    {
        LDAPConnection con = new LDAPConnection();
        con.connect( 3, HOST, port, USER, PASSWORD );
        LDAPAttributeSet attrs = new LDAPAttributeSet();
        LDAPAttribute ocls = new LDAPAttribute( "objectclass", new String[]
            { "top", "person" } );
        attrs.add( ocls );
        attrs.add( new LDAPAttribute( "sn", "Bush" ) );

        String descr[] =
            { "a British singer-songwriter with an expressive four-octave voice",
                "one of the most influential female artists of the twentieth century" };

        attrs.add( new LDAPAttribute( "description", descr[0] ) );
        attrs.add( new LDAPAttribute( "cn", "Kate Bush" ) );
        attrs.add( new LDAPAttribute( "description", descr[1] ) );

        String dn = "cn=Kate Bush," + BASE;
        LDAPEntry kate = new LDAPEntry( dn, attrs );

        con.add( kate );

        // Analyze entry and description attribute
        LDAPEntry kateReloaded = con.read( dn );
        assertNotNull( kateReloaded );
        LDAPAttribute attr = kateReloaded.getAttribute( "description" );
        assertNotNull( attr );
        assertEquals( 2, attr.getStringValueArray().length );

        // Remove entry
        con.delete( dn );
        con.disconnect();
    }
    
    /**
     * Try to add entry with invalid number of values for a single-valued atribute
     * 
     * @throws NamingException 
     * @see <a href="http://issues.apache.org/jira/browse/DIRSERVER-614">DIRSERVER-614</a>
     */
    @Test
    public void testAddWithInvalidNumberOfAttributeValues() throws NamingException
    {
        // add inetOrgPerson with two displayNames
        Attributes attrs = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "inetOrgPerson" );
        attrs.put( ocls );
        attrs.put( "cn", "Fiona Apple" );
        attrs.put( "sn", "Apple" );
        Attribute displayName = new AttributeImpl( "displayName" );
        displayName.add( "Fiona" );
        displayName.add( "Fiona A." );
        attrs.put( displayName );

        try
        {
            ctx.createSubcontext( "cn=Fiona Apple", attrs );
            fail( "creation of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            
        }
    }


    /**
     * Try to add entry and an alias to it. Afterwards, remove it.
     * 
     * @throws NamingException 
     */
    @Test
    public void testAddAlias() throws NamingException
    {

        // Create entry
        Attributes entry = new AttributesImpl();
        Attribute entryOcls = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        entryOcls.add( SchemaConstants.TOP_OC );
        entryOcls.add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );
        entry.put( entryOcls );
        entry.put( SchemaConstants.OU_AT, "favorite" );
        String entryRdn = "ou=favorite";
        ctx.createSubcontext( entryRdn, entry );

        // Create Alias
        String aliasedObjectName = entryRdn + "," + ctx.getNameInNamespace();
        Attributes alias = new AttributesImpl();
        Attribute aliasOcls = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        aliasOcls.add( SchemaConstants.TOP_OC );
        aliasOcls.add( SchemaConstants.EXTENSIBLE_OBJECT_OC );
        aliasOcls.add( SchemaConstants.ALIAS_OC );
        alias.put( aliasOcls );
        alias.put( SchemaConstants.OU_AT, "bestFruit" );
        alias.put( SchemaConstants.ALIASED_OBJECT_NAME_AT, aliasedObjectName );
        String rdnAlias = "ou=bestFruit";
        ctx.createSubcontext( rdnAlias, alias );

        // Remove alias and entry
        ctx.destroySubcontext( rdnAlias );
        ctx.destroySubcontext( entryRdn );
    }


    /**
     * Try to add entry and an alias to it. Afterwards, remove it. This version
     * cretes a container entry before the operations.
     * 
     * @throws NamingException 
     */
    @Test
    public void testAddAliasInContainer() throws NamingException
    {
        // Create container
        Attributes container = new AttributesImpl();
        Attribute containerOcls = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        containerOcls.add( SchemaConstants.TOP_OC );
        containerOcls.add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );
        container.put( containerOcls );
        container.put( SchemaConstants.OU_AT, "Fruits" );
        String containerRdn = "ou=Fruits";
        DirContext containerCtx = ctx.createSubcontext( containerRdn, container );

        // Create entry
        Attributes entry = new AttributesImpl();
        Attribute entryOcls = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        entryOcls.add( SchemaConstants.TOP_OC );
        entryOcls.add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );
        entry.put( entryOcls );
        entry.put( SchemaConstants.OU_AT, "favorite" );
        String entryRdn = "ou=favorite";
        containerCtx.createSubcontext( entryRdn, entry );

        // Create alias ou=bestFruit,ou=Fruits to entry ou=favorite,ou=Fruits
        String aliasedObjectName = entryRdn + "," + containerCtx.getNameInNamespace();
        Attributes alias = new AttributesImpl();
        Attribute aliasOcls = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        aliasOcls.add( SchemaConstants.TOP_OC );
        aliasOcls.add( SchemaConstants.EXTENSIBLE_OBJECT_OC );
        aliasOcls.add( SchemaConstants.ALIAS_OC );
        alias.put( aliasOcls );
        alias.put( SchemaConstants.OU_AT, "bestFruit" );
        alias.put( SchemaConstants.ALIASED_OBJECT_NAME_AT, aliasedObjectName );
        String rdnAlias = "ou=bestFruit";
        containerCtx.createSubcontext( rdnAlias, alias );

        // search one level scope for alias 
        SearchControls controls = new SearchControls();
        controls.setDerefLinkFlag( true );
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        containerCtx.addToEnvironment( "java.naming.ldap.derefAliases", "never" );
        NamingEnumeration<SearchResult> ne = containerCtx.search( "", "(objectClass=*)", controls );
        assertTrue( ne.hasMore() );
        SearchResult sr = ne.next();
        assertEquals( "ou=favorite", sr.getName() );
        assertTrue( ne.hasMore() );
        sr = ne.next();
        assertEquals( "ou=bestFruit", sr.getName() );
        
        // search one level with dereferencing turned on
        controls = new SearchControls();
        controls.setDerefLinkFlag( true );
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        containerCtx.addToEnvironment( "java.naming.ldap.derefAliases", "always" );
        ne = containerCtx.search( "", "(objectClass=*)", controls );
        assertTrue( ne.hasMore() );
        sr = ne.next();
        assertEquals( "ou=favorite", sr.getName() );
        assertFalse( ne.hasMore() );
        
        // search with base set to alias and dereferencing turned on
        controls = new SearchControls();
        controls.setDerefLinkFlag( false );
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        containerCtx.addToEnvironment( "java.naming.ldap.derefAliases", "always" );
        ne = containerCtx.search( "ou=bestFruit", "(objectClass=*)", controls );
        assertTrue( ne.hasMore() );
        sr = ne.next();
        assertEquals( "ldap://localhost:" + port + "/ou=favorite,ou=Fruits,ou=system", sr.getName() );
        assertFalse( ne.hasMore() );
        
        // Remove alias and entry
        containerCtx.destroySubcontext( rdnAlias );
        containerCtx.destroySubcontext( entryRdn );

        // Remove container
        ctx.destroySubcontext( containerRdn );
    }
}
