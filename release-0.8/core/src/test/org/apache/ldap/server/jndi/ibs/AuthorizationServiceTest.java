/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.jndi.ibs;


import java.util.HashSet;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;

import org.apache.ldap.common.exception.LdapNoPermissionException;
import org.apache.ldap.common.message.LockableAttributesImpl;
import org.apache.ldap.server.db.DbSearchResult;
import org.apache.ldap.server.jndi.AbstractMultiUserJndiTest;


/**
 * Tests the Authorization service to make sure it is enforcing policies
 * correctly.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class AuthorizationServiceTest extends AbstractMultiUserJndiTest
{
    /**
     * Makes sure the admin cannot delete the admin account.
     *
     * @throws NamingException if there are problems
     */
    public void testNoDeleteOnAdminByAdmin() throws NamingException
    {
        try
        {
            sysRoot.destroySubcontext( "uid=admin" );
            fail( "admin should not be able to delete his account" );
        }
        catch ( LdapNoPermissionException e )
        {
            assertNotNull( e );
        }
    }


    /**
     * Makes sure a non-admin user cannot delete the admin account.
     *
     * @throws NamingException if there are problems
     */
    public void testNoDeleteOnAdminByNonAdmin() throws NamingException
    {
        try
        {
            sysRootAsNonAdminUser.destroySubcontext( "uid=admin" );
            fail( sysRootAsNonAdminUser.getPrincipal().getDn()
                    + " should not be able to delete his account" );
        }
        catch ( LdapNoPermissionException e )
        {
            assertNotNull( e );
        }
    }


    /**
     * Makes sure the admin cannot rename the admin account.
     *
     * @throws NamingException if there are problems
     */
    public void testNoRdnChangesOnAdminByAdmin() throws NamingException
    {
        try
        {
            sysRoot.rename( "uid=admin", "uid=alex" );
            fail( "admin should not be able to rename his account" );
        }
        catch ( LdapNoPermissionException e )
        {
            assertNotNull( e );
        }
    }


    /**
     * Makes sure a non-admin user cannot rename the admin account.
     *
     * @throws NamingException if there are problems
     */
    public void testNoRdnChangesOnAdminByNonAdmin() throws NamingException
    {
        try
        {
            sysRootAsNonAdminUser.rename( "uid=admin", "uid=alex" );
            fail( "admin should not be able to rename his account" );
        }
        catch ( LdapNoPermissionException e )
        {
            assertNotNull( e );
        }
    }


    /**
     * Makes sure the admin cannot rename the admin account.
     *
     * @throws NamingException if there are problems
     */
    public void testModifyOnAdminByAdmin() throws NamingException
    {
        Attributes attributes = new LockableAttributesImpl();
        attributes.put( "userPassword", "replaced" );
        sysRoot.modifyAttributes( "uid=admin", DirContext.REPLACE_ATTRIBUTE, attributes );
        Attributes newAttrs = sysRoot.getAttributes( "uid=admin" );
        assertEquals( "replaced", newAttrs.get( "userPassword" ).get() );
    }


    /**
     * Makes sure the a non-admin user cannot rename the admin account.
     */
    public void testModifyOnAdminByNonAdmin()
    {
        Attributes attributes = new LockableAttributesImpl();
        attributes.put( "userPassword", "replaced" );

        try
        {
            sysRootAsNonAdminUser.modifyAttributes( "uid=admin",
                    DirContext.REPLACE_ATTRIBUTE, attributes );
            fail( sysRootAsNonAdminUser.getPrincipal().getDn() +
                    " should not be able to modify attributes on admin" );
        } catch( Exception e ) { }
    }


    /**
     * Makes sure the admin can see all entries we know of on a subtree search.
     *
     * @throws NamingException if there are problems
     */
    public void testSearchSubtreeByAdmin() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        HashSet set = new HashSet();
        NamingEnumeration list = sysRoot.search( "", "(objectClass=*)", controls );
        while ( list.hasMore() )
        {
            DbSearchResult result = ( DbSearchResult ) list.next();
            set.add( result.getName() );
        }

        assertTrue( set.contains( "ou=system" ) );
        assertTrue( set.contains( "ou=groups,ou=system" ) );
        assertTrue( set.contains( "cn=administrators,ou=groups,ou=system" ) );
        assertTrue( set.contains( "ou=users,ou=system" ) );
        assertTrue( set.contains( "uid=akarasulu,ou=users,ou=system" ) );
        assertTrue( set.contains( "uid=admin,ou=system" ) );
    }


    /**
     * Makes sure the admin can see all entries we know of on a subtree search.
     *
     * @throws NamingException if there are problems
     */
    public void testSearchSubtreeByNonAdmin() throws NamingException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        HashSet set = new HashSet();
        NamingEnumeration list = sysRootAsNonAdminUser.search( "",
                "(objectClass=*)", controls );
        while ( list.hasMore() )
        {
            DbSearchResult result = ( DbSearchResult ) list.next();
            set.add( result.getName() );
        }

        assertTrue( set.contains( "ou=system" ) );
        assertTrue( set.contains( "ou=groups,ou=system" ) );
        assertFalse( set.contains( "cn=administrators,ou=groups,ou=system" ) );
        assertTrue( set.contains( "ou=users,ou=system" ) );
        assertFalse( set.contains( "uid=akarasulu,ou=users,ou=system" ) );
        assertFalse( set.contains( "uid=admin,ou=system" ) );
    }
}
