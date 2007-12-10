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
package org.apache.directory.server.core.operational;


import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.unit.AbstractAdminTestCase;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.DerefAliasesEnum;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.junit.Test;


/**
 * Tests the methods on JNDI contexts that are analogous to entry modify
 * operations in LDAP.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class OperationalAttributeServiceITest extends AbstractAdminTestCase
{
    private static final String CREATORS_NAME = "creatorsName";

    private static final String CREATE_TIMESTAMP = "createTimestamp";

    private static final String MODIFIERS_NAME = "modifiersName";

    private static final String MODIFY_TIMESTAMP = "modifyTimestamp";

    private static final String RDN_KATE_BUSH = "cn=Kate Bush";


    protected Attributes getPersonAttributes( String sn, String cn )
    {
        Attributes attrs = new BasicAttributes( true );
        Attribute ocls = new BasicAttribute( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" );
        attrs.put( ocls );
        attrs.put( "cn", cn );
        attrs.put( "sn", sn );

        return attrs;
    }


    protected void setUp() throws NamingException, Exception
    {
        super.setUp();

        // Create an entry for Kate Bush
        Attributes attrs = getPersonAttributes( "Bush", "Kate Bush" );
        DirContext ctx = sysRoot.createSubcontext( RDN_KATE_BUSH, attrs );
        assertNotNull( ctx );
    }


    protected void tearDown() throws NamingException, Exception
    {
        sysRoot.destroySubcontext( RDN_KATE_BUSH );

        super.tearDown();
    }


    public void testModifyOperationalOpAttrs() throws NamingException
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
        assertNull( attributes.get( CREATE_TIMESTAMP ) );
        assertNull( attributes.get( CREATORS_NAME ) );

        SearchControls ctls = new SearchControls();
        ctls.setReturningAttributes( new String[]
            { "ou", "createTimestamp", "creatorsName" } );

        sysRoot.addToEnvironment( JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES,
            DerefAliasesEnum.NEVER_DEREF_ALIASES );
        NamingEnumeration list;
        list = sysRoot.search( "", "(ou=testing00)", ctls );
        SearchResult result = ( SearchResult ) list.next();
        list.close();

        assertNotNull( result.getAttributes().get( "ou" ) );
        assertNotNull( result.getAttributes().get( CREATORS_NAME ) );
        assertNotNull( result.getAttributes().get( CREATE_TIMESTAMP ) );
    }


    /**
     * Checks to confirm that the system context root ou=system has the required
     * operational attributes. Since this is created automatically on system
     * database creation properties the create attributes must be specified.
     * There are no interceptors in effect when this happens so we must test
     * explicitly.
     * 
     * 
     * @see <a href="http://nagoya.apache.org/jira/browse/DIREVE-57">DIREVE-57:
     *      ou=system does not contain operational attributes</a>
     */
    public void testSystemContextRoot() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        NamingEnumeration list;
        list = sysRoot.search( "", "(objectClass=*)", controls );
        SearchResult result = ( SearchResult ) list.next();

        // test to make sure op attribute do not occur - this is the control
        Attributes attributes = result.getAttributes();
        assertNull( attributes.get( "creatorsName" ) );
        assertNull( attributes.get( "createTimestamp" ) );

        // now we ask for all the op attributes and check to get them
        String[] ids = new String[]
            { "creatorsName", "createTimestamp" };
        controls.setReturningAttributes( ids );
        list = sysRoot.search( "", "(objectClass=*)", controls );
        result = ( SearchResult ) list.next();
        attributes = result.getAttributes();
        assertNotNull( attributes.get( "creatorsName" ) );
        assertNotNull( attributes.get( "createTimestamp" ) );
    }


    /**
     * Test which confirms that all new users created under the user's dn
     * (ou=users,ou=system) have the creatorsName set to the DN of the new user
     * even though the admin is creating the user. This is the basis for some
     * authorization rules to protect passwords.
     * 
     * NOTE THIS CHANGE WAS REVERTED SO WE ADAPTED THE TEST TO MAKE SURE THE
     * CHANGE DOES NOT PERSIST!
     * 
     * @see <a href="http://nagoya.apache.org/jira/browse/DIREVE-67">JIRA Issue
     *      DIREVE-67</a>
     */
    @Test
    public void testConfirmNonAdminUserDnIsCreatorsName() throws NamingException
    {

        Attributes attributes = sysRoot.getAttributes( "uid=akarasulu,ou=users", new String[]
            { "creatorsName" } );

        assertFalse( "uid=akarasulu,ou=users,ou=system".equals( attributes.get( "creatorsName" ).get() ) );
    }


    /**
     * Modify an entry and check whether attributes modifiersName and modifyTimestamp are present.
     */
    @Test
    public void testModifyShouldLeadToModifiersAttributes() throws NamingException
    {
        ModificationItem modifyOp = new ModificationItem( DirContext.ADD_ATTRIBUTE, new BasicAttribute( "description",
            "Singer Songwriter" ) );

        sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
            { modifyOp } );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        String[] ids = new String[]
            { MODIFIERS_NAME, MODIFY_TIMESTAMP };
        controls.setReturningAttributes( ids );

        NamingEnumeration list = sysRoot.search( RDN_KATE_BUSH, "(objectClass=*)", controls );
        SearchResult result = ( SearchResult ) list.next();
        Attributes attributes = result.getAttributes();
        assertNotNull( attributes.get( MODIFIERS_NAME ) );
        assertNotNull( attributes.get( MODIFY_TIMESTAMP ) );
    }


    /**
     * Modify an entry and check whether attribute modifyTimestamp changes.
     */
    @Test
    public void testModifyShouldChangeModifyTimestamp() throws NamingException, InterruptedException
    {
        // Add attribute description to entry
        ModificationItem modifyAddOp = new ModificationItem( DirContext.ADD_ATTRIBUTE, new BasicAttribute(
            "description", "an English singer, songwriter, musician" ) );
        sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
            { modifyAddOp } );

        // Determine modifyTimestamp
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        String[] ids = new String[]
            { MODIFY_TIMESTAMP };
        controls.setReturningAttributes( ids );
        NamingEnumeration list = sysRoot.search( RDN_KATE_BUSH, "(objectClass=*)", controls );
        SearchResult result = ( SearchResult ) list.next();
        Attributes attributes = result.getAttributes();
        Attribute modifyTimestamp = attributes.get( MODIFY_TIMESTAMP );
        assertNotNull( modifyTimestamp );
        String oldTimestamp = modifyTimestamp.get().toString();
        
        // Wait two seconds
        Thread.sleep( 2000 );

        // Change value of attribute description
        ModificationItem modifyOp = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
            "description", "one of England's most successful solo female performers" ) );
        sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
            { modifyOp } );

        // Determine modifyTimestamp after mofification
        list = sysRoot.search( RDN_KATE_BUSH, "(objectClass=*)", controls );
        result = ( SearchResult ) list.next();
        attributes = result.getAttributes();
        modifyTimestamp = attributes.get( MODIFY_TIMESTAMP );
        assertNotNull( modifyTimestamp );
        String newTimestamp = modifyTimestamp.get().toString();
        
        // assert the value has changed
        assertFalse( oldTimestamp.equals( newTimestamp ) );
    }


    /**
     * Try to add modifiersName attribute to an entry
     */
    @Test
    public void testModifyOperationalAttributeAdd() throws NamingException
    {
        ModificationItem modifyOp = new ModificationItem( DirContext.ADD_ATTRIBUTE, new BasicAttribute(
            "modifiersName", "cn=Tori Amos,dc=example,dc=com" ) );

        try
        {
            sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
                { modifyOp } );
            fail( "modification of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            // expected
        }
        catch ( NoPermissionException e )
        {
            // expected
        }
    }


    /**
     * Try to remove creatorsName attribute from an entry.
     */
    @Test
    public void testModifyOperationalAttributeRemove() throws NamingException
    {
        ModificationItem modifyOp = new ModificationItem( DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(
            "creatorsName" ) );

        try
        {
            sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
                { modifyOp } );
            fail( "modification of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            // expected
        }
        catch ( NoPermissionException e )
        {
            // expected
        }
    }


    /**
     * Try to replace creatorsName attribute on an entry.
     */
    @Test(expected=NoPermissionException.class)
    public void testModifyOperationalAttributeReplace() throws NamingException
    {
        ModificationItem modifyOp = new ModificationItemImpl( DirContext.REPLACE_ATTRIBUTE, new AttributeImpl(
            "creatorsName", "cn=Tori Amos,dc=example,dc=com" ) );

        try
        {
            sysRoot.modifyAttributes( RDN_KATE_BUSH, new ModificationItem[]
                { modifyOp } );
            fail( "modification of entry should fail" );
        }
        catch ( InvalidAttributeValueException e )
        {
            // expected
        }
        catch ( NoPermissionException e )
        {
            // expected
        }
    }
}
