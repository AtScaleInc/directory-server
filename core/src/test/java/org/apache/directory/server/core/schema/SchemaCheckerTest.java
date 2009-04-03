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


import junit.framework.TestCase;

import javax.naming.NamingException;

import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.schema.SchemaChecker;
import org.apache.directory.server.schema.bootstrap.ApacheSchema;
import org.apache.directory.server.schema.bootstrap.BootstrapSchemaLoader;
import org.apache.directory.server.schema.bootstrap.CoreSchema;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.bootstrap.SystemSchema;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.DefaultOidRegistry;
import org.apache.directory.server.schema.registries.DefaultRegistries;
import org.apache.directory.server.schema.registries.ObjectClassRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;


/**
 * Tests to make sure the schema checker is operating correctly.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SchemaCheckerTest extends TestCase
{
    static Registries registries;


    @BeforeClass
    protected void setUp() throws Exception
    {
        BootstrapSchemaLoader loader = new BootstrapSchemaLoader();
        registries = new DefaultRegistries( "bootstrap", loader, new DefaultOidRegistry() );

        Set<Schema> schemas = new HashSet<Schema>();
        schemas.add( new SystemSchema() );
        schemas.add( new ApacheSchema() );
        schemas.add( new CoreSchema() );
        loader.loadWithDependencies( schemas,registries );

        List<Throwable> errors = registries.checkRefInteg();
        
        if ( !errors.isEmpty() )
        {
            NamingException e = new NamingException();
            e.setRootCause( ( Throwable ) errors.get( 0 ) );
            throw e;
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations replace objectClasses.
     */
    @Test
    public void testPreventStructuralClassRemovalOnModifyReplace() throws Exception
    {
        LdapDN name = new LdapDN( "uid=akarasulu,ou=users,dc=example,dc=com" );
        ModificationOperation mod = ModificationOperation.REPLACE_ATTRIBUTE;
        ServerEntry modifyAttributes = new DefaultServerEntry( registries );
        AttributeType atCN = registries.getAttributeTypeRegistry().lookup( "cn" );
        modifyAttributes.put( new DefaultServerAttribute( atCN ) );

        ObjectClassRegistry ocRegistry = registries.getObjectClassRegistry();

        // this should pass
        SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, modifyAttributes );

        // this should succeed since person is still in replaced set and is structural
        modifyAttributes.removeAttributes( atCN );
        AttributeType atOC = registries.getAttributeTypeRegistry().lookup( "objectClass" );
        EntryAttribute objectClassesReplaced = new DefaultServerAttribute( atOC );
        objectClassesReplaced.add( "top" );
        objectClassesReplaced.add( "person" );
        modifyAttributes.put( objectClassesReplaced );
        SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, modifyAttributes );

        // this should fail since only top is left
        objectClassesReplaced = new DefaultServerAttribute( atOC );
        objectClassesReplaced.add( "top" );
        modifyAttributes.put( objectClassesReplaced );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, modifyAttributes );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }

        // this should fail since the modify operation tries to delete all
        // objectClass attribute values
        modifyAttributes.removeAttributes( "cn" );
        objectClassesReplaced = new DefaultServerAttribute( atOC );
        modifyAttributes.put( objectClassesReplaced );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, modifyAttributes );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations remove objectClasses.
     *
    public void testPreventStructuralClassRemovalOnModifyRemove() throws Exception
    {
        LdapDN name = new LdapDN( "uid=akarasulu,ou=users,dc=example,dc=com" );
        int mod = DirContext.REMOVE_ATTRIBUTE;
        Attributes modifyAttributes = new AttributesImpl( true );
        Attribute entryObjectClasses = new AttributeImpl( "objectClass" );
        entryObjectClasses.add( "top" );
        entryObjectClasses.add( "person" );
        entryObjectClasses.add( "organizationalPerson" );
        modifyAttributes.put( new AttributeImpl( "cn" ) );

        ObjectClassRegistry ocRegistry = registries.getObjectClassRegistry();

        // this should pass
        SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, modifyAttributes,
            entryObjectClasses );

        // this should succeed since person is left and is structural
        modifyAttributes.remove( "cn" );
        Attribute objectClassesRemoved = new AttributeImpl( "objectClass" );
        objectClassesRemoved.add( "person" );
        modifyAttributes.put( objectClassesRemoved );
        SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, modifyAttributes,
            entryObjectClasses );

        // this should fail since only top is left
        modifyAttributes.remove( "cn" );
        objectClassesRemoved = new AttributeImpl( "objectClass" );
        objectClassesRemoved.add( "person" );
        objectClassesRemoved.add( "organizationalPerson" );
        modifyAttributes.put( objectClassesRemoved );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, modifyAttributes,
                entryObjectClasses );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }

        // this should fail since the modify operation tries to delete all
        // objectClass attribute values
        modifyAttributes.remove( "cn" );
        objectClassesRemoved = new AttributeImpl( "objectClass" );
        modifyAttributes.put( objectClassesRemoved );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, modifyAttributes,
                entryObjectClasses );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations remove RDN attributes.
     */
    @Test
    public void testPreventRdnChangeOnModifyRemove() throws Exception
    {
        ModificationOperation mod = ModificationOperation.REMOVE_ATTRIBUTE;
        LdapDN name = new LdapDN( "ou=user,dc=example,dc=com" );
        ServerEntry attributes = new DefaultServerEntry( registries, name );
        attributes.put( "cn", "does not matter" );

        // postive test which should pass
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );

        // test should fail since we are removing the ou attribute
        AttributeType OU_AT = registries.getAttributeTypeRegistry().lookup( "ou" );
        attributes.put( new DefaultServerAttribute( "ou", OU_AT ) );

        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // test success using more than one attribute for the Rdn but not modifying rdn attribute
        name = new LdapDN( "ou=users+cn=system users,dc=example,dc=com" );
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "sn", "does not matter" );
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );

        // test for failure when modifying Rdn attribute in multi attribute Rdn
        AttributeType CN_AT = registries.getAttributeTypeRegistry().lookup( "cn" );
        attributes.put( new DefaultServerAttribute( "cn", CN_AT ) );
        
        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // should succeed since the value being deleted from the rdn attribute is
        // is not used when composing the Rdn
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "ou", "container" );
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );

        // now let's make it fail again just by providing the right value for ou (users)
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "ou", "users" );
        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations replace RDN attributes.
     */
    @Test
    public void testPreventRdnChangeOnModifyReplace() throws Exception
    {
        ModificationOperation mod = ModificationOperation.REPLACE_ATTRIBUTE;
        LdapDN name = new LdapDN( "ou=user,dc=example,dc=com" );
        ServerEntry attributes = new DefaultServerEntry( registries, name );
        attributes.put( "cn", "does not matter" );

        // postive test which should pass
        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );

        // test should fail since we are removing the ou attribute
        attributes.put( "ou", (String)null );
        
        try
        {
            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // test success using more than one attribute for the Rdn but not modifying rdn attribute
        name = new LdapDN( "ou=users+cn=system users,dc=example,dc=com" );
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "sn", "does not matter" );
        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );

        // test for failure when modifying Rdn attribute in multi attribute Rdn
        attributes.put("cn", (String)null );
        
        try
        {
            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // should succeed since the values being replaced from the rdn attribute is
        // is includes the old Rdn attribute value
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "ou", "container" );
        attributes.put( "ou", "users" );
        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );

        // now let's make it fail by not including the old value for ou (users)
        attributes = new DefaultServerEntry( registries, name );
        attributes.put( "ou", "container" );
        try
        {
            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attributes, registries.getOidRegistry() );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }
    }


    // ------------------------------------------------------------------------
    // Single Attribute Test Cases
    // ------------------------------------------------------------------------

    /**
     * Test case to check the schema checker operates correctly when modify
     * operations replace objectClasses.
     */
    @Test
    public void testPreventStructuralClassRemovalOnModifyReplaceAttribute() throws Exception
    {
        ObjectClassRegistry ocRegistry = registries.getObjectClassRegistry();
        AttributeType OBJECT_CLASS = registries.getAttributeTypeRegistry().lookup( "objectClass" );
        AttributeType CN_AT = registries.getAttributeTypeRegistry().lookup( "cn" );

        // this should pass
        LdapDN name = new LdapDN( "uid=akarasulu,ou=users,dc=example,dc=com" );
        ModificationOperation mod = ModificationOperation.REPLACE_ATTRIBUTE;
        SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, new DefaultServerAttribute( "cn", CN_AT ) );

        // this should succeed since person is still in replaced set and is structural
        ServerAttribute objectClassesReplaced = new DefaultServerAttribute( "objectClass", OBJECT_CLASS );
        objectClassesReplaced.add( "top" );
        objectClassesReplaced.add( "person" );
        SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, objectClassesReplaced );

        // this should fail since only top is left
        objectClassesReplaced = new DefaultServerAttribute( "objectClass", OBJECT_CLASS );
        objectClassesReplaced.add( "top" );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, objectClassesReplaced );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }

        // this should fail since the modify operation tries to delete all
        // objectClass attribute values
        objectClassesReplaced = new DefaultServerAttribute( "objectClass", OBJECT_CLASS );
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, mod, objectClassesReplaced );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations remove objectClasses.
     */
    @Test
    public void testPreventStructuralClassRemovalOnModifyRemoveAttribute() throws Exception
    {
        AttributeTypeRegistry atReg = registries.getAttributeTypeRegistry();
        LdapDN name = new LdapDN( "uid=akarasulu,ou=users,dc=example,dc=com" );
        ModificationOperation mod = ModificationOperation.REMOVE_ATTRIBUTE;
        AttributeType ocAt = atReg.lookup( "objectClass" );
        
        ServerAttribute entryObjectClasses = new DefaultServerAttribute( "objectClass", ocAt );
        entryObjectClasses.add( "top", "person", "organizationalPerson" );

        ObjectClassRegistry ocRegistry = registries.getObjectClassRegistry();

        // this should pass
        SchemaChecker.preventStructuralClassRemovalOnModifyRemove( 
            ocRegistry, 
            name, 
            mod, 
            new DefaultServerAttribute( "cn", atReg.lookup( "cn" ) ),
            entryObjectClasses );

        // this should succeed since person is left and is structural
        ServerAttribute objectClassesRemoved = new DefaultServerAttribute( 
            "objectClass", ocAt );
        objectClassesRemoved.add( "person" );
        SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, objectClassesRemoved,
            entryObjectClasses );

        // this should fail since only top is left
        objectClassesRemoved = new DefaultServerAttribute( "objectClass", ocAt );
        objectClassesRemoved.add( "person", "organizationalPerson" );
        
        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, objectClassesRemoved,
                entryObjectClasses );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }

        // this should fail since the modify operation tries to delete all
        // objectClass attribute values
        objectClassesRemoved = new DefaultServerAttribute( "objectClass", ocAt );

        try
        {
            SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, mod, objectClassesRemoved,
                entryObjectClasses );
            fail( "should never get here due to an LdapSchemaViolationException" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( e.getResultCode(), ResultCodeEnum.OBJECT_CLASS_MODS_PROHIBITED );
        }
    }


    /**
     * Test case to check the schema checker operates correctly when modify
     * operations remove RDN attributes.
     */
    @Test
    public void testPreventRdnChangeOnModifyRemoveAttribute() throws Exception
    {
        OidRegistry registry = new MockOidRegistry();
        ModificationOperation mod = ModificationOperation.REMOVE_ATTRIBUTE;
        LdapDN name = new LdapDN( "ou=user,dc=example,dc=com" );
        AttributeType cnAt = registries.getAttributeTypeRegistry().lookup( "cn" );
        AttributeType ouAt = registries.getAttributeTypeRegistry().lookup( "ou" );
        AttributeType snAt = registries.getAttributeTypeRegistry().lookup( "sn" );

        // postive test which should pass
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
            new DefaultServerAttribute( "cn", cnAt, "does not matter" ), registry );

        // test should fail since we are removing the ou attribute
        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
                new DefaultServerAttribute( "ou", ouAt ), registry );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // test success using more than one attribute for the Rdn but not modifying rdn attribute
        name = new LdapDN( "ou=users+cn=system users,dc=example,dc=com" );
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
            new DefaultServerAttribute( "sn", snAt, "does not matter" ), registry );

        // test for failure when modifying Rdn attribute in multi attribute Rdn
        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
                new DefaultServerAttribute( "cn", cnAt ), registry );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }

        // should succeed since the value being deleted from the rdn attribute is
        // is not used when composing the Rdn
        SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
            new DefaultServerAttribute( "ou", ouAt, "container" ), registry );

        // now let's make it fail again just by providing the right value for ou (users)
        try
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, mod, 
                new DefaultServerAttribute( "ou", ouAt, "users" ), registry );
            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
        }
        catch ( LdapSchemaViolationException e )
        {
            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
        }
    }


