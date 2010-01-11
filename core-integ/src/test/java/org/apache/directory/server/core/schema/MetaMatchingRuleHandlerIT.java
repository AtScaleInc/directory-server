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
package org.apache.directory.server.core.schema;


import static org.apache.directory.server.core.integ.IntegrationUtils.getSchemaContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapInvalidNameException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.comparators.StringComparator;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * A test case which tests the addition of various schema elements
 * to the ldap server.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith(FrameworkRunner.class)
public class MetaMatchingRuleHandlerIT extends AbstractMetaSchemaObjectHandler
{
    private static final String DESCRIPTION0 = "A test matchingRule";
    private static final String DESCRIPTION1 = "An alternate description";

    private static final String OID = "1.3.6.1.4.1.18060.0.4.0.1.100000";
    private static final String NEW_OID = "1.3.6.1.4.1.18060.0.4.0.1.100001";


    public static SchemaManager schemaManager;


    @Before
    public void setup()
    {
        schemaManager = service.getSchemaManager();
    }


    private void createComparator() throws Exception
    {
        Attributes attrs = AttributeUtils.createAttributes( 
            "objectClass: top",
            "objectClass: metaTop",
            "objectClass: metaComparator",
            "m-fqcn: " + StringComparator.class.getName(),
            "m-oid: " + OID,
            "m-description: A test comparator" );
        
        LdapDN dn = getComparatorContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        
        // Addition
        getSchemaContext( service ).createSubcontext( dn, attrs );
        
        assertTrue( isOnDisk( dn ) );
        assertTrue( service.getSchemaManager().getComparatorRegistry().contains( OID ) );
    }
    
    
    // ----------------------------------------------------------------------
    // Test all core methods with normal operational pathways
    // ----------------------------------------------------------------------
    // Test Add operation
    // ----------------------------------------------------------------------
    @Test
    public void testAddMatchingRuleToEnabledSchema() throws Exception
    {
        createComparator();
        
        Attributes attrs = AttributeUtils.createAttributes( 
            "objectClass: top",
            "objectClass: metaTop",
            "objectClass: metaMatchingRule",
            "m-oid", OID,
            "m-syntax", SchemaConstants.INTEGER_SYNTAX,
            "m-description", DESCRIPTION0 );

        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        
        // Pre-checks
        assertFalse( isOnDisk( dn ) );
        assertFalse( service.getSchemaManager().getAttributeTypeRegistry().contains( OID ) );

        // Addition
        getSchemaContext( service ).createSubcontext( dn, attrs );
        
        // Post-checks
        assertTrue( service.getSchemaManager().getMatchingRuleRegistry().contains( OID ) );
        assertEquals( service.getSchemaManager().getMatchingRuleRegistry().getSchemaName( OID ), "apachemeta" );
        assertTrue( isOnDisk( dn ) );
    }
    
    
    @Test
    public void testAddMatchingRuleToDisabledSchema() throws Exception
    {
        createComparator();
        
        Attributes attrs = AttributeUtils.createAttributes( 
            "objectClass: top",
            "objectClass: metaTop",
            "objectClass: metaMatchingRule",
            "m-oid", OID,
            "m-syntax", SchemaConstants.INTEGER_SYNTAX,
            "m-description", DESCRIPTION0 );
        
        LdapDN dn = getMatchingRuleContainer( "nis" );
        dn.add( "m-oid" + "=" + OID );
        getSchemaContext( service ).createSubcontext( dn, attrs );
        
        assertFalse( "adding new matchingRule to disabled schema should not register it into the registries", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        assertTrue( isOnDisk( dn ) );
    }
    
    
    @Test
    public void testAddMatchingRuleToUnloadedSchema() throws Exception
    {
        createComparator();
        
        Attributes attrs = AttributeUtils.createAttributes( 
            "objectClass: top",
            "objectClass: metaTop",
            "objectClass: metaMatchingRule",
            "m-oid", OID,
            "m-syntax", SchemaConstants.INTEGER_SYNTAX,
            "m-description", DESCRIPTION0 );
        
        LdapDN dn = getMatchingRuleContainer( "notloaded" );
        dn.add( "m-oid" + "=" + OID );
        
        try
        {
            getSchemaContext( service ).createSubcontext( dn, attrs );
            fail( "Should not be there" );
        }
        catch( NameNotFoundException nnfe )
        {
            // Expected result
        }
        
        assertFalse( "adding new matchingRule to disabled schema should not register it into the registries", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        assertFalse( isOnDisk( dn ) );
    }
    
    
    // ----------------------------------------------------------------------
    // Test Delete operation
    // ----------------------------------------------------------------------
    @Test
    public void testDeleteMatchingRuleFromEnabledSchema() throws Exception
    {
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        testAddMatchingRuleToEnabledSchema();
        
        assertTrue( "matchingRule should be removed from the registry after being deleted", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        assertTrue( isOnDisk( dn ) );
        
        getSchemaContext( service ).destroySubcontext( dn );

        assertFalse( "matchingRule should be removed from the registry after being deleted", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        try
        {
            schemaManager.getMatchingRuleRegistry().lookup( OID );
            fail( "matchingRule lookup should fail after deleting it" );
        }
        catch( NamingException e )
        {
        }

        assertFalse( isOnDisk( dn ) );
    }


    @Test
    public void testDeleteMatchingRuleFromDisabledSchema() throws Exception
    {
        LdapDN dn = getMatchingRuleContainer( "nis" );
        dn.add( "m-oid" + "=" + OID );
        testAddMatchingRuleToDisabledSchema();
        
        assertFalse( "matchingRule should be removed from the registry after being deleted", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        assertTrue( isOnDisk( dn ) );
        
        getSchemaContext( service ).destroySubcontext( dn );

        assertFalse( "matchingRule should be removed from the registry after being deleted", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        assertFalse( isOnDisk( dn ) );
    }


    // ----------------------------------------------------------------------
    // Test Modify operation
    // ----------------------------------------------------------------------
    @Test
    @Ignore
    public void testModifyMatchingRuleWithModificationItems() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        MatchingRule mr = schemaManager.getMatchingRuleRegistry().lookup( OID );
        assertEquals( mr.getDescription(), DESCRIPTION0 );
        assertEquals( mr.getSyntax().getOid(), SchemaConstants.INTEGER_SYNTAX );

        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        
        ModificationItem[] mods = new ModificationItem[2];
        Attribute attr = new BasicAttribute( "m-description", DESCRIPTION1 );
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        attr = new BasicAttribute( "m-syntax", SchemaConstants.DIRECTORY_STRING_SYNTAX );
        mods[1] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        getSchemaContext( service ).modifyAttributes( dn, mods );

        assertTrue( "matchingRule OID should still be present", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        assertEquals( "matchingRule schema should be set to apachemeta", 
            schemaManager.getMatchingRuleRegistry().getSchemaName( OID ), "apachemeta" );
        
        mr = schemaManager.getMatchingRuleRegistry().lookup( OID );
        assertEquals( mr.getDescription(), DESCRIPTION1 );
        assertEquals( mr.getSyntax().getOid(), SchemaConstants.DIRECTORY_STRING_SYNTAX );
    }

    
    @Test
    @Ignore
    public void testModifyMatchingRuleWithAttributes() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        MatchingRule mr = schemaManager.getMatchingRuleRegistry().lookup( OID );
        assertEquals( mr.getDescription(), DESCRIPTION0 );
        assertEquals( mr.getSyntax().getOid(), SchemaConstants.INTEGER_SYNTAX );

        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        
        Attributes mods = new BasicAttributes( true );
        mods.put( "m-description", DESCRIPTION1 );
        mods.put( "m-syntax", SchemaConstants.DIRECTORY_STRING_SYNTAX );
        getSchemaContext( service ).modifyAttributes( dn, DirContext.REPLACE_ATTRIBUTE, mods );

        assertTrue( "matchingRule OID should still be present", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        assertEquals( "matchingRule schema should be set to apachemeta", 
            schemaManager.getMatchingRuleRegistry().getSchemaName( OID ), "apachemeta" );

        mr = schemaManager.getMatchingRuleRegistry().lookup( OID );
        assertEquals( mr.getDescription(), DESCRIPTION1 );
        assertEquals( mr.getSyntax().getOid(), SchemaConstants.DIRECTORY_STRING_SYNTAX );
    }


    // ----------------------------------------------------------------------
    // Test Rename operation
    // ----------------------------------------------------------------------
    @Test
    @Ignore
    public void testRenameMatchingRule() throws Exception
    {
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN newdn = getMatchingRuleContainer( "apachemeta" );
        newdn.add( "m-oid" + "=" + NEW_OID );
        getSchemaContext( service ).rename( dn, newdn );

        assertFalse( "old matchingRule OID should be removed from the registry after being renamed", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        try
        {
            schemaManager.getMatchingRuleRegistry().lookup( OID );
            fail( "matchingRule lookup should fail after renaming the matchingRule" );
        }
        catch( NamingException e )
        {
        }

        assertTrue( schemaManager.getMatchingRuleRegistry().contains( NEW_OID ) );
    }

    
    // ----------------------------------------------------------------------
    // Test Move operation
    // ----------------------------------------------------------------------
    @Test
    @Ignore
    public void testMoveMatchingRule() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );

        LdapDN newdn = getMatchingRuleContainer( "apache" );
        newdn.add( "m-oid" + "=" + OID );
        
        getSchemaContext( service ).rename( dn, newdn );

        assertTrue( "matchingRule OID should still be present", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        assertEquals( "matchingRule schema should be set to apache not apachemeta", 
            schemaManager.getMatchingRuleRegistry().getSchemaName( OID ), "apache" );
    }


    @Test
    @Ignore
    public void testMoveMatchingRuleAndChangeRdn() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );

        LdapDN newdn = getMatchingRuleContainer( "apache" );
        newdn.add( "m-oid" + "=" + NEW_OID );
        
        getSchemaContext( service ).rename( dn, newdn );

        assertFalse( "old matchingRule OID should NOT be present", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        assertTrue( "new matchingRule OID should be present", 
            schemaManager.getMatchingRuleRegistry().contains( NEW_OID ) );
        
        assertEquals( "matchingRule with new oid should have schema set to apache NOT apachemeta", 
            schemaManager.getMatchingRuleRegistry().getSchemaName( NEW_OID ), "apache" );
    }


    // ----------------------------------------------------------------------
    // Test move, rename, and delete when a MR exists and uses the Normalizer
    // ----------------------------------------------------------------------

    
//    public void testDeleteSyntaxWhenInUse() throws NamingException
//    {
//        LdapDN dn = getSyntaxContainer( "apachemeta" );
//        dn.add( "m-oid" + "=" + OID );
//        testAddSyntax();
//        addDependeeMatchingRule();
//        
//        try
//        {
//            super.schemaRoot.destroySubcontext( dn );
//            fail( "should not be able to delete a syntax in use" );
//        }
//        catch( LdapOperationNotSupportedException e ) 
//        {
//            assertEquals( e.getResultCode(), ResultCodeEnum.UNWILLING_TO_PERFORM );
//        }
//
//        assertTrue( "syntax should still be in the registry after delete failure", 
//            getLdapSyntaxRegistry().hasSyntax( OID ) );
//    }
//    
//    
//    public void testMoveSyntaxWhenInUse() throws NamingException
//    {
//        testAddSyntax();
//        addDependeeMatchingRule();
//        
//        LdapDN dn = getSyntaxContainer( "apachemeta" );
//        dn.add( "m-oid" + "=" + OID );
//
//        LdapDN newdn = getSyntaxContainer( "apache" );
//        newdn.add( "m-oid" + "=" + OID );
//        
//        try
//        {
//            super.schemaRoot.rename( dn, newdn );
//            fail( "should not be able to move a syntax in use" );
//        }
//        catch( LdapOperationNotSupportedException e ) 
//        {
//            assertEquals( e.getResultCode(), ResultCodeEnum.UNWILLING_TO_PERFORM );
//        }
//
//        assertTrue( "syntax should still be in the registry after move failure", 
//            registries.getLdapSyntaxRegistry().hasSyntax( OID ) );
//    }
//
//
//    public void testMoveSyntaxAndChangeRdnWhenInUse() throws NamingException
//    {
//        testAddSyntax();
//        addDependeeMatchingRule()
//        
//        LdapDN dn = getSyntaxContainer( "apachemeta" );
//        dn.add( "m-oid" + "=" + OID );s
//
//        LdapDN newdn = getSyntaxContainer( "apache" );
//        newdn.add( "m-oid" + "=" + NEW_OID );
//        
//        try
//        {
//            super.schemaRoot.rename( dn, newdn );
//            fail( "should not be able to move a syntax in use" );
//        }
//        catch( LdapOperationNotSupportedException e ) 
//        {
//            assertEquals( e.getResultCode(), ResultCodeEnum.UNWILLING_TO_PERFORM );
//        }
//
//        assertTrue( "syntax should still be in the registry after move failure", 
//            registries.getSyntaxRegistry().hasSyntax( OID ) );
//    }
//
//    

    // Need to add body to this method which creates a new matchingRule after 
    // the matchingRule addition code has been added.
    
//    private void addDependeeMatchingRule()
//    {
//        throw new NotImplementedException();
//    }
//    
//    public void testRenameNormalizerWhenInUse() throws NamingException
//    {
//        LdapDN dn = getSyntaxContainer( "apachemeta" );
//        dn.add( "m-oid" + "=" + OID );
//        testAddSyntax();
//        addDependeeMatchingRule();
//        
//        LdapDN newdn = getSyntaxContainer( "apachemeta" );
//        newdn.add( "m-oid" + "=" + NEW_OID );
//        
//        try
//        {
//            super.schemaRoot.rename( dn, newdn );
//            fail( "should not be able to rename a syntax in use" );
//        }
//        catch( LdapOperationNotSupportedException e ) 
//        {
//            assertEquals( e.getResultCode(), ResultCodeEnum.UNWILLING_TO_PERFORM );
//        }
//
//        assertTrue( "syntax should still be in the registry after rename failure", 
//            registries.getSyntaxRegistry().hasSyntax( OID ) );
//    }


    // ----------------------------------------------------------------------
    // Let's try some freaky stuff
    // ----------------------------------------------------------------------


    @Test
    @Ignore
    public void testMoveMatchingRuleToTop() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );

        LdapDN top = new LdapDN();
        top.add( "m-oid" + "=" + OID );
        
        try
        {
            getSchemaContext( service ).rename( dn, top );
            fail( "should not be able to move a matchingRule up to ou=schema" );
        }
        catch( LdapInvalidNameException e ) 
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.NAMING_VIOLATION );
        }

        assertTrue( "matchingRule should still be in the registry after move failure", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
    }


    @Test
    @Ignore
    public void testMoveMatchingRuleToComparatorContainer() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );

        LdapDN newdn = new LdapDN( "ou=comparators,cn=apachemeta" );
        newdn.add( "m-oid" + "=" + OID );
        
        try
        {
            getSchemaContext( service ).rename( dn, newdn );
            fail( "should not be able to move a matchingRule into comparators container" );
        }
        catch( LdapInvalidNameException e ) 
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.NAMING_VIOLATION );
        }

        assertTrue( "matchingRule should still be in the registry after move failure", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
    }
    
    
    @Test
    @Ignore
    public void testMoveMatchingRuleToDisabledSchema() throws Exception
    {
        testAddMatchingRuleToEnabledSchema();
        
        LdapDN dn = getMatchingRuleContainer( "apachemeta" );
        dn.add( "m-oid" + "=" + OID );

        // nis is inactive by default
        LdapDN newdn = getMatchingRuleContainer( "nis" );
        newdn.add( "m-oid" + "=" + OID );
        
        getSchemaContext( service ).rename( dn, newdn );

        assertFalse( "matchingRule OID should no longer be present", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
    }


    @Test
    @Ignore
    public void testMoveMatchingRuleToEnabledSchema() throws Exception
    {
        testAddMatchingRuleToDisabledSchema();
        
        // nis is inactive by default
        LdapDN dn = getMatchingRuleContainer( "nis" );
        dn.add( "m-oid" + "=" + OID );

        assertFalse( "matchingRule OID should NOT be present when added to disabled nis schema", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );

        LdapDN newdn = getMatchingRuleContainer( "apachemeta" );
        newdn.add( "m-oid" + "=" + OID );
        
        getSchemaContext( service ).rename( dn, newdn );

        assertTrue( "matchingRule OID should be present when moved to enabled schema", 
            schemaManager.getMatchingRuleRegistry().contains( OID ) );
        
        assertEquals( "matchingRule should be in apachemeta schema after move", 
            schemaManager.getMatchingRuleRegistry().getSchemaName( OID ), "apachemeta" );
    }
}
