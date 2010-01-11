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
package org.apache.directory.server.core.authz;


import static org.apache.directory.server.core.authz.AutzIntegUtils.addEntryACI;
import static org.apache.directory.server.core.authz.AutzIntegUtils.addPrescriptiveACI;
import static org.apache.directory.server.core.authz.AutzIntegUtils.addSubentryACI;
import static org.apache.directory.server.core.authz.AutzIntegUtils.addUserToGroup;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createAccessControlSubentry;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createUser;
import static org.apache.directory.server.core.authz.AutzIntegUtils.deleteAccessControlSubentry;
import static org.apache.directory.server.core.authz.AutzIntegUtils.getContextAs;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests whether or not authorization around search, list and lookup operations
 * work properly.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( FrameworkRunner.class )
public class SearchAuthorizationIT extends AbstractLdapTestUnit
{

    @Before
    public void setService()
    {
       AutzIntegUtils.service = service;
    }
    
    
    /**
     * The search results of tests are added to this map via put (<String, SearchResult>)
     * the map is also cleared before each search test.  This allows further inspections
     * of the results for more specific test cases.
     */
    private Map<String, SearchResult> results = new HashMap<String, SearchResult>();


    /**
     * Generates a set of simple organizationalUnit entries where the
     * ou of the entry returned is the index of the entry in the array.
     *
     * @param count the number of entries to produce
     * @return an array of entries with length = count
     */
    private Attributes[] getTestNodes( final int count )
    {
        Attributes[] attributes = new Attributes[count];
        for ( int ii = 0; ii < count; ii++ )
        {
            attributes[ii] = new BasicAttributes( true );
            Attribute oc = new BasicAttribute( "objectClass" );
            oc.add( "top" );
            oc.add( "organizationalUnit" );
            attributes[ii].put( oc );
            Attribute ou = new BasicAttribute( "ou" );
            ou.add( String.valueOf( ii ) );
            ou.add( "testEntry" );
            attributes[ii].put( ou );
            attributes[ii].put( "telephoneNumber", String.valueOf( count ) );
        }

        return attributes;
    }


    private void recursivelyAddSearchData( Name parent, Attributes[] children, final int sizeLimit, int[] count )
        throws Exception
    {
        Name[] childRdns = new Name[children.length];
        for ( int ii = 0; ii < children.length && count[0] < sizeLimit; ii++ )
        {
            Name childRdn = new LdapDN();
            childRdn.addAll( parent );
            childRdn.add( "ou=" + ii );
            childRdns[ii] = childRdn;
            getSystemContext( service ).createSubcontext( childRdn, children[ii] );
            count[0]++;
        }

        if ( count[0] >= sizeLimit )
        {
            return;
        }

        for ( int ii = 0; ii < children.length && count[0] < sizeLimit; ii++ )
        {
            recursivelyAddSearchData( childRdns[ii], children, sizeLimit, count );
        }
    }


    /**
     * Starts creating nodes under a parent with a set number of children.  First
     * a single node is created under the parent.  Thereafter a number of children
     * determined by the branchingFactor is added.  Until a sizeLimit is reached
     * descendants are created this way in a breath first recursive descent.
     *
     * @param parent the parent under which the first node is created
     * @param branchingFactor how to brach the data
     * @param sizelimit the amount of entries 
     * @return the immediate child node created under parent which contains the subtree
     * @throws NamingException on error
     */
    private Name addSearchData( Name parent, int branchingFactor, int sizelimit ) throws Exception
    {
        parent = ( Name ) parent.clone();
        parent.add( "ou=tests" );
        getSystemContext( service ).createSubcontext( parent, getTestNodes( 1 )[0] );
        recursivelyAddSearchData( parent, getTestNodes( branchingFactor ), sizelimit, new int[]
            { 1 } );
        return parent;
    }


    /**
     * Recursively deletes all entries including the base specified.
     *
     * @param rdn the relative dn from ou=system of the entry to delete recursively
     * @throws NamingException if there are problems deleting entries
     */
    private void recursivelyDelete( Name rdn ) throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        NamingEnumeration<SearchResult> results = sysRoot.search( rdn, "(objectClass=*)", new SearchControls() );
        
