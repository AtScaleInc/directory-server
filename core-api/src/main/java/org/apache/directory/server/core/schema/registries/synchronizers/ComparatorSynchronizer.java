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
package org.apache.directory.server.core.schema.registries.synchronizers;


import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapInvalidNameException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.LdapComparator;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A handler for operations performed to add, delete, modify, rename and 
 * move schema comparators.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ComparatorSynchronizer extends AbstractRegistrySynchronizer
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( ComparatorSynchronizer.class );


    /**
     * Creates a new instance of ComparatorSynchronizer.
     *
     * @param schemaManager The global schemaManager
     * @throws Exception If the initialization failed
     */
    public ComparatorSynchronizer( SchemaManager schemaManager ) throws Exception
    {
        super( schemaManager );
    }


    /**
     * {@inheritDoc}
     */
    public boolean modify( ModifyOperationContext opContext, ServerEntry targetEntry, boolean cascade )
        throws Exception
    {
        LdapDN name = opContext.getDn();
        ServerEntry entry = opContext.getEntry();
        String schemaName = getSchemaName( name );
        String oid = getOid( entry );
        LdapComparator<?> comparator = factory.getLdapComparator( schemaManager, targetEntry, schemaManager
            .getRegistries(), schemaName );

        if ( isSchemaEnabled( schemaName ) )
        {
            comparator.setSchemaName( schemaName );

            schemaManager.unregisterComparator( oid );
            schemaManager.add( comparator );

            return SCHEMA_MODIFIED;
        }

        return SCHEMA_UNCHANGED;
    }


    /**
     * {@inheritDoc}
     */
    public void add( ServerEntry entry ) throws Exception
    {
        LdapDN dn = entry.getDn();
        LdapDN parentDn = ( LdapDN ) dn.clone();
        parentDn.remove( parentDn.size() - 1 );

        // The parent DN must be ou=comparators,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.COMPARATOR );

        // The new schemaObject's OID must not already exist
        checkOidIsUniqueForComparator( entry );

        // Build the new Comparator from the given entry
        String schemaName = getSchemaName( dn );

        LdapComparator<?> comparator = factory.getLdapComparator( schemaManager, entry, schemaManager.getRegistries(),
            schemaName );

        // At this point, the constructed LdapComparator has not been checked against the 
        // existing Registries. It will be checked there, if the schema and the 
        // LdapComparator are both enabled.
        Schema schema = schemaManager.getLoadedSchema( schemaName );

        if ( schema.isEnabled() && comparator.isEnabled() )
        {
            if ( schemaManager.add( comparator ) )
            {
                LOG.debug( "Added {} into the enabled schema {}", dn.getName(), schemaName );
            }
            else
            {
                // We have some error : reject the addition and get out
                String msg = "Cannot add the Comparator " + entry.getDn().getName() + " into the registries, "
                    + "the resulting registries would be inconsistent :" + 
                    StringTools.listToString( schemaManager.getErrors() );
                LOG.info( msg );
                throw new LdapOperationNotSupportedException( msg, ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
        }
        else
        {
            LOG.debug( "The Comparator {} cannot be added in the disabled schema {}", dn.getName(), schemaName );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void delete( ServerEntry entry, boolean cascade ) throws Exception
    {
        LdapDN dn = entry.getDn();
        LdapDN parentDn = ( LdapDN ) dn.clone();
        parentDn.remove( parentDn.size() - 1 );

        // The parent DN must be ou=comparators,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.COMPARATOR );

        // Get the SchemaName
        String schemaName = getSchemaName( entry.getDn() );

        // Get the Schema
        Schema schema = schemaManager.getLoadedSchema( schemaName );

        if ( schema.isDisabled() )
        {
            // The schema is disabled, nothing to do.
            LOG.debug( "The Comparator {} cannot be deleted from the disabled schema {}", dn.getName(), schemaName );
            
            return;
        }

        // Test that the Oid exists
        LdapComparator<?> comparator = null;

        try
        {
            comparator = ( LdapComparator<?> ) checkComparatorOidExists( entry );
        }
        catch ( LdapSchemaViolationException lsve )
        {
            // The comparator does not exist
            comparator = factory.getLdapComparator( schemaManager, entry, schemaManager.getRegistries(), schemaName );

            if ( schemaManager.getRegistries().contains( comparator ) )
            {
                // Remove the Comparator from the schema/SchemaObject Map
                schemaManager.getRegistries().dissociateFromSchema( comparator );

                // Ok, we can exit. 
                return;
            }
            else
            {
                // Ok, definitively an error
                String msg = "Cannot delete the Comparator " + entry.getDn().getName() + " as it "
                    + "does not exist in any schema";
                LOG.info( msg );
                throw new LdapSchemaViolationException( msg, ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
        }

        List<Throwable> errors = new ArrayList<Throwable>();

        if ( schema.isEnabled() && comparator.isEnabled() )
        {
            if ( schemaManager.delete( comparator ) )
            {
                LOG.debug( "Deleted {} from the enabled schema {}", dn.getName(), schemaName );
            }
            else
            {
                String msg = "Cannot delete the Comparator " + entry.getDn().getName() + " into the registries, "
                    + "the resulting registries would be inconsistent :" + StringTools.listToString( errors );
                LOG.info( msg );
                throw new LdapOperationNotSupportedException( msg, ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
        }
        else
        {
            LOG.debug( "The Comparator {} cannot be deleted from the disabled schema {}", dn.getName(), schemaName );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void rename( ServerEntry entry, Rdn newRdn, boolean cascade ) throws Exception
    {
        String oldOid = getOid( entry );

        if ( schemaManager.getMatchingRuleRegistry().contains( oldOid ) )
        {
            throw new LdapOperationNotSupportedException(
                "The comparator with OID " + oldOid + " cannot have it's OID changed until all "
                    + "matchingRules using that comparator have been deleted.", ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        String oid = ( String ) newRdn.getNormValue();
        checkOidIsUniqueForComparator( oid );

        String schemaName = getSchemaName( entry.getDn() );

        if ( isSchemaEnabled( schemaName ) )
        {
            // Inject the new OID in the entry
            ServerEntry targetEntry = ( ServerEntry ) entry.clone();
            String newOid = ( String ) newRdn.getNormValue();
            checkOidIsUnique( newOid );
            targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );

            // Inject the new DN
            LdapDN newDn = new LdapDN( targetEntry.getDn() );
            newDn.remove( newDn.size() - 1 );
            newDn.add( newRdn );
            targetEntry.setDn( newDn );

            // Register the new comparator, and unregister the old one
            LdapComparator<?> comparator = factory.getLdapComparator( schemaManager, targetEntry, schemaManager
                .getRegistries(), schemaName );
            schemaManager.unregisterComparator( oldOid );
            schemaManager.add( comparator );
        }
    }


    public void moveAndRename( LdapDN oriChildName, LdapDN newParentName, Rdn newRdn, boolean deleteOldRn,
        ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oldOid = getOid( entry );

        if ( schemaManager.getMatchingRuleRegistry().contains( oldOid ) )
        {
            throw new LdapOperationNotSupportedException(
                "The comparator with OID " + oldOid + " cannot have it's OID changed until all "
                    + "matchingRules using that comparator have been deleted.", ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        String oid = ( String ) newRdn.getNormValue();
        checkOidIsUniqueForComparator( oid );

        String newSchemaName = getSchemaName( newParentName );

        LdapComparator<?> comparator = factory.getLdapComparator( schemaManager, entry, schemaManager.getRegistries(),
            newSchemaName );

        String oldSchemaName = getSchemaName( oriChildName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterComparator( oldOid );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( comparator );
        }
    }


    public void move( LdapDN oriChildName, LdapDN newParentName, ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oid = getOid( entry );

        if ( schemaManager.getMatchingRuleRegistry().contains( oid ) )
        {
            throw new LdapOperationNotSupportedException( "The comparator with OID " + oid
                + " cannot be moved to another schema until all "
                + "matchingRules using that comparator have been deleted.", ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        String newSchemaName = getSchemaName( newParentName );

        LdapComparator<?> comparator = factory.getLdapComparator( schemaManager, entry, schemaManager.getRegistries(),
            newSchemaName );

        String oldSchemaName = getSchemaName( oriChildName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterComparator( oid );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( comparator );
        }
    }


    private void checkOidIsUniqueForComparator( String oid ) throws NamingException
    {
        if ( schemaManager.getComparatorRegistry().contains( oid ) )
        {
            throw new LdapNamingException( "Oid " + oid + " for new schema comparator is not unique.",
                ResultCodeEnum.OTHER );
        }
    }


    private void checkOidIsUniqueForComparator( ServerEntry entry ) throws Exception
    {
        String oid = getOid( entry );

        if ( schemaManager.getComparatorRegistry().contains( oid ) )
        {
            throw new LdapNamingException( "Oid " + oid + " for new schema comparator is not unique.",
                ResultCodeEnum.OTHER );
        }
    }


    /**
     * Check that a Comparator exists in the ComparatorRegistry, and if so,
     * return it.
     */
    protected LdapComparator<?> checkComparatorOidExists( ServerEntry entry ) throws Exception
    {
        String oid = getOid( entry );

        if ( schemaManager.getComparatorRegistry().contains( oid ) )
        {
            return ( LdapComparator<?> ) schemaManager.getComparatorRegistry().get( oid );
        }
        else
        {
            throw new LdapSchemaViolationException( "Oid " + oid + " for new schema entity does not exist.",
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

        if ( !schemaManager.getAttributeTypeRegistry().getOidByName( rdn.getNormType() ).equals(
            SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidNameException( "The parent entry of a comparator should be an organizationalUnit.",
                ResultCodeEnum.NAMING_VIOLATION );
        }

        if ( !( ( String ) rdn.getNormValue() ).equalsIgnoreCase( SchemaConstants.COMPARATORS_AT ) )
        {
            throw new LdapInvalidNameException(
                "The parent entry of a comparator should have a relative name of ou=comparators.",
                ResultCodeEnum.NAMING_VIOLATION );
        }
    }
}
