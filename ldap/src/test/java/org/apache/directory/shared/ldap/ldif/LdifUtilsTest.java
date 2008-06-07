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
package org.apache.directory.shared.ldap.ldif;

import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.util.StringTools;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.junit.Test;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Tests the LdifUtils methods
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class LdifUtilsTest
{
    private String testString = "this is a test";
    
    /**
     * Helper method to build a basic entry used by the Modify tests
     */
    private Attributes buildEntry()
    {
        Attributes entry = new AttributesImpl();
        
        Attribute oc = new AttributeImpl( "objectclass" );
        oc.add( "top" );
        oc.add( "person" );
        entry.put( oc );
        
        entry.put( "cn", "test" );
        entry.put( "sn", "joe doe" );
        entry.put( "l", "USA" );
        
        return entry;
    }

    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char NUL (ASCII code 0)
     */
    @Test
    public void testIsLdifSafeStartingWithNUL()
    {
        char c = ( char ) 0;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char LF (ASCII code 10)
     */
    @Test
    public void testIsLdifSafeStartingWithLF()
    {
        char c = ( char ) 10;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }

    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char CR (ASCII code 13)
     */
    @Test
    public void testIsLdifSafeStartingWithCR()
    {
        char c = ( char ) 13;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }

    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char SPACE (ASCII code 32)
     */
    @Test
    public void testIsLdifSafeStartingWithSpace()
    {
        char c = ( char ) 32;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char COLON (:) (ASCII code 58)
     */
    @Test
    public void testIsLdifSafeStartingWithColon()
    {
        char c = ( char ) 58;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char LESS_THAN (<) (ASCII code 60)
     */
    @Test
    public void testIsLdifSafeStartingWithLessThan()
    {
        char c = ( char ) 60;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char with ASCII code 127
     */
    @Test
    public void testIsLdifSafeStartingWithCharGreaterThan127()
    {
        char c = ( char ) 127;
        
        assertTrue( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String starting with the
     * char with ASCII code greater than 127
     */
    @Test
    public void testIsLdifSafeStartingWithCharGreaterThan127Bis()
    {
        char c = ( char ) 222;
        
        assertFalse( LdifUtils.isLDIFSafe( c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String containing the
     * char NUL (ASCII code 0)
     */
    @Test
    public void testIsLdifSafeContainsNUL()
    {
        char c = ( char ) 0;
        
        assertFalse( LdifUtils.isLDIFSafe( testString + c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String containing the
     * char LF (ASCII code 10)
     */
    @Test
    public void testIsLdifSafeContainsLF()
    {
        char c = ( char ) 10;
        
        assertFalse( LdifUtils.isLDIFSafe( testString + c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String containing the
     * char CR (ASCII code 13)
     */
    @Test
    public void testIsLdifSafeContainsCR()
    {
        char c = ( char ) 13;
        
        assertFalse( LdifUtils.isLDIFSafe( testString + c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String containing the
     * char with ASCII code 127
     */
    @Test
    public void testIsLdifSafeContainsCharGreaterThan127()
    {
        char c = ( char ) 127;
        
        assertTrue( LdifUtils.isLDIFSafe( testString + c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String containing a
     * char with ASCII code greater than 127
     */
    @Test
    public void testIsLdifSafeContainsCharGreaterThan127Bis()
    {
        char c = ( char ) 328;
        
        assertFalse( LdifUtils.isLDIFSafe( testString + c + testString ) );
    }
    
    /**
     * Tests the method IsLdifSafe with a String ending with the
     * char SPACE (ASCII code 32)
     */
    @Test
    public void testIsLdifSafeEndingWithSpace()
    {
        char c = ( char ) 32;
        
        assertFalse( LdifUtils.isLDIFSafe( testString  + c) );
    }
    
    /**
     * Tests the method IsLdifSafe with a correct String
     */
    @Test
    public void testIsLdifSafeCorrectString()
    {        
        assertTrue( LdifUtils.isLDIFSafe( testString ) );
    }
    
    
    /**
     * Test the way LDIF lines are stripped to a number of chars
     */
    @Test
    public void testStripLineToNChars()
    {
        String line = "abc";
        
        try
        {
            LdifUtils.stripLineToNChars( line, 1 );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            // This is correct
        }
        
        String res = LdifUtils.stripLineToNChars( line, 2 );
        assertEquals( "ab\n c", res );
        assertEquals( "abc", LdifUtils.stripLineToNChars( line, 3 ) );
    }

    /**
     * Test that the LDIF is stripped to 5 chars per line
     *
     */
    @Test
    public void testStripLineTo5Chars()
    {
        assertEquals( "a", LdifUtils.stripLineToNChars( "a", 5 ) );
        assertEquals( "ab", LdifUtils.stripLineToNChars( "ab", 5 ) );
        assertEquals( "abc", LdifUtils.stripLineToNChars( "abc", 5 ) );
        assertEquals( "abcd", LdifUtils.stripLineToNChars( "abcd", 5 ) );
        assertEquals( "abcde", LdifUtils.stripLineToNChars( "abcde", 5 ) );
        assertEquals( "abcde\n f", LdifUtils.stripLineToNChars( "abcdef", 5 ) );
        assertEquals( "abcde\n fg", LdifUtils.stripLineToNChars( "abcdefg", 5 ) );
        assertEquals( "abcde\n fgh", LdifUtils.stripLineToNChars( "abcdefgh", 5 ) );
        assertEquals( "abcde\n fghi", LdifUtils.stripLineToNChars( "abcdefghi", 5 ) );
        assertEquals( "abcde\n fghi\n j", LdifUtils.stripLineToNChars( "abcdefghij", 5 ) );
        assertEquals( "abcde\n fghi\n jk", LdifUtils.stripLineToNChars( "abcdefghijk", 5 ) );
        assertEquals( "abcde\n fghi\n jkl", LdifUtils.stripLineToNChars( "abcdefghijkl", 5 ) );
        assertEquals( "abcde\n fghi\n jklm", LdifUtils.stripLineToNChars( "abcdefghijklm", 5 ) );
        assertEquals( "abcde\n fghi\n jklm\n n", LdifUtils.stripLineToNChars( "abcdefghijklmn", 5 ) );
        assertEquals( "abcde\n fghi\n jklm\n no", LdifUtils.stripLineToNChars( "abcdefghijklmno", 5 ) );
        assertEquals( "abcde\n fghi\n jklm\n nop", LdifUtils.stripLineToNChars( "abcdefghijklmnop", 5 ) );
    }
    
    
    /**
     * Tests that unsafe characters are encoded using UTF-8 charset. 
     * 
     * @throws NamingException
     */
    @Test
    public void testConvertToLdifEncoding() throws NamingException
    {
        Attributes attributes = new BasicAttributes( "cn", "Saarbr\u00FCcken" );
        String ldif = LdifUtils.convertToLdif( attributes );
        assertEquals( "cn:: U2FhcmJyw7xja2Vu\n", ldif );
    }
    
    
    /**
     * Tests that null values are correctly encoded 
     * 
     * @throws NamingException
     */
    @Test
    public void testConvertToLdifAttrWithNullValues() throws NamingException
    {
        Attributes attributes = new BasicAttributes( "cn", null );
        String ldif = LdifUtils.convertToLdif( attributes );
        assertEquals( "cn:\n", ldif );
    }
    
    
    /**
     * Test a conversion of an entry from a LDIF file
     */
    @Test
    public void testConvertToLdif() throws NamingException
    {
        String expected = 
            "dn:: Y249U2Fhcm\n" +
            " Jyw7xja2VuLCBk\n" +
            " Yz1leGFtcGxlLC\n" +
            " BkYz1jb20=\n" +
            "changeType: Add\n" +
            "sn: test\n" +
            "cn:: U2FhcmJyw7\n xja2Vu\n" +
            "objectClass: to\n p\n" +
            "objectClass: pe\n rson\n" +
            "objectClass: in\n etorgPerson\n\n";
        
        LdifEntry entry = new LdifEntry();
        entry.setDn( "cn=Saarbr\u00FCcken, dc=example, dc=com" );
        entry.setChangeType( ChangeType.Add );
        
        Attribute oc = new BasicAttribute( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "inetorgPerson" );
        
        entry.addAttribute( oc );
        
        entry.addAttribute( "cn", "Saarbr\u00FCcken" );
        entry.addAttribute( "sn", "test" );

        String ldif = LdifUtils.convertToLdif( entry, 15 );
        //Attributes result = LdifUtils.convertFromLdif( ldif );
        //assertEquals( entry, result );
    }
    
    
    /**
     * Test a conversion of an attributes from a LDIF file
     */
    @Test
    public void testConvertAttributesfromLdif() throws NamingException
    {
        String expected = 
            "sn: test\n" +
            "cn: Saarbrucke\n n\n" +
            "objectClass: to\n p\n" +
            "objectClass: pe\n rson\n" +
            "objectClass: in\n etorgPerson\n\n";
        
        Attributes attributes = new BasicAttributes( true );
        
        Attribute oc = new BasicAttribute( "objectclass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "inetorgPerson" );
        
        attributes.put( oc );
        
        attributes.put( "cn", "Saarbrucken" );
        attributes.put( "sn", "test" );

        String ldif = LdifUtils.convertToLdif( attributes, 15 );
        Attributes result = LdifUtils.convertAttributesFromLdif( ldif );
        assertEquals( attributes, result );
    }

    
    /**
     * Test a AddRequest reverse
     *
     * @throws NamingException
     */
    @Test
    public void testReverseAdd() throws NamingException
    {
        LdapDN dn = new LdapDN( "dc=apache, dc=com" );
        LdifEntry reversed = LdifUtils.reverseAdd( dn );
        
        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Delete, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Test a AddRequest reverse where the DN is to be base64 encoded 
     *
     * @throws NamingException
     */
    @Test
    public void testReverseAddBase64DN() throws NamingException
    {
        LdapDN dn = new LdapDN( "dc=Emmanuel L\u00c9charny" );
        LdifEntry reversed = LdifUtils.reverseAdd( dn );
        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Delete, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Test a DelRequest reverse
     *
     * @throws NamingException
     */
    @Test
    public void testReverseDel() throws NamingException
    {
        LdapDN dn = new LdapDN( "dc=apache, dc=com" );
        
        Attributes deletedEntry = new AttributesImpl();
        
        Attribute oc = new AttributeImpl( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        
        deletedEntry.put( oc );
        
        deletedEntry.put( "cn", "test" );
        deletedEntry.put( "sn", "apache" );
        deletedEntry.put( "dc", "apache" );
        
        LdifEntry reversed = LdifUtils.reverseDel( dn, deletedEntry );
        
        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Add, reversed.getChangeType() );
        assertNotNull( reversed.getAttributes() );
        assertEquals( deletedEntry, reversed.getAttributes() );
    }
    
    
    /**
     * Check that the correct reverse LDIF is produced for a modifyDn
     * operation that just renames the person without preserving the
     * old rdn.
     *
     * @throws NamingException on error
     */
    @Test
    public void testReverseModifyDNDeleteOldRdnNoSuperior() throws NamingException
    {
        LdapDN dn = new LdapDN( "cn=john doe, dc=example, dc=com" );

        AttributesImpl attrs = new AttributesImpl( "objectClass", "person", true );
        attrs.get( "objectClass" ).add( "uidObject" );
        attrs.put( "cn", "john doe" );
        attrs.put( "sn", "doe" );
        attrs.put( "uid", "jdoe" );

        LdifEntry reversed = LdifUtils.reverseModifyRdn( attrs, null, dn, new Rdn( "cn=jack doe" ) );

        assertNotNull( reversed );
        assertEquals( "cn=jack doe, dc=example, dc=com", reversed.getDn() );
        assertEquals( ChangeType.ModRdn, reversed.getChangeType() );
        assertTrue( reversed.isDeleteOldRdn() );
        assertEquals( "cn=john doe", reversed.getNewRdn() );
        assertNull( reversed.getNewSuperior() );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Check that the correct reverse LDIF is produced for a modifyDn
     * operation that just renames the person while preserving the
     * old rdn.
     *
     * @throws NamingException on error
     */
    @Test
    public void testReverseModifyDNNoSuperior() throws NamingException
    {
        LdapDN dn = new LdapDN( "cn=john doe, dc=example, dc=com" );

        AttributesImpl attrs = new AttributesImpl( "objectClass", "person", true );
        attrs.get( "objectClass" ).add( "uidObject" );
        attrs.put( "cn", "john doe" );
        attrs.put( "cn", "jack doe" );
        attrs.put( "sn", "doe" );
        attrs.put( "uid", "jdoe" );

        LdifEntry reversed = LdifUtils.reverseModifyRdn( attrs, null, dn, new Rdn( "cn=jack doe" ) );

        assertNotNull( reversed );
        assertEquals( "cn=jack doe, dc=example, dc=com", reversed.getDn() );
        assertEquals( ChangeType.ModRdn, reversed.getChangeType() );
        assertFalse( reversed.isDeleteOldRdn() );
        assertEquals( "cn=john doe", reversed.getNewRdn() );
        assertNull( reversed.getNewSuperior() );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Check that the correct reverse LDIF is produced for a modifyDn
     * operation that moves and renames the entry while preserving the
     * old rdn.
     *
     * @throws NamingException on error
     */
    @Test
    public void testReverseModifyDNSuperior() throws NamingException
    {
        LdapDN dn = new LdapDN( "cn=john doe, dc=example, dc=com" );
        LdapDN newSuperior = new LdapDN( "ou=system" );

        AttributesImpl attrs = new AttributesImpl( "objectClass", "person", true );
        attrs.get( "objectClass" ).add( "uidObject" );
        attrs.put( "cn", "john doe" );
        attrs.put( "cn", "jack doe" );
        attrs.put( "sn", "doe" );
        attrs.put( "uid", "jdoe" );

        LdifEntry reversed = LdifUtils.reverseModifyRdn( attrs, newSuperior, dn, new Rdn( "cn=jack doe" ) );

        assertNotNull( reversed );
        assertEquals( "cn=jack doe,ou=system", reversed.getDn() );
        assertEquals( ChangeType.ModRdn, reversed.getChangeType() );
        assertFalse( reversed.isDeleteOldRdn() );
        assertEquals( "cn=john doe", reversed.getNewRdn() );
        assertEquals( "dc=example, dc=com", StringTools.trim( reversed.getNewSuperior() ) );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Test a reversed move ModifyDN no rdn changes
     *
     * @throws NamingException on error
     */
    @Test
    public void testReverseModifyDNMove() throws NamingException
    {
        LdapDN dn = new LdapDN( "cn=john doe, dc=example, dc=com" );
        LdapDN newSuperior = new LdapDN( "ou=system" );

        AttributesImpl attrs = new AttributesImpl( "objectClass", "person", true );
        attrs.get( "objectClass" ).add( "uidObject" );
        attrs.put( "cn", "john doe" );
        attrs.put( "cn", "jack doe" );
        attrs.put( "sn", "doe" );
        attrs.put( "uid", "jdoe" );

        LdifEntry reversed = LdifUtils.reverseModifyRdn( attrs, newSuperior, dn, null );

        assertNotNull( reversed );
        assertEquals( "cn=john doe,ou=system", reversed.getDn() );
        assertEquals( ChangeType.ModDn, reversed.getChangeType() );
        assertFalse( reversed.isDeleteOldRdn() );
        assertNull( reversed.getNewRdn() );
        assertEquals( "dc=example, dc=com", StringTools.trim( reversed.getNewSuperior() ) );
        assertNull( reversed.getAttributes() );
    }


    /**
     * Test a reversed ModifyDN with a deleteOldRdn, rdn change, and a superior
     *
     * @throws NamingException on error
     */
    @Test
    public void testReverseModifyDNDeleteOldRdnSuperior() throws NamingException
    {
        LdapDN dn = new LdapDN( "cn=john doe, dc=example, dc=com" );
        LdapDN newSuperior = new LdapDN( "ou=system" );

        AttributesImpl attrs = new AttributesImpl( "objectClass", "person", true );
        attrs.get( "objectClass" ).add( "uidObject" );
        attrs.put( "cn", "john doe" );
        attrs.put( "sn", "doe" );
        attrs.put( "uid", "jdoe" );

        LdifEntry reversed = LdifUtils.reverseModifyRdn( attrs, newSuperior, dn, new Rdn( "cn=jack doe" ) );

        assertNotNull( reversed );
        assertEquals( "cn=jack doe,ou=system", reversed.getDn() );
        assertEquals( ChangeType.ModRdn, reversed.getChangeType() );
        assertTrue( reversed.isDeleteOldRdn() );
        assertEquals( "cn=john doe", reversed.getNewRdn() );
        assertEquals( "dc=example, dc=com", StringTools.trim( reversed.getNewSuperior() ) );
        assertNull( reversed.getAttributes() );
    }

    
    /**
     * Test a reversed Modify adding a new attribute value
     * in an exiting attribute
     */
    @Test
    public void testReverseModifyAddNewOuValue() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();

        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );
        
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.ADD_ATTRIBUTE, 
            new AttributeImpl( "ou", "BigCompany inc." ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.REMOVE_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        assertEquals( "BigCompany inc.", attr.get() );
    }


    /**
     * Test a reversed Modify adding a new attribute
     */
    @Test
    public void testReverseModifyAddNewOu() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();
        
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.ADD_ATTRIBUTE, 
            new AttributeImpl( "ou", "BigCompany inc." ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );

        assertEquals( DirContext.REMOVE_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        assertEquals( "BigCompany inc.", attr.get() );
    }

   
    /**
     * Test a reversed Modify adding a existing attribute value
     */
    @Test
    @Ignore ( "This is just not a valid test since it leaves us with no reverse LDIF" )
    public void testReverseModifyAddExistingCnValue() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();

        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.ADD_ATTRIBUTE,
            new AttributeImpl( "cn", "test" ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNull( reversed );
    }

    
    /**
     * Test a reversed Modify adding a existing value from an existing attribute
     */
    @Test
    public void testReverseModifyDelExistingOuValue() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();
        
        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );

        LdapDN dn = new LdapDN( "cn=test, ou=system" );

        ModificationItemImpl mod = new ModificationItemImpl( 
            DirContext.REMOVE_ATTRIBUTE, 
            new AttributeImpl( "ou", "acme corp" ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.ADD_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );

        Attribute addedAttr = new AttributeImpl( "ou", "acme corp" );
        assertEquals( addedAttr, attr );
    }


    /**
     * Test a reversed Modify deleting an existing attribute
     */
    @Test
    public void testReverseModifyDeleteOU() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();
        
        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );

        LdapDN dn = new LdapDN( "cn=test, ou=system" );

        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.REMOVE_ATTRIBUTE, 
            new AttributeImpl( "ou" ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );


        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.ADD_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        
        assertEquals( ou, attr );
    }

   
    /**
     * Test a reversed Modify deleting all values of an existing attribute
     */
    @Test
    public void testReverseModifyDelExistingOuWithAllValues() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();

        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );
        
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.REMOVE_ATTRIBUTE, ou );

        LdifEntry reversed = LdifUtils.reverseModify( dn, 
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );


        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.ADD_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        
        assertEquals( ou, attr );
    }

    
    /**
     * Test a reversed Modify replacing existing values with new values
     */
    @Test
    public void testReverseModifyReplaceExistingOuValues() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();
        
        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );

        LdapDN dn = new LdapDN( "cn=test, ou=system" );

        Attribute ouModified = new AttributeImpl( "ou" );
        ouModified.add( "directory" );
        ouModified.add( "BigCompany inc." );
        
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.REPLACE_ATTRIBUTE, ouModified );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );



        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.REPLACE_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( ou, attr );
    }


    /**
     * Test a reversed Modify replace by injecting a new attribute
     */
    @Test
    public void testReverseModifyReplaceNewAttribute() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();
        
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        
        Attribute newOu = new AttributeImpl( "ou" );
        newOu.add( "apache" );
        newOu.add( "acme corp" );

        
        ModificationItemImpl mod = new ModificationItemImpl(
            DirContext.REPLACE_ATTRIBUTE, newOu );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.REPLACE_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        
        assertNull( attr.get() );
    }

   
    /**
     * Test a reversed Modify replace by removing an attribute
     */
    @Test
    public void testReverseModifyReplaceExistingOuWithNothing() throws NamingException
    {
        Attributes modifiedEntry = buildEntry();

        Attribute ou = new AttributeImpl( "ou" );
        ou.add( "apache" );
        ou.add( "acme corp" );
        modifiedEntry.put( ou );
        
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        
        ModificationItemImpl mod = new ModificationItemImpl( 
            DirContext.REPLACE_ATTRIBUTE, new AttributeImpl( "ou" ) );

        LdifEntry reversed = LdifUtils.reverseModify( dn,
                Collections.<ModificationItemImpl>singletonList( mod ), modifiedEntry );

        assertNotNull( reversed );
        assertEquals( dn.getUpName(), reversed.getDn() );
        assertEquals( ChangeType.Modify, reversed.getChangeType() );
        assertNull( reversed.getAttributes() );
        
        List<ModificationItemImpl> mods = reversed.getModificationItems();
        
        assertNotNull( mods );
        assertEquals( 1, mods.size() );
        
        ModificationItemImpl modif = mods.get( 0 );
        
        assertEquals( DirContext.REPLACE_ATTRIBUTE, modif.getModificationOp() );

        Attribute attr = modif.getAttribute();
        
        assertNotNull( attr );
        assertEquals( "ou", attr.getID() );
        
        assertEquals( ou, attr );
    }
    
    
    /**
     * Test a multiple modifications reverse.
     * 
     * On the following entry :
     *  dn: cn=test, ou=system
     *  objectclass: top
     *  objectclass: person
     *  cn: test
     *  sn: joe doe
     *  l: USA
     *  ou: apache
     *  ou: acme corp
     * 
     * We will :
     *  - add an 'ou' value 'BigCompany inc.'
     *  - delete the 'l' attribute
     *  - add the 'l=FR' attribute
     *  - replace the 'l=FR' by a 'l=USA' attribute
     *  - replace the 'ou' attribute with 'apache' value.
     *  
     * The modify ldif will be :
     * 
     *  dn: cn=test, ou=system
     *  changetype: modify
     *  add: ou
     *  ou: BigCompany inc.
     *  -
     *  delete: l
     *  -
     *  add: l
     *  l: FR
     *  -
     *  replace: l
     *  l: USA
     *  -
     *  replace: ou
     *  ou: apache
     *  -
     *  
     * At the end, the entry will looks like :
     *  dn: cn=test, ou=system
     *  objectclass: top
     *  objectclass: person
     *  cn: test
     *  sn: joe doe
     *  l: USA
     *  ou: apache
     *  
     * and the reversed LDIF will be :
     * 
     *  dn: cn=test, ou=system
     *  changetype: modify
     *  replace: ou
     *  ou: apache
     *  ou: acme corp
     *  -
     *  replace: l
     *  l: USA
     *  -
     *  delete: l
     *  l: FR
     *  -
     *  add: l
     *  l: USA
     *  -
     *  delete: ou 
     *  ou: BigCompany inc.
     *  -
     * 
     */
    @Test
    public void testReverseMultipleModifications() throws NamingException
    {
        String initialEntryLdif = 
                "dn: cn=test, ou=system\n" + 
                "objectclass: top\n" + 
                "objectclass: person\n" + 
                "cn: test\n" + 
                "sn: joe doe\n" + 
                "l: USA\n" + 
                "ou: apache\n" + 
                "ou: acme corp\n"; 
        
        LdifReader reader = new LdifReader();
        List<LdifEntry> entries = reader.parseLdif( initialEntryLdif );
        
        LdifEntry initialEntry = entries.get( 0 );
 
        // We will :
        //   - add an 'ou' value 'BigCompany inc.'
        //   - delete the 'l' attribute
        //   - add the 'l=FR' attribute
        //   - replace the 'l=FR' by a 'l=USA' attribute
        //   - replace the 'ou' attribute with 'apache' value.
        LdapDN dn = new LdapDN( "cn=test, ou=system" );
        
        List<ModificationItemImpl> modifications = new ArrayList<ModificationItemImpl>();

        // First, inject the 'ou'
        
        ModificationItemImpl mod = new ModificationItemImpl( 
            DirContext.ADD_ATTRIBUTE, new AttributeImpl( "ou", "BigCompany inc." ) );
        modifications.add( mod );

        // Remove the 'l'
        mod = new ModificationItemImpl(
            DirContext.REMOVE_ATTRIBUTE, new AttributeImpl( "l" ) );
        modifications.add( mod );
        
        // Add 'l=FR'
        mod = new ModificationItemImpl( 
            DirContext.ADD_ATTRIBUTE, new AttributeImpl( "l", "FR" ) );
        modifications.add( mod );

        // Replace it with 'l=USA'
        mod = new ModificationItemImpl( 
            DirContext.REPLACE_ATTRIBUTE, new AttributeImpl( "l", "USA" ) );
        modifications.add( mod );

        // Replace the ou value
        mod = new ModificationItemImpl( 
            DirContext.REPLACE_ATTRIBUTE, new AttributeImpl( "ou", "apache" ) );
        modifications.add( mod );
        
        LdifEntry reversedEntry = LdifUtils.reverseModify( dn, modifications, initialEntry.getAttributes() );

        String expectedEntryLdif = 
            "dn: cn=test, ou=system\n" +
            "changetype: modify\n" +
            "replace: ou\n" +
            "ou: apache\n" +
            "ou: acme corp\n" +
            "-\n" +
            "replace: l\n" +
            "l: USA\n" +
            "-\n" +
            "delete: l\n" +
            "l: FR\n" +
            "-\n" +
            "add: l\n" +
            "l: USA\n" +
            "-\n" +
            "delete: ou\n" + 
            "ou: BigCompany inc.\n" +
            "-\n\n";
    
        reader = new LdifReader();
        entries = reader.parseLdif( expectedEntryLdif );
    
        LdifEntry expectedEntry = entries.get( 0 );
        
        assertEquals( expectedEntry, reversedEntry );
    }
}
