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


import java.util.Set;

import javax.naming.NamingException;

import org.apache.directory.server.constants.MetaSchemaConstants;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.registries.MatchingRuleRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapInvalidNameException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.MatchingRule;


/**
 * A handler for operations peformed to add, delete, modify, rename and 
 * move schema normalizers.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MetaMatchingRuleHandler extends AbstractSchemaChangeHandler
{
    private final SchemaPartitionDao dao;
    private final MatchingRuleRegistry matchingRuleRegistry;

    
    public MetaMatchingRuleHandler( Registries targetRegistries, PartitionSchemaLoader loader, SchemaPartitionDao dao ) 
        throws Exception
    {
        super( targetRegistries, loader );
        
        this.dao = dao;
        this.matchingRuleRegistry = targetRegistries.getMatchingRuleRegistry();
    }


    protected boolean modify( LdapDN name, ServerEntry entry, ServerEntry targetEntry, 
        boolean cascade ) throws Exception
    {
        String oid = getOid( entry );
        Schema schema = getSchema( name );
        MatchingRule mr = factory.getMatchingRule( targetEntry, targetRegistries, schema.getSchemaName() );
        
        if ( ! schema.isDisabled() )
        {
            matchingRuleRegistry.unregister( oid );
            matchingRuleRegistry.register( mr );
            
            return SCHEMA_MODIFIED;
        }
        
        return SCHEMA_UNCHANGED;
    }


    public void add( LdapDN name, ServerEntry entry ) throws Exception
    {
        LdapDN parentDn = ( LdapDN ) name.clone();
        parentDn.remove( parentDn.size() - 1 );
        checkNewParent( parentDn );
        checkOidIsUnique( entry );
        
        String schemaName = getSchemaName( name );
        MatchingRule mr = factory.getMatchingRule( entry, targetRegistries, schemaName );
        add( mr );
    }


    public void delete( LdapDN name, ServerEntry entry, boolean cascade ) throws Exception
    {
        String schemaName = getSchemaName( name );
        MatchingRule mr = factory.getMatchingRule( entry, targetRegistries, schemaName );
        Set<ServerEntry> dependees = dao.listMatchingRuleDependents( mr );
        
        if ( dependees != null && dependees.size() > 0 )
        {
            throw new LdapOperationNotSupportedException( "The matchingRule with OID " + mr.getOid() 
                + " cannot be deleted until all entities" 
                + " using this matchingRule have also been deleted.  The following dependees exist: " 
                + getOids( dependees ), 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        delete( mr, cascade );
    }


    public void delete( MatchingRule mr, boolean cascade ) throws Exception
    {
        Schema schema = loader.getSchema( mr.getSchema() );
        if ( ! schema.isDisabled() )
        {
            matchingRuleRegistry.unregister( mr.getOid() );
        }
        unregisterOids( mr.getOid() );
    }

    
    public void rename( LdapDN name, ServerEntry entry, Rdn newRdn, boolean cascade ) throws Exception
    {
        Schema schema = getSchema( name );
        MatchingRule oldMr = factory.getMatchingRule( entry, targetRegistries, schema.getSchemaName() );
        Set<ServerEntry> dependees = dao.listMatchingRuleDependents( oldMr );
        
        if ( dependees != null && dependees.size() > 0 )
        {
            throw new LdapOperationNotSupportedException( "The matchingRule with OID " + oldMr.getOid()
                + " cannot be deleted until all entities" 
                + " using this matchingRule have also been deleted.  The following dependees exist: " 
                + getOids( dependees ), 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        String newOid = ( String ) newRdn.getValue();
        checkOidIsUnique( newOid );
        
        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );
        MatchingRule mr = factory.getMatchingRule( targetEntry, targetRegistries, schema.getSchemaName() );

        if ( ! schema.isDisabled() )
        {
            matchingRuleRegistry.unregister( oldMr.getOid() );
            matchingRuleRegistry.register( mr );
        }
        else
        {
            registerOids( mr );
        }

        unregisterOids( oldMr.getOid() );
    }


    public void move( LdapDN oriChildName, LdapDN newParentName, Rdn newRdn, boolean deleteOldRn, 
        ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        Schema oldSchema = getSchema( oriChildName );
        MatchingRule oldMr = factory.getMatchingRule( entry, targetRegistries, oldSchema.getSchemaName() );
        Set<ServerEntry> dependees = dao.listMatchingRuleDependents( oldMr );
        
        if ( dependees != null && dependees.size() > 0 )
        {
            throw new LdapOperationNotSupportedException( "The matchingRule with OID " + oldMr.getOid()
                + " cannot be deleted until all entities" 
                + " using this matchingRule have also been deleted.  The following dependees exist: " 
                + getOids( dependees ), 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema newSchema = getSchema( newParentName );
        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        String newOid = ( String ) newRdn.getValue();
        checkOidIsUnique( newOid );
        
        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );
        MatchingRule mr = factory.getMatchingRule( targetEntry, targetRegistries, newSchema.getSchemaName() );

        if ( ! oldSchema.isDisabled() )
        {
            matchingRuleRegistry.unregister( oldMr.getOid() );
        }
        unregisterOids( oldMr.getOid() );

        if ( ! newSchema.isDisabled() )
        {
            matchingRuleRegistry.register( mr );
        }
        else
        {
            registerOids( mr );
        }
    }


    public void replace( LdapDN oriChildName, LdapDN newParentName, ServerEntry entry, boolean cascade ) 
        throws Exception
    {
        checkNewParent( newParentName );
        Schema oldSchema = getSchema( oriChildName );
        MatchingRule oldMr = factory.getMatchingRule( entry, targetRegistries, oldSchema.getSchemaName() );
        Set<ServerEntry> dependees = dao.listMatchingRuleDependents( oldMr );
        
        if ( dependees != null && dependees.size() > 0 )
        {
            throw new LdapOperationNotSupportedException( "The matchingRule with OID " + oldMr.getOid() 
                + " cannot be deleted until all entities" 
                + " using this matchingRule have also been deleted.  The following dependees exist: " 
                + getOids( dependees ), 
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        Schema newSchema = getSchema( newParentName );
        MatchingRule mr = factory.getMatchingRule( entry, targetRegistries, newSchema.getSchemaName() );
        
        if ( ! oldSchema.isDisabled() )
        {
            matchingRuleRegistry.unregister( oldMr.getOid() );
        }
        
        if ( ! newSchema.isDisabled() )
        {
            matchingRuleRegistry.register( mr );
        }
    }
    
    
    private void checkNewParent( LdapDN newParent ) throws NamingException
    {
        if ( newParent.size() != 3 )
        {
            throw new LdapInvalidNameException( 
                "The parent dn of a matchingRule should be at most 3 name components in length.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        Rdn rdn = newParent.getRdn();
        if ( ! targetRegistries.getOidRegistry().getOid( rdn.getNormType() ).equals( SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidNameException( "The parent entry of a matchingRule should be an organizationalUnit.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
        
        if ( ! ( ( String ) rdn.getValue() ).equalsIgnoreCase( SchemaConstants.MATCHING_RULES_AT ) )
        {
            throw new LdapInvalidNameException( 
                "The parent entry of a syntax should have a relative name of ou=matchingRules.", 
                ResultCodeEnum.NAMING_VIOLATION );
        }
    }


    public void add( MatchingRule mr ) throws Exception
    {
        Schema schema = loader.getSchema( mr.getSchema() );
        
        if ( ! schema.isDisabled() )
        {
            matchingRuleRegistry.register( mr );
        }
        else
        {
            registerOids( mr );
        }
    }
}
