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
package org.apache.directory.server.core.exception;


import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapContextNotEmptyException;
import org.apache.directory.shared.ldap.exception.LdapNameAlreadyBoundException;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the correct operation of the ServerExceptionService.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( FrameworkRunner.class )
public class ExceptionServiceIT extends AbstractLdapTestUnit
{
    private DirContext createSubContext( String type, String value ) throws Exception
    {
        return createSubContext( getSystemContext( service ), type, value );
    }


    private DirContext createSubContext( DirContext ctx, String type, String value ) throws NamingException
    {
        Attributes subentry = AttributeUtils.createAttributes( 
            "objectClass: top",
            "objectClass: person",
            "objectClass: OrganizationalPerson",
            "sn", value,
            "cn", value );

        return ctx.createSubcontext( type + "=" + value, subentry );
    }


    // ------------------------------------------------------------------------
    // Search Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Test search operation failure when the search base is non-existant.
     *
     * @throws NamingException on error
     */
    @Test
    public void testFailSearchNoSuchObject() throws Exception
    {
        SearchControls ctls = new SearchControls();
        try
        {
            getSystemContext( service ).search( "ou=blah", "(objectClass=*)", ctls );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Search operation control to test if normal search operations occur
     * correctly.
     *
     * @throws NamingException on error
     */
    @Test
    public void testSearchControl() throws Exception
    {
        SearchControls ctls = new SearchControls();
        NamingEnumeration<SearchResult> list = getSystemContext( service ).search( "ou=users", "(objectClass=*)", ctls );

        if ( list.hasMore() )
        {
            SearchResult result = list.next();
            assertNotNull( result.getAttributes() );
            assertEquals( "uid=akarasulu,ou=users,ou=system", result.getName() );
        }

        assertFalse( list.hasMore() );
    }


    // ------------------------------------------------------------------------
    // Move Operation Tests
    // ------------------------------------------------------------------------

    /**
     * Test move operation failure when the object moved is non-existant.
     *
     * @throws NamingException on error
     */
    @Test
    public void testFailMoveEntryAlreadyExists() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            Attributes attrs = new BasicAttributes( "ou", "users", true );
            Attribute attr = new BasicAttribute( "ObjectClass" );
            attr.add( "top"  );
            attr.add( "OrganizationalUnit" );
            attrs.put( attr );

            sysRoot.createSubcontext( "ou=users,ou=groups", attrs );
            sysRoot.rename( "ou=users", "ou=users,ou=groups" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameAlreadyBoundException e )
        {
            assertEquals( "ou=users,ou=groups,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.ENTRY_ALREADY_EXISTS, e.getResultCode() );
        }

        try
        {
            Attributes attrs = new BasicAttributes( "ou", "uzerz", true );
            Attribute attr = new BasicAttribute( "ObjectClass" );
            attr.add( "top"  );
            attr.add( "OrganizationalUnit" );
            attrs.put( attr );

            sysRoot.createSubcontext( "ou=uzerz,ou=groups", attrs );
            sysRoot.addToEnvironment( "java.naming.ldap.deleteRDN", "false" );
            sysRoot.rename( "ou=users", "ou=uzerz,ou=groups" );
            sysRoot.removeFromEnvironment( "java.naming.ldap.deleteRDN" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameAlreadyBoundException e )
        {
            assertEquals( "ou=uzerz,ou=groups,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.ENTRY_ALREADY_EXISTS, e.getResultCode() );
        }
    }


    /**
     * Test move operation failure when the object moved is non-existant.

     * @throws NamingException on error
     */
    @Test
    public void testFailMoveNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.rename( "ou=blah", "ou=blah,ou=groups" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }

        try
        {
            sysRoot.addToEnvironment( "java.naming.ldap.deleteRDN", "false" );
            sysRoot.rename( "ou=blah", "ou=blah2,ou=groups" );
            sysRoot.removeFromEnvironment( "java.naming.ldap.deleteRDN" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Move operation control to test if normal move operations occur
     * correctly.
     *
     * @throws NamingException on error
     */
    @Test
    public void testMoveControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        sysRoot.rename( "ou=users", "ou=users,ou=groups" );
        assertNotNull( sysRoot.lookup( "ou=users,ou=groups" ) );

        try
        {
            sysRoot.lookup( "ou=users" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( NamingException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertTrue( e instanceof LdapNameNotFoundException );
        }
    }


    // ------------------------------------------------------------------------
    // ModifyRdn Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Test modifyRdn operation failure when the object renamed is non-existant.
     *
     * @throws NamingException on error
     */
    @Test
    public void testFailModifyRdnEntryAlreadyExists() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.rename( "ou=users", "ou=groups" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameAlreadyBoundException e )
        {
            assertEquals( "ou=groups,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.ENTRY_ALREADY_EXISTS, e.getResultCode() );
        }
    }


    /**
     * Test modifyRdn operation failure when the object renamed is non-existant.
     *
     * @throws NamingException on error
     */
    @Test
    public void testFailModifyRdnNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.rename( "ou=blah", "ou=asdf" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=blah,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Modify operation control to test if normal modify operations occur
     * correctly.
     *
     * @throws Exception on error
     */
    @Test
    public void testModifyRdnControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        sysRoot.rename( "ou=users", "ou=asdf" );
        assertNotNull( sysRoot.lookup( "ou=asdf" ) );

        try
        {
            sysRoot.lookup( "ou=users" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( NamingException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertTrue( e instanceof LdapNameNotFoundException );
        }
    }


    // ------------------------------------------------------------------------
    // Modify Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Test modify operation failure when the object modified is non-existant.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailModifyNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        Attributes attrs = new BasicAttributes( true );
        Attribute ou = new BasicAttribute( "ou" );
        ou.add( "users" );
        ou.add( "dummyValue" );
        attrs.put( ou );

        try
        {
            sysRoot.modifyAttributes( "ou=blah", DirContext.ADD_ATTRIBUTE, attrs );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }

        ModificationItem[] mods = new ModificationItem[]
            { new ModificationItem( DirContext.ADD_ATTRIBUTE, ou ) };

        try
        {
            sysRoot.modifyAttributes( "ou=blah", mods );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Modify operation control to test if normal modify operations occur
     * correctly.
     *
     * @throws Exception on error
     */
    @Test
    public void testModifyControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        Attributes attrs = new BasicAttributes( true );
        Attribute attr = new BasicAttribute( "ou" );
        attr.add( "dummyValue" );
        attrs.put( attr );
        sysRoot.modifyAttributes( "ou=users", DirContext.ADD_ATTRIBUTE, attrs );
        Attribute ou = sysRoot.getAttributes( "ou=users" ).get( "ou" );
        assertTrue( ou.contains( "users" ) );
        assertTrue( ou.contains( "dummyValue" ) );

        attr = new BasicAttribute( "ou" );
        attr.add( "another" );
        ModificationItem[] mods = new ModificationItem[]
            { new ModificationItem( DirContext.ADD_ATTRIBUTE, attr ) };

        sysRoot.modifyAttributes( "ou=users", mods );
        ou = sysRoot.getAttributes( "ou=users" ).get( "ou" );
        assertTrue( ou.contains( "users" ) );
        assertTrue( ou.contains( "dummyValue" ) );
        assertTrue( ou.contains( "another" ) );
    }


    // ------------------------------------------------------------------------
    // Lookup Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Test lookup operation failure when the object looked up is non-existant.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailLookupNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.lookup( "ou=blah" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Lookup operation control to test if normal lookup operations occur
     * correctly.
     *
     * @throws Exception on error
     */
    @Test
    public void testLookupControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        LdapContext ctx = ( LdapContext ) sysRoot.lookup( "ou=users" );
        assertNotNull( ctx );
        assertEquals( "users", ctx.getAttributes( "" ).get( "ou" ).get() );
    }


    // ------------------------------------------------------------------------
    // List Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Test list operation failure when the base searched is non-existant.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailListNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.list( "ou=blah" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * List operation control to test if normal list operations occur correctly.
     *
     * @throws Exception on error
     */
    @Test
    public void testListControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        NamingEnumeration<?> list = sysRoot.list( "ou=users" );

        if ( list.hasMore() )
        {
            SearchResult result = (SearchResult)list.next();
            assertNotNull( result.getAttributes() );
            assertEquals( "uid=akarasulu,ou=users,ou=system", result.getName() );
        }

        assertFalse( list.hasMore() );
    }


    // ------------------------------------------------------------------------
    // Add Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Tests for add operation failure when the parent of the entry to add does
     * not exist.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailAddOnAlias() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        Attributes attrs = new BasicAttributes( true );
        Attribute attr = new BasicAttribute( "objectClass" );
        attr.add( "top" );
        attr.add( "alias" );
        attr.add( SchemaConstants.EXTENSIBLE_OBJECT_OC );
        attrs.put( attr );
        attrs.put( "aliasedObjectName", "ou=users,ou=system" );

        sysRoot.createSubcontext( "cn=toanother", attrs );

        try
        {
            sysRoot.createSubcontext( "ou=blah,cn=toanother" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.OBJECT_CLASS_VIOLATION, e.getResultCode() );
        }
    }


    /**
     * Tests for add operation failure when the parent of the entry to add does
     * not exist.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailAddNoSuchEntry() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.createSubcontext( "ou=blah,ou=abc" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.OBJECT_CLASS_VIOLATION, e.getResultCode() );
        }
    }


    /**
     * Tests for add operation failure when the entry to add already exists.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailAddEntryAlreadyExists() throws Exception
    {
        createSubContext( "ou", "blah");

        try
        {
            createSubContext( "ou", "blah");
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameAlreadyBoundException e )
        {
            assertEquals( "ou=blah,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.ENTRY_ALREADY_EXISTS, e.getResultCode() );
        }
    }


    /**
     * Add operation control to test if normal add operations occur correctly.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        DirContext ctx = createSubContext( "ou", "blah");
        createSubContext( ctx, "ou", "subctx");
        Object obj = sysRoot.lookup( "ou=subctx,ou=blah" );
        assertNotNull( obj );
    }


    // ------------------------------------------------------------------------
    // Delete Operation Tests
    // ------------------------------------------------------------------------


    /**
     * Tests for delete failure when the entry to be deleted has child entires.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailDeleteNotAllowedOnNonLeaf() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        DirContext ctx = createSubContext( "ou", "blah" );
        createSubContext( ctx,  "ou", "subctx" );

        try
        {
            sysRoot.destroySubcontext( "ou=blah" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapContextNotEmptyException e )
        {
            assertEquals( "ou=blah,ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_NON_LEAF, e.getResultCode() );
        }
    }


    /**
     * Tests delete to make sure it fails when we try to delete an entry that
     * does not exist.
     *
     * @throws Exception on error
     */
    @Test
    public void testFailDeleteNoSuchObject() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        try
        {
            sysRoot.destroySubcontext( "ou=blah" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }


    /**
     * Delete operation control to test if normal delete operations occur.
     *
     * @throws Exception on error
     */
    @Test
    public void testDeleteControl() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );

        createSubContext( "ou", "blah" );

        Object obj = sysRoot.lookup( "ou=blah" );
        assertNotNull( obj );
        sysRoot.destroySubcontext( "ou=blah" );

        try
        {
            sysRoot.lookup( "ou=blah" );
            fail( "Execution should never get here due to exception!" );
        }
        catch ( LdapNameNotFoundException e )
        {
            assertEquals( "ou=system", e.getResolvedName().toString() );
            assertEquals( ResultCodeEnum.NO_SUCH_OBJECT, e.getResultCode() );
        }
    }
}
