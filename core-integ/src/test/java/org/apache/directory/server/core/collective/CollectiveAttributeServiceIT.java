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
package org.apache.directory.server.core.collective;


import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.message.AddResponse;
import org.apache.directory.ldap.client.api.message.ModifyResponse;
import org.apache.directory.ldap.client.api.message.SearchResponse;
import org.apache.directory.ldap.client.api.message.SearchResultEntry;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.ldif.LdapLdifException;
import org.apache.directory.shared.ldap.ldif.LdifUtils;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test cases for the collective attribute service.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith ( FrameworkRunner.class )
public class CollectiveAttributeServiceIT extends AbstractLdapTestUnit
{
    private static LdapConnection connection;
    
    private Entry getTestEntry( String dn, String cn ) throws LdapLdifException, LdapException
    {
        Entry subentry = LdifUtils.createEntry( 
            new DN( dn ), 
            "objectClass: top",
            "objectClass: person",
            "cn", cn ,
            "sn: testentry" );
        
        return subentry;
    }


    private Entry getTestSubentry( String dn )  throws LdapLdifException, LdapException
    {
        Entry subentry = LdifUtils.createEntry( 
            new DN( dn ),
            "objectClass: top",
            "objectClass: subentry",
            "objectClass: collectiveAttributeSubentry",
            "c-ou: configuration",
            "subtreeSpecification: { base \"ou=configuration\" }",
            "cn: testsubentry" );
        
        return subentry;
    }


    private Entry getTestSubentry2( String dn ) throws LdapLdifException, LdapException
    {
        Entry subentry = LdifUtils.createEntry( 
            new DN( dn ),
            "objectClass: top",
            "objectClass: subentry",
            "objectClass: collectiveAttributeSubentry",
            "c-ou: configuration2",
            "subtreeSpecification: { base \"ou=configuration\" }",
            "cn: testsubentry2" );
        
        return subentry;
    }


    private Entry getTestSubentry3( String dn ) throws LdapLdifException, LdapException
    {
        Entry subentry = LdifUtils.createEntry( 
            new DN( dn ),
            "objectClass: top",
            "objectClass: subentry",
            "objectClass: collectiveAttributeSubentry",
            "c-st: FL",
            "subtreeSpecification: { base \"ou=configuration\" }",
            "cn: testsubentry3" );
        
        return subentry;
    }


