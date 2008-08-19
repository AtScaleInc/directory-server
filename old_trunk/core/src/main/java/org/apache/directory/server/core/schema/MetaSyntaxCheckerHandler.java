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
import java.util.List;

import javax.naming.NamingException;

import org.apache.directory.server.constants.MetaSchemaConstants;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.server.schema.registries.SyntaxCheckerRegistry;
import org.apache.directory.server.schema.registries.SyntaxRegistry;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.exception.LdapInvalidNameException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.syntax.SyntaxChecker;
import org.apache.directory.shared.ldap.schema.syntax.SyntaxCheckerDescription;
import org.apache.directory.shared.ldap.util.Base64;


/**
 * A handler for operations peformed to add, delete, modify, rename and 
 * move schema normalizers.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MetaSyntaxCheckerHandler extends AbstractSchemaChangeHandler
{
    private final SchemaEntityFactory factory;
    private final SyntaxCheckerRegistry syntaxCheckerRegistry;
    private final SyntaxRegistry syntaxRegistry;
    private final AttributeType byteCodeAT;
    private final AttributeType descAT;
    private final AttributeType fqcnAT;
    

    public MetaSyntaxCheckerHandler( Registries targetRegistries, PartitionSchemaLoader loader ) throws NamingException
    {
        super( targetRegistries, loader );
        this.syntaxCheckerRegistry = targetRegistries.getSyntaxCheckerRegistry();
        this.syntaxRegistry = targetRegistries.getSyntaxRegistry();
        this.factory = new SchemaEntityFactory( targetRegistries );
        this.byteCodeAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_BYTECODE_AT );
        this.descAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_DESCRIPTION_AT );
        this.fqcnAT = targetRegistries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_FQCN_AT );
    }


    private SyntaxCheckerDescription getSyntaxCheckerDescription( String schemaName, ServerEntry entry ) 
        throws NamingException
    {
        SyntaxCheckerDescription description = new SyntaxCheckerDescription();
        description.setNumericOid( getOid( entry ) );
        List<String> values = new ArrayList<String>();
        values.add( schemaName );
        description.addExtension( MetaSchemaConstants.X_SCHEMA, values );
        description.setFqcn( entry.get( fqcnAT ).getString() );
        
        EntryAttribute desc = entry.get( descAT );
        
        if ( desc != null && desc.size() > 0 )
        {
            description.setDescription( desc.getString() );
        }
        
        EntryAttribute bytecode = entry.get( byteCodeAT );
        
        if ( bytecode != null && bytecode.size() > 0 )
        {
            byte[] bytes = bytecode.getBytes();
            description.setBytecode( new String( Base64.encode( bytes ) ) );
        }

        return description;
    }

    
    protected void modify( LdapDN name, ServerEntry entry, ServerEntry targetEntry, boolean cascade ) throws NamingException
    {
        String oid = getOid( entry );
        SyntaxChecker syntaxChecker = factory.getSyntaxChecker( targetEntry, targetRegistries );
        
        Schema schema = getSchema( name );
        
        if ( ! schema.isDisabled() )
        {
            syntaxCheckerRegistry.unregister( oid );
            SyntaxCheckerDescription syntaxCheckerDescription = 
                getSyntaxCheckerDescription( schema.getSchemaName(), targetEntry );
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }


    public void add( LdapDN name, ServerEntry entry ) throws NamingException
    {
        LdapDN parentDn = ( LdapDN ) name.clone();
        parentDn.remove( parentDn.size() - 1 );
        checkNewParent( parentDn );
        String oid = getOid( entry );
        if ( super.targetRegistries.getSyntaxCheckerRegistry().hasSyntaxChecker( oid ) )
        {
            throw new LdapNamingException( "Oid " + oid + " for new schema syntaxChecker is not unique.", 
                ResultCodeEnum.OTHER );
        }
        
        SyntaxChecker syntaxChecker = factory.getSyntaxChecker( entry, targetRegistries );
        Schema schema = getSchema( name );
        
        if ( ! schema.isDisabled() )
        {
            SyntaxCheckerDescription syntaxCheckerDescription = 
                getSyntaxCheckerDescription( schema.getSchemaName(), entry );
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }


    public void add( SyntaxCheckerDescription syntaxCheckerDescription ) throws NamingException
    {
        SyntaxChecker syntaxChecker = factory.getSyntaxChecker( syntaxCheckerDescription, targetRegistries );
        String schemaName = MetaSchemaConstants.SCHEMA_OTHER;
        
        if ( syntaxCheckerDescription.getExtensions().get( MetaSchemaConstants.X_SCHEMA ) != null )
        {
            schemaName = syntaxCheckerDescription.getExtensions()
                .get( MetaSchemaConstants.X_SCHEMA ).get( 0 );
        }
        
        Schema schema = loader.getSchema( schemaName );
        
        if ( ! schema.isDisabled() )
        {
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }


    public void delete( LdapDN name, ServerEntry entry, boolean cascade ) throws NamingException
    {
        delete( getOid( entry ), cascade );
    }


    public void delete( String oid, boolean cascade ) throws NamingException
    {
        if ( syntaxRegistry.hasSyntax( oid ) )
        {
            throw new LdapOperationNotSupportedException( "The syntaxChecker with OID " + oid 
                + " cannot be deleted until all " 
                + "syntaxes using this syntaxChecker have also been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        if ( syntaxCheckerRegistry.hasSyntaxChecker( oid ) )
        {
            syntaxCheckerRegistry.unregister( oid );
        }
    }


    public void rename( LdapDN name, ServerEntry entry, Rdn newRdn, boolean cascade ) throws NamingException
    {
        String oldOid = getOid( entry );

        if ( syntaxRegistry.hasSyntax( oldOid ) )
        {
            throw new LdapOperationNotSupportedException( "The syntaxChecker with OID " + oldOid 
                + " cannot have it's OID changed until all " 
                + "syntaxes using that syntaxChecker have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema schema = getSchema( name );
        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        String newOid = ( String ) newRdn.getValue();
        if ( super.targetRegistries.getSyntaxCheckerRegistry().hasSyntaxChecker( newOid ) )
        {
            throw new LdapNamingException( "Oid " + newOid + " for new schema syntaxChecker is not unique.", 
                ResultCodeEnum.OTHER );
        }

        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );
        if ( ! schema.isDisabled() )
        {
            SyntaxChecker syntaxChecker = factory.getSyntaxChecker( targetEntry, targetRegistries );
            syntaxCheckerRegistry.unregister( oldOid );
            SyntaxCheckerDescription syntaxCheckerDescription = 
                getSyntaxCheckerDescription( schema.getSchemaName(), entry );
            syntaxCheckerDescription.setNumericOid( newOid );
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }


    public void move( LdapDN oriChildName, LdapDN newParentName, Rdn newRdn, boolean deleteOldRn, 
        ServerEntry entry, boolean cascade ) throws NamingException
    {
        checkNewParent( newParentName );
        String oldOid = getOid( entry );

        if ( syntaxRegistry.hasSyntax( oldOid ) )
        {
            throw new LdapOperationNotSupportedException( "The syntaxChecker with OID " + oldOid 
                + " cannot have it's OID changed until all " 
                + "syntaxes using that syntaxChecker have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema oldSchema = getSchema( oriChildName );
        Schema newSchema = getSchema( newParentName );
        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        
        String newOid = ( String ) newRdn.getValue();
        if ( super.targetRegistries.getSyntaxCheckerRegistry().hasSyntaxChecker( newOid ) )
        {
            throw new LdapNamingException( "Oid " + newOid + " for new schema syntaxChecker is not unique.", 
                ResultCodeEnum.OTHER );
        }

        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );
        SyntaxChecker syntaxChecker = factory.getSyntaxChecker( targetEntry, targetRegistries );

        if ( ! oldSchema.isDisabled() )
        {
            syntaxCheckerRegistry.unregister( oldOid );
        }

        if ( ! newSchema.isDisabled() )
        {
            SyntaxCheckerDescription syntaxCheckerDescription = 
                getSyntaxCheckerDescription( newSchema.getSchemaName(), entry );
            syntaxCheckerDescription.setNumericOid( newOid );
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }


    public void replace( LdapDN oriChildName, LdapDN newParentName, ServerEntry entry, boolean cascade ) 
        throws NamingException
    {
        checkNewParent( newParentName );
        String oid = getOid( entry );

        if ( syntaxRegistry.hasSyntax( oid ) )
        {
            throw new LdapOperationNotSupportedException( "The syntaxChecker with OID " + oid 
                + " cannot be moved to another schema until all " 
                + "syntax using that syntaxChecker have been deleted.", 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema oldSchema = getSchema( oriChildName );
        Schema newSchema = getSchema( newParentName );
        
        SyntaxChecker syntaxChecker = factory.getSyntaxChecker( entry, targetRegistries );
        
        if ( ! oldSchema.isDisabled() )
        {
            syntaxCheckerRegistry.unregister( oid );
        }
        
        if ( ! newSchema.isDisabled() )
        {
            SyntaxCheckerDescription syntaxCheckerDescription = 
                getSyntaxCheckerDescription( newSchema.getSchemaName(), entry );
            syntaxCheckerRegistry.register( syntaxCheckerDescription, syntaxChecker );
        }
    }
    
    
    private void checkNewParent( LdapDN newParent ) throws NamingException
    {
        if ( newParent.size() != 3 )
        {
            throw new LdapInvalidNameException( 
                "The parent dn of a syntaxChecker should be at most 3 name components in length.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        Rdn rdn = newParent.getRdn();
        if ( ! targetRegistries.getOidRegistry().getOid( rdn.getNormType() ).equals( SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidNameException( "The parent entry of a syntaxChecker should be an organizationalUnit.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        if ( ! ( ( String ) rdn.getValue() ).equalsIgnoreCase( SchemaConstants.SYNTAX_CHECKERS_AT ) )
        {
            throw new LdapInvalidNameException( 
                "The parent entry of a normalizer should have a relative name of ou=syntaxCheckers.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
    }
}