        while ( results.hasMore() )
        {
            SearchResult result = results.next();
            Name childRdn = new LdapDN( result.getName() );
            childRdn.remove( 0 );
            recursivelyDelete( childRdn );
        }
        sysRoot.destroySubcontext( rdn );
    }


    /**
     * Performs a single level search as a specific user on newly created data and checks
     * that result set count is 3.  The basic (objectClass=*) filter is used.
     *
     * @param uid the uid RDN attribute value for the user under ou=users,ou=system
     * @param password the password of the user
     * @return true if the search succeeds as expected, false otherwise
     * @throws NamingException if there are problems conducting the search
     */
    private boolean checkCanSearchAs( String uid, String password ) throws Exception
    {
        return checkCanSearchAs( uid, password, "(objectClass=*)", null, 3 );
    }


    /**
     * Performs a single level search as a specific user on newly created data and checks
     * that result set count is equal to a user specified amount.  The basic
     * (objectClass=*) filter is used.
     *
     * @param uid the uid RDN attribute value for the user under ou=users,ou=system
     * @param password the password of the user
     * @param resultSetSz the expected size of the results
     * @return true if the search succeeds as expected, false otherwise
     * @throws NamingException if there are problems conducting the search
     */
    private boolean checkCanSearchAs( String uid, String password, int resultSetSz ) throws Exception
    {
        return checkCanSearchAs( uid, password, "(objectClass=*)", null, resultSetSz );
    }


    /**
     * Performs a search as a specific user on newly created data and checks
     * that result set count is equal to a user specified amount.  The basic
     * (objectClass=*) filter is used.
     *
     * @param uid the uid RDN attribute value for the user under ou=users,ou=system
     * @param password the password of the user
     * @param cons search controls
     * @param resultSetSz the expected size of the results
     * @return true if the search succeeds as expected, false otherwise
     * @throws NamingException if there are problems conducting the search
     */
    private boolean checkCanSearchAs( String uid, String password, SearchControls cons, int resultSetSz )
        throws Exception
    {
        return checkCanSearchAs( uid, password, "(objectClass=*)", cons, resultSetSz );
    }


    /**
     * Performs a search as a specific user on newly created data and checks
     * that result set count is equal to a user specified amount.
     *
     * @param uid the uid RDN attribute value for the user under ou=users,ou=system
     * @param password the password of the user
     * @param filter the search filter to use
     * @param cons search controls
     * @param resultSetSz the expected size of the results
     * @return true if the search succeeds as expected, false otherwise
     * @throws NamingException if there are problems conducting the search
     */
    private boolean checkCanSearchAs( String uid, String password, String filter, SearchControls cons, int resultSetSz )
        throws Exception
    {
        if ( cons == null )
        {
            cons = new SearchControls();
        }

        Name base = addSearchData( new LdapDN(), 3, 10 );
        Name userDn = new LdapDN( "uid=" + uid + ",ou=users,ou=system" );
        try
        {
            results.clear();
            DirContext userCtx = getContextAs( userDn, password );
            NamingEnumeration<SearchResult> list = userCtx.search( base, filter, cons );
            int counter = 0;
            
            while ( list.hasMore() )
            {
                SearchResult result = list.next();
                results.put( result.getName(), result );
                counter++;
            }
            return counter == resultSetSz;
        }
        catch ( LdapNoPermissionException e )
        {
            return false;
        }
        finally
        {
            recursivelyDelete( base );
        }
    }


    /**
     * Adds an entryACI to specified entry below ou=system and runs a search.  Then it
     * checks to see the result size is correct.
     *
     * @param uid the uid RDN attribute value for the user under ou=users,ou=system
     * @param password the password of the user
     * @param cons the search controls
     * @param rdn the rdn
     * @param aci the aci
     * @param resultSetSz the result sz
     * @return true if the search succeeds as expected, false otherwise
     * @throws NamingException if there are problems conducting the search
     */
    private boolean checkSearchAsWithEntryACI( String uid, String password, SearchControls cons, Name rdn, String aci,
        int resultSetSz ) throws Exception
    {
        if ( cons == null )
        {
            cons = new SearchControls();
        }

        Name base = addSearchData( new LdapDN(), 3, 10 );
        addEntryACI( rdn, aci );
        Name userDn = new LdapDN( "uid=" + uid + ",ou=users,ou=system" );
        try
        {
            results.clear();
            DirContext userCtx = getContextAs( userDn, password );
            NamingEnumeration<SearchResult> list = userCtx.search( base, "(objectClass=*)", cons );
            int counter = 0;
            
            while ( list.hasMore() )
            {
                SearchResult result = list.next();
                results.put( result.getName(), result );
                counter++;
            }
            return counter == resultSetSz;
        }
        catch ( LdapNoPermissionException e )
        {
            return false;
        }
        finally
        {
            recursivelyDelete( base );
        }
    }


    /**
     * Checks to see that the addSearchData() and the recursiveDelete()
     * functions in this test work properly.
     *
     * @throws NamingException if there is a problem with the implementation of
     * these utility functions
     */
    @Test
    public void testAddSearchData() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        Name base = addSearchData( new LdapDN(), 3, 10 );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        NamingEnumeration<SearchResult> results = sysRoot.search( base, "(objectClass=*)", controls );
        int counter = 0;
        
        while ( results.hasMore() )
        {
            results.next();
            counter++;
        }

        assertEquals( 10, counter );
        recursivelyDelete( base );
        //noinspection EmptyCatchBlock
        try
        {
            sysRoot.lookup( base );
            fail();
        }
        catch ( LdapNameNotFoundException e )
        {
        }
    }


    // -----------------------------------------------------------------------
    // All or nothing search ACI rule tests
    // -----------------------------------------------------------------------

    /**
     * Checks to make sure group membership based userClass works for add operations.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testGrantAdministrators() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "billyd", "billyd" ) );

        // Gives search perms to all users in the Administrators group for
        // entries and all attribute types and values
        createAccessControlSubentry( "searchAdmin", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { userGroup { \"cn=Administrators,ou=groups,ou=system\" } }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now add that test entry which we could not before
        // add op should still fail since billd is not in the admin group
        assertFalse( checkCanSearchAs( "billyd", "billyd" ) );

        // now add billyd to the Administrator group and try again
        addUserToGroup( "billyd", "Administrators" );

        // try an add operation which should succeed with ACI and group membership change
        assertTrue( checkCanSearchAs( "billyd", "billyd" ) );
    }


    /**
     * Checks to make sure name based userClass works for search operations.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testGrantSearchByName() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "billyd", "billyd" ) );

        // now add a subentry that enables user billyd to add an entry below ou=system
        createAccessControlSubentry( "billydSearch", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { name { \"uid=billyd,ou=users,ou=system\" } }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // should work now that billyd is authorized by name
        assertTrue( checkCanSearchAs( "billyd", "billyd" ) );
    }


    /**
     * Checks to make sure name based userClass works for search operations
     * when we vary the case of the DN.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testGrantSearchByNameUserDnCase() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "BillyD", "billyd" ) );

        // now add a subentry that enables user billyd to add an entry below ou=system
        createAccessControlSubentry( "billydSearch", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { name { \"uid=billyd,ou=users,ou=system\" } }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // should work now that billyd is authorized by name
        assertTrue( checkCanSearchAs( "BillyD", "billyd" ) );
    }


    /**
     * Checks to make sure subtree based userClass works for search operations.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testGrantSearchBySubtree() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "billyd", "billyd" ) );

        // now add a subentry that enables user billyd to add an entry below ou=system
        createAccessControlSubentry( "billySearchBySubtree", "{ " + "identificationTag \"searchAci\", "
            + "precedence 14, " + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { subtree { { base \"ou=users,ou=system\" } } }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials {  grantRead, grantReturnDN, grantBrowse } } } } }" );

        // should work now that billyd is authorized by the subtree userClass
        assertTrue( checkCanSearchAs( "billyd", "billyd" ) );
    }


    /**
     * Checks to make sure <b>allUsers</b> userClass works for search operations.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testGrantSearchAllUsers() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an search operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "billyd", "billyd" ) );

        // now add a subentry that enables anyone to search an entry below ou=system
        createAccessControlSubentry( "anybodySearch", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { { " + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now search that tree which we could not before
        // should work now with billyd now that all users are authorized
        assertTrue( checkCanSearchAs( "billyd", "billyd" ) );
    }


    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    /**
     * Checks to make sure search does not return entries not assigned the
     * perscriptiveACI and that it does not fail with an exception.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testSelectiveGrantsAllUsers() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.SUBTREE_SCOPE );
        assertFalse( checkCanSearchAs( "billyd", "billyd", cons, 4 ) );

        // now add a subentry that enables anyone to add an entry below ou=system
        // down two more rdns for DNs of a max size of 3
        createAccessControlSubentry( "anybodySearch", "{ maximum 2 }", "{ " + "identificationTag \"searchAci\", "
            + "precedence 14, " + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now add that test entry which we could not before
        // should work now with billyd now that all users are authorized
        assertTrue( checkCanSearchAs( "billyd", "billyd", cons, 4 ) );
    }


    /**
     * Checks to make sure attributeTypes are not present when permissions are
     * not given for reading them and their values.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testHidingAttributes() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.SUBTREE_SCOPE );
        assertFalse( checkCanSearchAs( "billyd", "billyd", cons, 4 ) );

        // now add a subentry that enables anyone to search an entry below ou=system
        // down two more rdns for DNs of a max size of 3.  It only grants access to
        // the ou and objectClass attributes however.
        createAccessControlSubentry( "excluseTelephoneNumber", "{ maximum 2 }", "{ "
            + "identificationTag \"searchAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allAttributeValues { ou, objectClass } }, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now add that search and find 4 entries
        assertTrue( checkCanSearchAs( "billyd", "billyd", cons, 4 ) );

        // check to make sure the telephoneNumber attribute is not present in results
        for ( SearchResult result : results.values() )
        {
            assertNull( result.getAttributes().get( "telephoneNumber" ) );
        }

        // delete the subentry to test more general rule's inclusion of telephoneNumber
        deleteAccessControlSubentry( "excluseTelephoneNumber" );

        // now add a subentry that enables anyone to search an entry below ou=system
        // down two more rdns for DNs of a max size of 3.  This time we should be able
        // to see the telephoneNumber attribute
        createAccessControlSubentry( "includeAllAttributeTypesAndValues", "{ maximum 2 }", "{ "
            + "identificationTag \"searchAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues }, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // again we should find four entries
        assertTrue( checkCanSearchAs( "billyd", "billyd", cons, 4 ) );

        // check now to make sure the telephoneNumber attribute is present in results
        for ( SearchResult result : results.values() )
        {
            assertNotNull( result.getAttributes().get( "telephoneNumber" ) );
        }
    }


    /**
     * Checks to make sure specific attribute values are not present when
     * read permission is denied.
     *
     * @throws javax.naming.NamingException if the test encounters an error
     */
    @Test
    public void testHidingAttributeValues() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // try an add operation which should fail without any ACI
        assertFalse( checkCanSearchAs( "billyd", "billyd", 3 ) );

        // now add a subentry that enables anyone to search an entry below ou=system
        // down two more rdns for DNs of a max size of 3.  It only grants access to
        // the ou and objectClass attributes however.
        createAccessControlSubentry(
            "excluseOUValue",
            "{ maximum 2 }",
            "{ "
                + "identificationTag \"searchAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { allUsers }, "
                + "userPermissions { { "
                + "protectedItems {entry, attributeType { ou }, allAttributeValues { objectClass }, attributeValue { ou=0, ou=1, ou=2 } }, "
                + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now add that search and find 4 entries
        assertTrue( checkCanSearchAs( "billyd", "billyd", 3 ) );

        // check to make sure the ou attribute value "testEntry" is not present in results
        for ( SearchResult result : results.values() )
        {
            assertFalse( result.getAttributes().get( "ou" ).contains( "testEntry" ) );
        }

        // delete the subentry to test more general rule's inclusion of all values
        deleteAccessControlSubentry( "excluseOUValue" );

        // now add a subentry that enables anyone to search an entry below ou=system
        // down two more rdns for DNs of a max size of 3.  This time we should be able
        // to see the telephoneNumber attribute
        createAccessControlSubentry( "includeAllAttributeTypesAndValues", "{ maximum 2 }", "{ "
            + "identificationTag \"searchAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues }, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // again we should find four entries
        assertTrue( checkCanSearchAs( "billyd", "billyd", 3 ) );

        // check now to make sure the telephoneNumber attribute is present in results
        for ( SearchResult result : results.values() )
        {
            assertTrue( result.getAttributes().get( "ou" ).contains( "testEntry" ) );
        }
    }


    /**
     * Adds a perscriptiveACI to allow search, tests for success, then adds entryACI
     * to deny read, browse and returnDN to a specific entry and checks to make sure
     * that entry cannot be accessed via search as a specific user.
     *
     * @throws NamingException if the test is broken
     */
    @Test
    public void testPerscriptiveGrantWithEntryDenial() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // now add an entryACI denies browse, read and returnDN to a specific entry
        String aci = "{ " + "identificationTag \"denyAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { denyRead, denyReturnDN, denyBrowse } } } } }";

        // try a search operation which should fail without any prescriptive ACI
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.SUBTREE_SCOPE );
        LdapDN rdn = new LdapDN( "ou=tests" );
        assertFalse( checkSearchAsWithEntryACI( "billyd", "billyd", cons, rdn, aci, 9 ) );

        // now add a subentry that enables anyone to search below ou=system
        createAccessControlSubentry( "anybodySearch", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { { " + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now search the tree which we could not before
        // should work with billyd now that all users are authorized
        // we should NOT see the entry we are about to deny access to
        assertTrue( checkSearchAsWithEntryACI( "billyd", "billyd", cons, rdn, aci, 9 ) );
        assertNull( results.get( "ou=tests,ou=system" ) );

        // try without the entry ACI .. just perscriptive and see ou=tests,ou=system
        assertTrue( checkCanSearchAs( "billyd", "billyd", cons, 10 ) );
        assertNotNull( results.get( "ou=tests,ou=system" ) );
    }


    /**
     * Adds a perscriptiveACI to allow search, tests for success, then adds entryACI
     * to deny read, browse and returnDN to a specific entry and checks to make sure
     * that entry cannot be accessed via search as a specific user.  Here the
     * precidence of the ACI is put to the test.
     *
     * @throws NamingException if the test is broken
     */
    @Test
    public void testPerscriptiveGrantWithEntryDenialWithPrecidence() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // now add an entryACI denies browse, read and returnDN to a specific entry
        String aci = "{ " + "identificationTag \"denyAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { denyRead, denyReturnDN, denyBrowse } } } } }";

        // try a search operation which should fail without any prescriptive ACI
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.SUBTREE_SCOPE );
        LdapDN rdn = new LdapDN( "ou=tests" );
        assertFalse( checkSearchAsWithEntryACI( "billyd", "billyd", cons, rdn, aci, 9 ) );

        // now add a subentry that enables anyone to search below ou=system
        createAccessControlSubentry( "anybodySearch", "{ " + "identificationTag \"searchAci\", " + "precedence 15, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { { " + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // see if we can now search the tree which we could not before
        // should work with billyd now that all users are authorized
        // we should also see the entry we are about to deny access to
        // we see it because the precidence of the grant is greater
        // than the precedence of the denial
        assertTrue( checkSearchAsWithEntryACI( "billyd", "billyd", cons, rdn, aci, 10 ) );
        assertNotNull( results.get( "ou=tests,ou=system" ) );

        // now add an entryACI denies browse, read and returnDN to a specific entry
        // but this time the precedence will be higher than that of the grant
        aci = "{ " + "identificationTag \"denyAci\", " + "precedence 16, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { denyRead, denyReturnDN, denyBrowse } } } } }";

        // see if we can now search the tree which we could not before
        // should work with billyd now that all users are authorized
        // we should NOT see the entry we are about to deny access to
        // we do NOT see it because the precidence of the grant is less
        // than the precedence of the denial - so the denial wins
        assertTrue( checkSearchAsWithEntryACI( "billyd", "billyd", cons, rdn, aci, 9 ) );
        assertNull( results.get( "ou=tests,ou=system" ) );
    }


    /**
     * Performs an object level search on the specified subentry relative to ou=system as a specific user.
     *
     * @param uid the uid RDN attribute value of the user to perform the search as
     * @param password the password of the user
     * @param rdn the relative name to the subentry under the ou=system AP
     * @return the single search result if access is allowed or null
     * @throws NamingException if the search fails w/ exception other than no permission
     */
    private SearchResult checkCanSearhSubentryAs( String uid, String password, Name rdn ) throws Exception
    {
        DirContext userCtx = getContextAs( new LdapDN( "uid=" + uid + ",ou=users,ou=system" ), password );
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.OBJECT_SCOPE );
        SearchResult result = null;
        NamingEnumeration<SearchResult> list = null;

        //noinspection EmptyCatchBlock
        try
        {
            list = userCtx.search( rdn, "(objectClass=*)", cons );
            if ( list.hasMore() )
            {
                result = list.next();
                list.close();
                return result;
            }
        }
        catch ( LdapNoPermissionException e )
        {
            return null;
        }
        finally
        {
            if ( list != null )
            {
                list.close();
            }
        }

        return result;
    }


    @Test
    public void testSubentryAccess() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // now add a subentry that enables anyone to search below ou=system
        createAccessControlSubentry( "anybodySearch", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { { " + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // check and see if we can access the subentry now
        assertNotNull( checkCanSearhSubentryAs( "billyd", "billyd", new LdapDN( "cn=anybodySearch" ) ) );

        // now add a denial to prevent all users except the admin from accessing the subentry
        addSubentryACI( "{ " + "identificationTag \"searchAci\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { denyRead, denyReturnDN, denyBrowse } } } } }" );

        // now we should not be able to access the subentry with a search
        assertNull( checkCanSearhSubentryAs( "billyd", "billyd", new LdapDN( "cn=anybodySearch" ) ) );
    }


    @Test
    public void testGetMatchedName() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );

        // now add a subentry that enables anyone to search/lookup and disclose on error
        // below ou=system, with the exclusion of ou=groups and everything below it
        createAccessControlSubentry( "selectiveDiscloseOnError", "{ specificExclusions { chopBefore:\"ou=groups\" } }",
            "{ " + "identificationTag \"searchAci\", " + "precedence 14, " + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, " + "userPermissions { { "
                + "protectedItems {entry, allUserAttributeTypesAndValues}, "
                + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse, grantDiscloseOnError } } } } }" );

        // get a context as the user and try a lookup of a non-existant entry under ou=groups,ou=system
        DirContext userCtx = getContextAs( new LdapDN( "uid=billyd,ou=users,ou=system" ), "billyd" );
        try
        {
            userCtx.lookup( "cn=blah,ou=groups" );
        }
        catch ( NamingException e )
        {
            Name matched = e.getResolvedName();

            // we should not see ou=groups,ou=system for the remaining name
            assertEquals( matched.toString(), "ou=system" );
        }

        // now delete and replace subentry with one that does not excluse ou=groups,ou=system
        deleteAccessControlSubentry( "selectiveDiscloseOnError" );
        createAccessControlSubentry( "selectiveDiscloseOnError", "{ " + "identificationTag \"searchAci\", "
            + "precedence 14, " + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { allUsers }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse, grantDiscloseOnError } } } } }" );

        // now try a lookup of a non-existant entry under ou=groups,ou=system again
        try
        {
            userCtx.lookup( "cn=blah,ou=groups" );
        }
        catch ( NamingException e )
        {
            Name matched = e.getResolvedName();

            // we should not see ou=groups,ou=system for the remaining name
            assertEquals( matched.toString(), "ou=groups,ou=system" );
        }
    }
    
    @Test
    public void testUserClassParentOfEntry() throws Exception
    {
        // create the non-admin user
        createUser( "billyd", "billyd" );
        
        // create an entry subordinate to the user
        DirContext billydCtx = AutzIntegUtils.getContextAsAdmin("uid=billyd,ou=users,ou=system");
        Attributes phoneBook = new BasicAttributes( "ou", "phoneBook", true );
        Attribute objectClass = new BasicAttribute( "objectClass" );
        phoneBook.put( objectClass );
        objectClass.add( "top" );
        objectClass.add( "organizationalUnit" );
        billydCtx.createSubcontext( "ou=phoneBook", phoneBook );

        // now add a subentry that enables anyone to search below their own entries
        createAccessControlSubentry( "anybodySearchTheirSubordinates", "{ " + "identificationTag \"searchAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { { " + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { grantRead, grantReturnDN, grantBrowse } } } } }" );

        // check and see if we can access the subentry now
        assertNotNull( checkCanSearhSubentryAs( "billyd", "billyd", new LdapDN( "ou=phoneBook,uid=billyd,ou=users" ) ) );

        // now add a denial to prevent all users except the admin from accessing the subentry
        addPrescriptiveACI( "anybodySearchTheirSubordinates", "{ " + "identificationTag \"anybodyDontSearchTheirSubordinates\", " + "precedence 14, " + "authenticationLevel none, "
            + "itemOrUserFirst userFirst: { " + "userClasses { parentOfEntry }, " + "userPermissions { { "
            + "protectedItems {entry, allUserAttributeTypesAndValues}, "
            + "grantsAndDenials { denyRead, denyReturnDN, denyBrowse } } } } }" );

        // now we should not be able to access the subentry with a search
        assertNull( checkCanSearhSubentryAs( "billyd", "billyd", new LdapDN( "ou=phoneBook,uid=billyd,ou=users" ) ) );
    }
}