    private void addAdministrativeRole( String role ) throws Exception
    {
        EntryAttribute attribute = new DefaultEntryAttribute( "administrativeRole", role );
        
        connection.modify( new DN( "ou=system" ), new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, attribute ) );
    }


    private Map<String, Entry> getAllEntries() throws Exception
    {
        Map<String, Entry> resultMap = new HashMap<String, Entry>();

        Cursor<SearchResponse> cursor = 
            connection.search( "ou=system", "(objectClass=*)", SearchScope.SUBTREE, "+", "*" );
        
        while ( cursor.next() )
        {
            SearchResponse result = cursor.get();
            
            if ( result instanceof SearchResultEntry )
            {
                Entry entry = ((SearchResultEntry)result).getEntry();
                resultMap.put( entry.getDn().getName(), entry );
            }
        }

        return resultMap;
    }


    private Map<String, Entry> getAllEntriesRestrictAttributes() throws Exception
    {
        Map<String, Entry> resultMap = new HashMap<String, Entry>();

        Cursor<SearchResponse> cursor = 
            connection.search( "ou=system", "(objectClass=*)", SearchScope.SUBTREE, "cn" );
        
        while ( cursor.next() )
        {
            SearchResponse result = cursor.get();
            
            if ( result instanceof SearchResultEntry )
            {
                Entry entry = ((SearchResultEntry)result).getEntry();
                resultMap.put( entry.getDn().getName(), entry );
            }
        }

        return resultMap;
    }
    
    
    private Map<String, Entry> getAllEntriesCollectiveAttributesOnly() throws Exception
    {
        Map<String, Entry> resultMap = new HashMap<String, Entry>();

        Cursor<SearchResponse> cursor = 
            connection.search( "ou=system", "(objectClass=*)", SearchScope.SUBTREE, "c-ou", "c-st" );
        
        while ( cursor.next() )
        {
            SearchResponse result = cursor.get();
            
            if ( result instanceof SearchResultEntry )
            {
                Entry entry = ((SearchResultEntry)result).getEntry();
                resultMap.put( entry.getDn().getName(), entry );
            }
        }

        return resultMap;
    }

    
    @Before
    public void init() throws Exception
    {
        connection = IntegrationUtils.getAdminConnection( service );
    }
    

    @After
    public void shutdown() throws Exception
    {
        connection.close();
    }
    

    @Test
    public void testLookup() throws Exception
    {
        // -------------------------------------------------------------------
        // Setup the collective attribute specific administration point
        // -------------------------------------------------------------------
        addAdministrativeRole( "collectiveAttributeSpecificArea" );
        Entry subentry = getTestSubentry( "cn=testsubentry,ou=system" );
        connection.add( subentry );

        // -------------------------------------------------------------------
        // test an entry that should show the collective attribute c-ou
        // -------------------------------------------------------------------

        SearchResponse response = connection.lookup( "ou=services,ou=configuration,ou=system" );
        Entry entry = ((SearchResultEntry)response).getEntry();
        EntryAttribute c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertEquals( "configuration", c_ou.getString() );

        // -------------------------------------------------------------------
        // test an entry that should not show the collective attribute
        // -------------------------------------------------------------------

        response = connection.lookup( "ou=users,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );
        assertNull( "the c-ou collective attribute should not be present", c_ou );

        // -------------------------------------------------------------------
        // now modify entries included by the subentry to have collectiveExclusions
        // -------------------------------------------------------------------

        ModificationItem[] items = new ModificationItem[]
            { new ModificationItem( DirContext.ADD_ATTRIBUTE,
                new BasicAttribute( "collectiveExclusions", "c-ou" ) ) };
        getSystemContext( service ).modifyAttributes( "ou=services,ou=configuration", items );

        // entry should not show the c-ou collective attribute anymore
        response = connection.lookup( "ou=services,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }

        // now add more collective subentries - the c-ou should still not show due to exclusions
        Entry subentry2 = getTestSubentry2( "cn=testsubentry2,ou=system" );
        connection.add( subentry2 );

        response = connection.lookup( "ou=services,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }

        // entries without the collectiveExclusion should still show both values of c-ou
        response = connection.lookup( "ou=interceptors,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
        assertTrue( c_ou.contains( "configuration2" ) );

        // request the collective attribute specifically
        response = connection.lookup( "ou=interceptors,ou=configuration,ou=system", "c-ou" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );
        
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
        assertTrue( c_ou.contains( "configuration2" ) );
        
        // unspecify the collective attribute in the returning attribute list
        response = connection.lookup( "ou=interceptors,ou=configuration,ou=system", "objectClass" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        assertNull( "a collective c-ou attribute should not be present", c_ou );
        
        // -------------------------------------------------------------------
        // now add the subentry for the c-st collective attribute
        // -------------------------------------------------------------------

        connection.add( getTestSubentry3( "cn=testsubentry3,ou=system" ) );

        // the new attribute c-st should appear in the node with the c-ou exclusion
        response = connection.lookup( "ou=services,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        EntryAttribute c_st = entry.get( "c-st" );

        assertNotNull( "a collective c-st attribute should be present", c_st );
        assertTrue( c_st.contains( "FL" ) );

        // in node without exclusions both values of c-ou should appear with c-st value
        response = connection.lookup( "ou=interceptors,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
        assertTrue( c_ou.contains( "configuration2" ) );
        
        c_st = entry.get( "c-st" );
        assertNotNull( "a collective c-st attribute should be present", c_st );
        assertTrue( c_st.contains( "FL" ) );

        // -------------------------------------------------------------------
        // now modify an entry to exclude all collective attributes
        // -------------------------------------------------------------------

        items = new ModificationItem[]
            { new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute( "collectiveExclusions",
                "excludeAllCollectiveAttributes" ) ) };
        getSystemContext( service ).modifyAttributes( "ou=interceptors,ou=configuration", items );

        // none of the attributes should appear any longer
        response = connection.lookup( "ou=interceptors,ou=configuration,ou=system" );
        entry = ((SearchResultEntry)response).getEntry();
        c_ou = entry.get( "c-ou" );

        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }

        c_st = entry.get( "c-st" );
        
        if ( c_st != null )
        {
            assertEquals( "the c-st collective attribute should not be present", 0, c_st.size() );
        }
    }


    @Test
    public void testSearch() throws Exception
    {
        // -------------------------------------------------------------------
        // Setup the collective attribute specific administration point
        // -------------------------------------------------------------------

        addAdministrativeRole( "collectiveAttributeSpecificArea" );
        connection.add( getTestSubentry( "cn=testsubentry,ou=system" ) );

        // -------------------------------------------------------------------
        // test an entry that should show the collective attribute c-ou
        // -------------------------------------------------------------------

        Map<String, Entry> entries = getAllEntries();
        Entry entry = entries.get( "ou=services,ou=configuration,ou=system" );
        EntryAttribute c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertEquals( "configuration", c_ou.getString() );

        
        // ------------------------------------------------------------------
        // test an entry that should show the collective attribute c-ou, 
        // but restrict returned attributes to c-ou and c-st
        // ------------------------------------------------------------------
        
        entries = getAllEntriesCollectiveAttributesOnly();
        entry = entries.get( "ou=services,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertEquals( "configuration", c_ou.getString() );   
        
        
        // -------------------------------------------------------------------
        // test an entry that should not show the collective attribute
        // -------------------------------------------------------------------

        entry = entries.get( "ou=users,ou=system" );
        c_ou = entry.get( "c-ou" );
        assertNull( "the c-ou collective attribute should not be present", c_ou );

        // -------------------------------------------------------------------
        // now modify entries included by the subentry to have collectiveExclusions
        // -------------------------------------------------------------------
        ModificationItem[] items = new ModificationItem[]
            { new ModificationItem( DirContext.ADD_ATTRIBUTE,
                new BasicAttribute( "collectiveExclusions", "c-ou" ) ) };
        getSystemContext( service ).modifyAttributes( "ou=services,ou=configuration", items );
        entries = getAllEntries();

        // entry should not show the c-ou collective attribute anymore
        entry = entries.get( "ou=services,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        
        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }

        // now add more collective subentries - the c-ou should still not show due to exclusions
        connection.add( getTestSubentry2( "cn=testsubentry2,ou=system" ) );
        entries = getAllEntries();

        entry = entries.get( "ou=services,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        
        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }

        // entries without the collectiveExclusion should still show both values of c-ou
        entry = entries.get( "ou=interceptors,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
        assertTrue( c_ou.contains( "configuration2" ) );

        // -------------------------------------------------------------------
        // now add the subentry for the c-st collective attribute
        // -------------------------------------------------------------------

        connection.add( getTestSubentry3( "cn=testsubentry3,ou=system" ) );
        entries = getAllEntries();

        // the new attribute c-st should appear in the node with the c-ou exclusion
        entry = entries.get( "ou=services,ou=configuration,ou=system" );
        EntryAttribute c_st = entry.get( "c-st" );
        assertNotNull( "a collective c-st attribute should be present", c_st );
        assertTrue( c_st.contains( "FL" ) );

        // in node without exclusions both values of c-ou should appear with c-st value
        entry = entries.get( "ou=interceptors,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
        assertTrue( c_ou.contains( "configuration2" ) );
        c_st = entry.get( "c-st" );
        assertNotNull( "a collective c-st attribute should be present", c_st );
        assertTrue( c_st.contains( "FL" ) );

        // -------------------------------------------------------------------
        // now modify an entry to exclude all collective attributes
        // -------------------------------------------------------------------

        items = new ModificationItem[]
            { new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute( "collectiveExclusions",
                "excludeAllCollectiveAttributes" ) ) };
        getSystemContext( service ).modifyAttributes( "ou=interceptors,ou=configuration", items );
        entries = getAllEntries();

        // none of the attributes should appear any longer
        entry = entries.get( "ou=interceptors,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        
        if ( c_ou != null )
        {
            assertEquals( "the c-ou collective attribute should not be present", 0, c_ou.size() );
        }
        
        c_st = entry.get( "c-st" );
        
        if ( c_st != null )
        {
            assertEquals( "the c-st collective attribute should not be present", 0, c_st.size() );
        }

        // -------------------------------------------------------------------
        // Now search attributes but restrict returned attributes to cn and ou
        // -------------------------------------------------------------------

        entries = getAllEntriesRestrictAttributes();

        // we should no longer see collective attributes with restricted return attribs
        entry = entries.get( "ou=services,ou=configuration,ou=system" );
        c_st = entry.get( "c-st" );
        assertNull( "a collective c-st attribute should NOT be present", c_st );

        entry = entries.get( "ou=partitions,ou=configuration,ou=system" );
        c_ou = entry.get( "c-ou" );
        c_st = entry.get( "c-st" );
        assertNull( c_ou );
        assertNull( c_st );
    }
    
    
    @Test
    public void testAddRegularEntryWithCollectiveAttribute() throws Exception
    {
        Entry entry = getTestEntry( "cn=Ersin Er,ou=system", "Ersin Er" );
        entry.put( "c-l", "Turkiye" );
        
        AddResponse response = connection.add( entry );
        
        assertEquals( ResultCodeEnum.OBJECT_CLASS_VIOLATION, response.getLdapResult().getResultCode() );
    }
    
    
    @Test
    public void testModifyRegularEntryAddingCollectiveAttribute() throws Exception
    {
        Entry entry = getTestEntry( "cn=Ersin Er,ou=system", "Ersin Er" );
        connection.add( entry );
        
        ModifyResponse response = connection.modify( new DN( "cn=Ersin Er,ou=system" ), 
            new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, 
                new DefaultEntryAttribute( "c-l", "Turkiye" ) ) );
        
        assertEquals( ResultCodeEnum.OBJECT_CLASS_VIOLATION, response.getLdapResult().getResultCode() );
    }
    
    
    @Test
    public void testPolymorphicReturnAttrLookup() throws Exception
    {
        // -------------------------------------------------------------------
        // Setup the collective attribute specific administration point
        // -------------------------------------------------------------------
        addAdministrativeRole( "collectiveAttributeSpecificArea" );
        Entry subentry = getTestSubentry( "cn=testsubentry,ou=system" );
        connection.add( subentry );
    
        // request the collective attribute's super type specifically
        SearchResponse response = connection.lookup( "ou=interceptors,ou=configuration,ou=system", "ou" );
        
        Entry entry = ((SearchResultEntry)response).getEntry();
        
        EntryAttribute c_ou = entry.get( "c-ou" );
        assertNotNull( "a collective c-ou attribute should be present", c_ou );
        assertTrue( c_ou.contains( "configuration" ) );
    }
}
