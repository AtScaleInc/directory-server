/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.schema;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.apache.directory.server.constants.MetaSchemaConstants;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.registries.ComparatorRegistry;
import org.apache.directory.server.schema.registries.MatchingRuleRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapInvalidNameException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.syntax.ComparatorDescription;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.Base64;
import org.apache.directory.shared.ldap.util.NamespaceTools;


/**
 * A handler for operations peformed to add, delete, modify, rename and 
 * move schema comparators.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MetaComparatorHandler extends AbstractSchemaChangeHandler
{
    private final SchemaEntityFactory factory;
    private final ComparatorRegistry comparatorRegistry;
    private final MatchingRuleRegistry matchingRuleRegistry;
    private final AttributeType byteCodeAT;
    private final AttributeType descAT;
    private final AttributeType fqcnAT;

    

    public MetaComparatorHandler( Registries targetRegistries, PartitionSchemaLoader loader ) throws NamingException
    {
        super( targetRegistries, loader );
        this.comparatorRegistry = targetRegistries.getComparatorRegistry();
        this.matchingRuleRegistry = targetRegistries.getMatchingRuleRegistry();
        this.factory = new SchemaEntityFactory( targetRegistries );
        this.byteCodeAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_BYTECODE_AT );
        this.descAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_DESCRIPTION_AT );
        this.fqcnAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_FQCN_AT );
    }

    
    protected void modify( LdapDN name, Attributes entry, Attributes targetEntry, boolean cascade ) throws NamingException
    {
        String oid = getOid( entry );
        Comparator comparator = factory.getComparator( targetEntry, targetRegistries );
        Schema schema = getSchema( name );
        
        if ( ! schema.isDisabled() )
        {
            comparatorRegistry.unregister( oid );
            ComparatorDescription description = getComparatorDescription( schema.getSchemaName(), targetEntry );
            comparatorRegistry.register( description, comparator );
        }
    }

    
    private ComparatorDescription getComparatorDescription( String schemaName, Attributes entry ) throws NamingException
    {
        ComparatorDescription description = new ComparatorDescription();
        description.setNumericOid( getOid( entry ) );
        List<String> values = new ArrayList<String>();
        values.add( schemaName );
        description.addExtension( MetaSchemaConstants.X_SCHEMA, values );
        description.setFqcn( ( String ) AttributeUtils.getAttribute( entry, fqcnAT ).get() );
        
        Attribute desc = AttributeUtils.getAttribute( entry, descAT );
        if ( desc != null && desc.size() > 0 )
        {
            description.setDescription( ( String ) desc.get() );
        }
        
        Attribute bytecode = AttributeUtils.getAttribute( entry, byteCodeAT );
        if ( bytecode != null && bytecode.size() > 0 )
        {
            byte[] bytes = ( byte[] ) bytecode.get();
            description.setBytecode( new String( Base64.encode( bytes ) ) );
        }

        return description;
    }
    

    public void add( LdapDN name, Attributes entry ) throws NamingException
    {
        LdapDN parentDn = ( LdapDN ) name.clone();
        parentDn.remove( parentDn.size() - 1 );
        checkNewParent( parentDn );
        checkOidIsUniqueForComparator( entry );
        
        Comparator comparator = factory.getComparator( entry, targetRegistries );
        Schema schema = getSchema( name );
        
        if ( ! schema.isDisabled() )
        {
            ComparatorDescription comparatorDescription = getComparatorDescription( schema.getSchemaName(), entry );
            comparatorRegistry.register( comparatorDescription, comparator );
        }
    }
    
    
    public void add( ComparatorDescription comparatorDescription ) throws NamingException
    {
        Comparator comparator = factory.getComparator( comparatorDescription, targetRegistries );
        String schemaName = MetaSchemaConstants.SCHEMA_OTHER;
        
        if ( comparatorDescription.getExtensions().get( MetaSchemaConstants.X_SCHEMA ) != null )
        {
            schemaName = comparatorDescription.getExtensions().get( MetaSchemaConstants.X_SCHEMA ).get( 0 );
        }
        
        Schema schema = loader.getSchema( schemaName );
        
        if ( ! schema.isDisabled() )
        {
            comparatorRegistry.register( comparatorDescription, comparator );
        }
    }


    public void delete( LdapDN name, Attributes entry, boolean cascade ) throws NamingException
    {
        String oid = getOid( entry );
        delete( oid, cascade );
    }


    public void delete( String oid, boolean cascade ) throws NamingException
    {
        if ( matchingRuleRegistry.hasMatchingRule( oid ) )
        {
            throw new LdapOperationNotSupportedException( "The comparator with OID " + oid 
                + " cannot be deleted until all " 
                + "matchingRules using that comparator have also been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        if ( comparatorRegistry.hasComparator( oid ) )
        {
            comparatorRegistry.unregister( oid );
        }
    }

    
    public void rename( LdapDN name, Attributes entry, String newRdn, boolean cascade ) throws NamingException
    {
        String oldOid = getOid( entry );

        if ( matchingRuleRegistry.hasMatchingRule( oldOid ) )
        {
            throw new LdapOperationNotSupportedException( "The comparator with OID " + oldOid 
                + " cannot have it's OID changed until all " 
                + "matchingRules using that comparator have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        String oid = NamespaceTools.getRdnValue( newRdn );
        checkOidIsUniqueForComparator( oid );
        Schema schema = getSchema( name );
        
        if ( ! schema.isDisabled() )
        {
            Comparator comparator = factory.getComparator( entry, targetRegistries );
            comparatorRegistry.unregister( oldOid );
            ComparatorDescription comparatorDescription = getComparatorDescription( schema.getSchemaName(), entry );
            comparatorDescription.setNumericOid( oid );
            comparatorRegistry.register( comparatorDescription, comparator );
        }
    }


    public void move( LdapDN oriChildName, LdapDN newParentName, String newRn, boolean deleteOldRn, 
        Attributes entry, boolean cascade ) throws NamingException
    {
        checkNewParent( newParentName );
        String oldOid = getOid( entry );

        if ( matchingRuleRegistry.hasMatchingRule( oldOid ) )
        {
            throw new LdapOperationNotSupportedException( "The comparator with OID " + oldOid 
                + " cannot have it's OID changed until all " 
                + "matchingRules using that comparator have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        String oid = NamespaceTools.getRdnValue( newRn );
        checkOidIsUniqueForComparator( oid );
        
        Schema oldSchema = getSchema( oriChildName );
        Schema newSchema = getSchema( newParentName );
        
        Comparator comparator = factory.getComparator( entry, targetRegistries );

        if ( ! oldSchema.isDisabled() )
        {
            comparatorRegistry.unregister( oldOid );
        }

        if ( ! newSchema.isDisabled() )
        {
            ComparatorDescription comparatorDescription = getComparatorDescription( newSchema.getSchemaName(), entry );
            comparatorDescription.setNumericOid( oid );
            comparatorRegistry.register( comparatorDescription, comparator );
        }
    }


    public void replace( LdapDN oriChildName, LdapDN newParentName, Attributes entry, boolean cascade ) 
        throws NamingException
    {
        checkNewParent( newParentName );
        String oid = getOid( entry );

        if ( matchingRuleRegistry.hasMatchingRule( oid ) )
        {
            throw new LdapOperationNotSupportedException( "The comparator with OID " + oid 
                + " cannot be moved to another schema until all " 
                + "matchingRules using that comparator have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema oldSchema = getSchema( oriChildName );
        Schema newSchema = getSchema( newParentName );
        
        Comparator comparator = factory.getComparator( entry, targetRegistries );
        
        if ( ! oldSchema.isDisabled() )
        {
            comparatorRegistry.unregister( oid );
        }
        
        if ( ! newSchema.isDisabled() )
        {
            ComparatorDescription comparatorDescription = getComparatorDescription( newSchema.getSchemaName(), entry );
            comparatorRegistry.register( comparatorDescription, comparator );
        }
    }
    
    
    private void checkOidIsUniqueForComparator( String oid ) throws NamingException
    {
        if ( super.targetRegistries.getComparatorRegistry().hasComparator( oid ) )
        {
            throw new LdapNamingException( "Oid " + oid + " for new schema comparator is not unique.", 
                ResultCodeEnum.OTHER );
        }
    }


    private void checkOidIsUniqueForComparator( Attributes entry ) throws NamingException
    {
        String oid = getOid( entry );
        
        if ( super.targetRegistries.getComparatorRegistry().hasComparator( oid ) )
        {
            throw new LdapNamingException( "Oid " + oid + " for new schema comparator is not unique.", 
                ResultCodeEnum.OTHER );
        }
    }


    private void checkNewParent( LdapDN newParent ) throws NamingException
    {
        if ( newParent.size() != 3 )
        {
            throw new LdapInvalidNameException( 
                "The parent dn of a comparator should be at most 3 name components in length.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        Rdn rdn = newParent.getRdn();
        if ( ! targetRegistries.getOidRegistry().getOid( rdn.getNormType() ).equals( SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidNameException( "The parent entry of a comparator should be an organizationalUnit.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        if ( ! ( ( String ) rdn.getValue() ).equalsIgnoreCase( SchemaConstants.COMPARATORS_AT ) )
        {
            throw new LdapInvalidNameException( 
                "The parent entry of a comparator should have a relative name of ou=comparators.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
    }
}
