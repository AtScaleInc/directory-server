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


import java.util.Arrays;
import java.util.Hashtable;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.Attribute;
import javax.naming.directory.AttributeInUseException;
import javax.naming.directory.AttributeModificationException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.NoSuchAttributeException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Testcase with different modify operations on a person entry. Each includes a
 * single add op only. Created to demonstrate DIREVE-241 ("Adding an already
 * existing attribute value with a modify operation does not cause an error.").
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ModifyAddTest extends AbstractServerTest
{
    private LdapContext ctx = null;
    private static final String RDN_TORI_AMOS = "cn=Tori Amos";
    private static final String PERSON_DESCRIPTION = "an American singer-songwriter";
    private static final String RDN_DEBBIE_HARRY = "cn=Debbie Harry";


    /**
     * Creation of required attributes of a person entry.
     */
    private Attributes getPersonAttributes( String sn, String cn )
    {
        Attributes attributes = new AttributesImpl();
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "person" );
        attribute.add( "organizationalperson" );
        attribute.add( "inetorgperson" );
        attributes.put( attribute );
        attributes.put( "cn", cn );
        attributes.put( "sn", sn );

        return attributes;
    }


    /**
     * Create context and a person entry.
     */
    @Before
    protected void setUp() throws Exception
    {
        super.setUp();

        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + port + "/ou=system" );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );

        ctx = new InitialLdapContext( env, null );
        assertNotNull( ctx );

        // Create Tori Amos with description
        Attributes attributes = this.getPersonAttributes( "Amos", "Tori Amos" );
        attributes.put( "description", "an American singer-songwriter" );
        ctx.createSubcontext( RDN_TORI_AMOS, attributes );

        // Create Debbie Harry ( I feel like being God when creating people as good looking as Blondie :)
        attributes = getPersonAttributes( "Bush", "Kate Bush" );
        ctx.createSubcontext( RDN_DEBBIE_HARRY, attributes );

    }


    /**
     * Remove person entry and close context.
     */
    @After
    protected void tearDown() throws Exception
    {
        ctx.unbind( RDN_TORI_AMOS );
        ctx.destroySubcontext( RDN_DEBBIE_HARRY );
        ctx.close();
        ctx = null;
        super.tearDown();
    }


    /**
     * Add a new attribute to a person entry.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddNewAttributeValue() throws NamingException
    {
        // Add telephoneNumber attribute
        String newValue = "1234567890";
        Attributes attrs = new AttributesImpl( "telephoneNumber", newValue );
        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );

        // Verify, that attribute value is added
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        Attribute attr = attrs.get( "telephoneNumber" );
        assertNotNull( attr );
        assertTrue( attr.contains( newValue ) );
        assertEquals( 1, attr.size() );
    }


    /**
     * Add a new attribute with two values.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddNewAttributeValues() throws NamingException
    {
        // Add telephoneNumber attribute
        String[] newValues =
            { "1234567890", "999999999" };
        Attribute attr = new AttributeImpl( "telephoneNumber" );
        attr.add( newValues[0] );
        attr.add( newValues[1] );
        Attributes attrs = new AttributesImpl();
        attrs.put( attr );
        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );

        // Verify, that attribute values are present
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        attr = attrs.get( "telephoneNumber" );
        assertNotNull( attr );
        assertTrue( attr.contains( newValues[0] ) );
        assertTrue( attr.contains( newValues[1] ) );
        assertEquals( newValues.length, attr.size() );
    }


    /**
     * Add an additional value.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddAdditionalAttributeValue() throws NamingException
    {
        // A new description attribute value
        String newValue = "A new description for this person";
        assertFalse( newValue.equals( PERSON_DESCRIPTION ) );
        Attributes attrs = new AttributesImpl( "description", newValue );

        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );

        // Verify, that attribute value is added
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        Attribute attr = attrs.get( "description" );
        assertNotNull( attr );
        assertTrue( attr.contains( newValue ) );
        assertTrue( attr.contains( PERSON_DESCRIPTION ) );
        assertEquals( 2, attr.size() );
    }


    /**
     * Try to add an already existing attribute value.
     * 
     * Expected behaviour: Modify operation fails with an
     * AttributeInUseException. Original LDAP Error code: 20 (Indicates that the
     * attribute value specified in a modify or add operation already exists as
     * a value for that attribute).
     * 
     * @throws NamingException
     */
    @Test
    public void testAddExistingAttributeValue() throws NamingException
    {
        // Change description attribute
        Attributes attrs = new AttributesImpl( "description", PERSON_DESCRIPTION );
        
        try
        {
            ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );
            fail( "Adding an already existing atribute value should fail." );
        }
        catch ( AttributeInUseException e )
        {
            // expected behaviour
        }

        // Verify, that attribute is still there, and is the only one
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        Attribute attr = attrs.get( "description" );
        assertNotNull( attr );
        assertTrue( attr.contains( PERSON_DESCRIPTION ) );
        assertEquals( 1, attr.size() );
    }

    /**
     * Try to add an already existing attribute value.
     * 
     * Expected behaviour: Modify operation fails with an
     * AttributeInUseException. Original LDAP Error code: 20 (Indicates that the
     * attribute value specified in a modify or add operation already exists as
     * a value for that attribute).
     * 
     * Check for bug DIR_SERVER664
     * 
     * @throws NamingException
     */
    @Test
    public void testAddExistingNthAttributesDirServer664() throws NamingException
    {
        // Change description attribute
        Attributes attrs = new AttributesImpl( true );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 1" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 2" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 3" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 4" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 5" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 6" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 7" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 8" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 9" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 10" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 11" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 12" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 13" ) );
        attrs.put( new AttributeImpl( "telephoneNumber", "attr 14" ) );
        
        Attribute attr = new AttributeImpl( "description", PERSON_DESCRIPTION );

        attrs.put( attr );
        
        try
        {
            ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );
            fail( "Adding an already existing atribute value should fail." );
        }
        catch ( AttributeInUseException e )
        {
            // expected behaviour
        }

        // Verify, that attribute is still there, and is the only one
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        attr = attrs.get( "description" );
        assertNotNull( attr );
        assertTrue( attr.contains( PERSON_DESCRIPTION ) );
        assertEquals( 1, attr.size() );
    }

    /**
     * Check for DIR_SERVER_643
     * 
     * @throws NamingException
     */
    @Test
    public void testTwoDescriptionDirServer643() throws NamingException
    {
        // Change description attribute
        Attributes attrs = new AttributesImpl( true );
        Attribute attr = new AttributeImpl( "description", "a British singer-songwriter with an expressive four-octave voice" );
        attr.add( "one of the most influential female artists of the twentieth century" );
        attrs.put( attr );
        
        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );

        // Verify, that attribute is still there, and is the only one
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        attr = attrs.get( "description" );
        assertNotNull( attr );
        assertEquals( 3, attr.size() );
        assertTrue( attr.contains( "a British singer-songwriter with an expressive four-octave voice" ) );
        assertTrue( attr.contains( "one of the most influential female artists of the twentieth century" ) );
        assertTrue( attr.contains( PERSON_DESCRIPTION ) );
    }

    /**
     * Try to add a duplicate attribute value to an entry, where this attribute
     * is already present (objectclass in this case). Expected behaviour is that
     * the modify operation causes an error (error code 20, "Attribute or value
     * exists").
     * 
     * @throws NamingException
     */
    @Test
    public void testAddDuplicateValueToExistingAttribute() throws NamingException
    {
        // modify object classes, add a new value twice
        Attribute ocls = new AttributeImpl( "objectClass", "organizationalPerson" );
        ModificationItemImpl[] modItems = new ModificationItemImpl[2];
        modItems[0] = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, ocls );
        modItems[1] = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, ocls );
        try
        {
            ctx.modifyAttributes( RDN_TORI_AMOS, modItems );
            fail( "Adding a duplicate attribute value should cause an error." );
        }
        catch ( AttributeInUseException ex )
        {
        }

        // Check, whether attribute objectClass is unchanged
        Attributes attrs = ctx.getAttributes( RDN_TORI_AMOS );
        ocls = attrs.get( "objectClass" );
        assertEquals( ocls.size(), 4 );
        assertTrue( ocls.contains( "top" ) );
        assertTrue( ocls.contains( "person" ) );
    }


    /**
     * Try to add a duplicate attribute value to an entry, where this attribute
     * is not present. Expected behaviour is that the modify operation causes an
     * error (error code 20, "Attribute or value exists").
     * 
     * @throws NamingException
     */
    @Test
    public void testAddDuplicateValueToNewAttribute() throws NamingException
    {
        // add the same description value twice
        Attribute desc = new AttributeImpl( "description", "another description value besides songwriter" );
        ModificationItemImpl[] modItems = new ModificationItemImpl[2];
        modItems[0] = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, desc );
        modItems[1] = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, desc );
        try
        {
            ctx.modifyAttributes( RDN_TORI_AMOS, modItems );
            fail( "Adding a duplicate attribute value should cause an error." );
        }
        catch ( AttributeInUseException ex )
        {
        }

        // Check, whether attribute description is still not present
        Attributes attrs = ctx.getAttributes( RDN_TORI_AMOS );
        assertEquals( 1, attrs.get( "description" ).size() );
    }


    /**
     * Create an entry with a bad attribute : this should fail.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddUnexistingAttribute() throws NamingException
    {
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + port + "/ou=system" );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );

        ctx = new InitialLdapContext( env, null );
        assertNotNull( ctx );

        // Create a third person with a voice attribute
        Attributes attributes = this.getPersonAttributes( "Jackson", "Michael Jackson" );
        attributes.put( "voice", "He is bad ..." );

        try
        {
            ctx.createSubcontext( "cn=Mickael Jackson", attributes );
        }
        catch ( InvalidAttributeIdentifierException iaie )
        {
            assertTrue( true );
            return;
        }

        fail( "Should never reach this point" );
    }


    /**
     * Modify the entry with a bad attribute : this should fail 
     * 
     * @throws NamingException
     */
    @Test
    public void testSearchBadAttribute() throws NamingException
    {
        // Add a not existing attribute
        String newValue = "unbelievable";
        Attributes attrs = new AttributesImpl( "voice", newValue );

        try
        {
            ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );
        }
        catch ( NoSuchAttributeException nsae )
        {
            // We have a failure : the attribute is unknown in the schema
            assertTrue( true );
            return;
        }

        fail( "Cannot reach this point" );
    }
    
    
    /**
     * Create a person entry and perform a modify op, in which
     * we modify an attribute two times.
     * 
     * @throws NamingException 
     */
    @Test
    public void testAttributeValueMultiMofificationDIRSERVER_636() throws NamingException {

        // Create a person entry
        Attributes attrs = getPersonAttributes("Bush", "Kate Bush");
        String rdn = "cn=Kate Bush";
        ctx.createSubcontext(rdn, attrs);

        // Add a description with two values
        String[] descriptions = {
                "Kate Bush is a British singer-songwriter.",
                "She has become one of the most influential female artists of the twentieth century." };
        Attribute desc1 = new AttributeImpl("description");
        desc1.add(descriptions[0]);
        desc1.add(descriptions[1]);

        ModificationItemImpl addModOp = new ModificationItemImpl(
                DirContext.ADD_ATTRIBUTE, desc1);

        Attribute desc2 = new AttributeImpl("description");
        desc2.add(descriptions[1]);
        ModificationItemImpl delModOp = new ModificationItemImpl(
                DirContext.REMOVE_ATTRIBUTE, desc2);

        ctx.modifyAttributes(rdn, new ModificationItemImpl[] { addModOp,
                        delModOp });

        SearchControls sctls = new SearchControls();
        sctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String filter = "(cn=*Bush)";
        String base = "";

        // Check entry
        NamingEnumeration<SearchResult> enm = ctx.search(base, filter, sctls);
        assertTrue(enm.hasMore());
        
        while (enm.hasMore()) {
            SearchResult sr = enm.next();
            attrs = sr.getAttributes();
            Attribute desc = sr.getAttributes().get("description");
            assertNotNull(desc);
            assertEquals(1, desc.size());
            assertTrue(desc.contains(descriptions[0]));
        }

        // Remove the person entry
        ctx.destroySubcontext(rdn);
    }

    /**
     * Try to add subschemaSubentry attribute to an entry
     * 
     * @throws NamingException 
     */
    @Test
    public void testModifyOperationalAttributeAdd() throws NamingException
    {
        ModificationItem modifyOp = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, new BasicAttribute(
            "subschemaSubentry", "cn=anotherSchema" ) );

        try
        {
            ctx.modifyAttributes( RDN_DEBBIE_HARRY, new ModificationItem[]
                { modifyOp } );

            fail( "modification of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            // Expected result
        }
        catch ( NoPermissionException e )
        {
            // Expected result
        }
    }


    /**
     * Create a person entry and perform a modify op on an
     * attribute which is part of the DN. This is not allowed.
     * 
     * A JIRA has been created for this bug : DIRSERVER_687
     * 
     * @throws NamingException 
     */
    @Test
     public void testDNAttributeMemberMofificationDIRSERVER_687() throws NamingException {

        // Create a person entry
        Attributes attrs = getPersonAttributes("Bush", "Kate Bush");
        String rdn = "cn=Kate Bush";
        ctx.createSubcontext(rdn, attrs);

        // Try to modify the cn attribute
        Attribute desc1 = new AttributeImpl( "cn", "Georges Bush" );

        ModificationItem addModOp = new ModificationItem(
                DirContext.REPLACE_ATTRIBUTE, desc1);

        try
        {
            ctx.modifyAttributes( rdn, new ModificationItem[] { addModOp } );
            fail();
        }
        catch ( AttributeModificationException ame )
        {
            assertTrue( true );
            // Remove the person entry
            ctx.destroySubcontext(rdn);
        }
        catch ( NamingException ne ) 
        {
            assertTrue( true );
            // Remove the person entry
            ctx.destroySubcontext(rdn);
        }
    }
    
    /**
     * Try to modify an entry adding invalid number of values for a single-valued atribute
     * 
     * @throws NamingException 
     * @see <a href="http://issues.apache.org/jira/browse/DIRSERVER-614">DIRSERVER-614</a>
     */
    @Test
    public void testModifyAddWithInvalidNumberOfAttributeValues() throws NamingException
    {
        Attributes attrs = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "inetOrgPerson" );
        attrs.put( ocls );
        attrs.put( "cn", "Fiona Apple" );
        attrs.put( "sn", "Apple" );
        ctx.createSubcontext( "cn=Fiona Apple", attrs );
        
        // add two displayNames to an inetOrgPerson
        attrs = new AttributesImpl();
        Attribute displayName = new AttributeImpl( "displayName" );
        displayName.add( "Fiona" );
        displayName.add( "Fiona A." );
        attrs.put( displayName );
        
        try
        {
            ctx.modifyAttributes( "cn=Fiona Apple", DirContext.ADD_ATTRIBUTE, attrs );
            fail( "modification of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            
        }
    }


    /**
     * Add a new attribute to a person entry.
     * 
     * @throws NamingException
     */
    @Test
    public void testAddNewBinaryAttributeValue() throws NamingException
    {
        // Add a binary attribute
        byte[] newValue = new byte[]{0x00, 0x01, 0x02, 0x03};
        Attributes attrs = new AttributesImpl( "userCertificate;binary", newValue );
        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );

        // Verify, that attribute value is added
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        Attribute attr = attrs.get( "userCertificate" );
        assertNotNull( attr );
        assertTrue( attr.contains( newValue ) );
        byte[] certificate = (byte[])attr.get();
        assertTrue( Arrays.equals( newValue, certificate ) );
        assertEquals( 1, attr.size() );
    }
    
    
    /**
     * Add a new ;binary attribute with bytes greater than 0x80
     * to a person entry.
     * Test for DIRSERVER-1146
     *
     * @throws NamingException
     */
    @Test
    public void testAddNewBinaryAttributeValue0x80() throws NamingException
    {
        // Add a ;binary attribute with high-bytes
        byte[] newValue = new byte[]{(byte)0x80, (byte)0x81, (byte)0x82, (byte)0x83};
        Attributes attrs = new AttributesImpl( "userCertificate;binary", newValue );
        ctx.modifyAttributes( RDN_TORI_AMOS, DirContext.ADD_ATTRIBUTE, attrs );
        
        // Verify, that attribute value is added
        attrs = ctx.getAttributes( RDN_TORI_AMOS );
        Attribute attr = attrs.get( "userCertificate" );
        assertNotNull( attr );
        assertTrue( attr.contains( newValue ) );
        byte[] certificate = (byte[])attr.get();
        assertTrue( Arrays.equals( newValue, certificate ) );
        assertEquals( 1, attr.size() );
    }
}