//    /**
//     * Test case to check the schema checker operates correctly when modify
//     * operations replace RDN attributes.
//     */
//    public void testPreventRdnChangeOnModifyReplaceAttribute() throws Exception
//    {
//        int mod = DirContext.REPLACE_ATTRIBUTE;
//        LdapDN name = new LdapDN( "ou=user,dc=example,dc=com" );
//
//        // postive test which should pass
//        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, new AttributeImpl( "cn", "does not matter" ), registries.getOidRegistry() );
//
//        // test should fail since we are removing the ou attribute
//        try
//        {
//            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, new AttributeImpl( "ou" ), registries.getOidRegistry() );
//            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
//        }
//        catch ( LdapSchemaViolationException e )
//        {
//            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
//        }
//
//        // test success using more than one attribute for the Rdn but not modifying rdn attribute
//        name = new LdapDN( "ou=users+cn=system users,dc=example,dc=com" );
//        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, new AttributeImpl( "sn", "does not matter" ), registries.getOidRegistry() );
//
//        // test for failure when modifying Rdn attribute in multi attribute Rdn
//        try
//        {
//            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, new AttributeImpl( "cn" ), registries.getOidRegistry() );
//            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
//        }
//        catch ( LdapSchemaViolationException e )
//        {
//            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
//        }
//
//        // should succeed since the values being replaced from the rdn attribute is
//        // is includes the old Rdn attribute value
//        Attribute attribute = new AttributeImpl( "ou" );
//        attribute.add( "container" );
//        attribute.add( "users" );
//        SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attribute, registries.getOidRegistry() );
//
//        // now let's make it fail by not including the old value for ou (users)
//        attribute = new AttributeImpl( "ou" );
//        attribute.add( "container" );
//        try
//        {
//            SchemaChecker.preventRdnChangeOnModifyReplace( name, mod, attribute, registries.getOidRegistry() );
//            fail( "should never get here due to a LdapSchemaViolationException being thrown" );
//        }
//        catch ( LdapSchemaViolationException e )
//        {
//            assertEquals( ResultCodeEnum.NOT_ALLOWED_ON_RDN, e.getResultCode() );
//        }
//    }


    class MockOidRegistry implements OidRegistry
    {
        public String getOid( String name ) throws NamingException
        {
            return StringTools.deepTrimToLower( name );
        }

        public boolean hasOid( String id )
        {
            return true;
        }

        public String getPrimaryName( String oid ) throws NamingException
        {
            return oid;
        }

        public List<String> getNameSet( String oid ) throws NamingException
        {
            return Collections.singletonList( oid );
        }

        public Iterator<?> list()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public void register( String name, String oid )
        {
        }

        public Map<String,String> getOidByName()
        {
            return null;
        }

        public Map<String,String> getNameByOid()
        {
            return null;
        }

        public void unregister( String numericOid ) throws NamingException
        {
        }
    }
}
