/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.schema;


import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.DITContentRule;
import org.apache.directory.shared.ldap.schema.DITStructureRule;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.MatchingRuleUse;
import org.apache.directory.shared.ldap.schema.NameForm;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaUtils;
import org.apache.directory.shared.ldap.schema.Syntax;
import org.apache.directory.shared.ldap.schema.parsers.ComparatorDescription;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescription;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescription;

import javax.naming.NamingException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * This class manage the Schema's operations. 
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DefaultSchemaService implements SchemaService
{
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /** cached version of the schema subentry with all attributes in it */
    private ServerEntry schemaSubentry;
    private final Object lock = new Object();
    
    /** The directory service instance */
    private DirectoryService directoryService;

    /** a handle on the registries */
    private Registries registries;

    /** a handle on the schema partition */
    private JdbmPartition schemaPartition;

    /** schema operation control */
    private SchemaOperationControl schemaControl;

    /**
     * the normalized name for the schema modification attributes
     */
    private LdapDN schemaModificationAttributesDN;


    /**
     * Create a new instance of the schemaService
     * 
     * @param registries The associated registries
     * @param schemaPartition The schema partition reference
     * @param schemaControl The schema control instance
     * @throws NamingException If somethi,ng went wrong during initialization
     */
    public DefaultSchemaService( DirectoryService directoryService, JdbmPartition schemaPartition, SchemaOperationControl schemaControl ) throws NamingException
    {
        this.directoryService = directoryService;
        this.registries = directoryService.getRegistries();
        this.schemaPartition = schemaPartition;
        this.schemaControl = schemaControl;

        schemaModificationAttributesDN = new LdapDN( ServerDNConstants.SCHEMA_TIMESTAMP_ENTRY_DN );
        schemaModificationAttributesDN.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.ISchemaService#isSchemaSubentry(java.lang.String)
     */
    public boolean isSchemaSubentry( String dnString ) throws NamingException
    {
        if ( ServerDNConstants.CN_SCHEMA_DN.equalsIgnoreCase( dnString ) ||
            ServerDNConstants.CN_SCHEMA_DN_NORMALIZED.equalsIgnoreCase( dnString ) )
        {
            return true;
        }

        LdapDN dn = new LdapDN( dnString ).normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
        return dn.getNormName().equals( ServerDNConstants.CN_SCHEMA_DN_NORMALIZED );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.ISchemaService#getRegistries()
     */
    public Registries getRegistries()
    {
        return registries;
    }


    /**
     * Generate the comparators attribute from the registry
     */
    private ServerAttribute generateComparators() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.COMPARATORS_AT ) );

        Iterator<ComparatorDescription> list = 
            registries.getComparatorRegistry().comparatorDescriptionIterator();
        
        while ( list.hasNext() )
        {
            ComparatorDescription description = list.next();
            attr.add( SchemaUtils.render( description ) );
        }

        return attr;
    }


    private ServerAttribute generateNormalizers() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.NORMALIZERS_AT ) );

        Iterator<NormalizerDescription> list = registries.getNormalizerRegistry().normalizerDescriptionIterator();

        while ( list.hasNext() )
        {
            NormalizerDescription normalizer = list.next();
            attr.add( SchemaUtils.render( normalizer ) );
        }
        
        return attr;
    }


    private ServerAttribute generateSyntaxCheckers() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.SYNTAX_CHECKERS_AT ) );

        Iterator<SyntaxCheckerDescription> list =
            registries.getSyntaxCheckerRegistry().syntaxCheckerDescriptionIterator();

        while ( list.hasNext() )
        {
            SyntaxCheckerDescription syntaxCheckerDescription = list.next();
            attr.add( SchemaUtils.render( syntaxCheckerDescription ) );
        }
        
        return attr;
    }


    private ServerAttribute generateObjectClasses() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.OBJECT_CLASSES_AT ) );

        Iterator<ObjectClass> list = registries.getObjectClassRegistry().iterator();

        while ( list.hasNext() )
        {
            ObjectClass oc = list.next();
            attr.add( SchemaUtils.render( oc ).toString() );
        }
        
        return attr;
    }


    private ServerAttribute generateAttributeTypes() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.ATTRIBUTE_TYPES_AT ) );

        Iterator<AttributeType> list = registries.getAttributeTypeRegistry().iterator();

        while ( list.hasNext() )
        {
            AttributeType at = list.next();
            attr.add( SchemaUtils.render( at ).toString() );
        }

        return attr;
    }


    private ServerAttribute generateMatchingRules() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.MATCHING_RULES_AT ) );

        Iterator<MatchingRule> list = registries.getMatchingRuleRegistry().iterator();

        while ( list.hasNext() )
        {
            MatchingRule mr = list.next();
            attr.add( SchemaUtils.render( mr ).toString() );
        }

        return attr;
    }


    private ServerAttribute generateMatchingRuleUses() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.MATCHING_RULE_USE_AT ) );

        Iterator<MatchingRuleUse> list = registries.getMatchingRuleUseRegistry().iterator();

        while ( list.hasNext() )
        {
            MatchingRuleUse mru = list.next();
            attr.add( SchemaUtils.render( mru ).toString() );
        }

        return attr;
    }


    private ServerAttribute generateSyntaxes() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.LDAP_SYNTAXES_AT ) );

        Iterator<Syntax> list = registries.getSyntaxRegistry().iterator();

        while ( list.hasNext() )
        {
            Syntax syntax = list.next();
            attr.add( SchemaUtils.render( syntax ).toString() );
        }

        return attr;
    }


    private ServerAttribute generateDitContextRules() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.DIT_CONTENT_RULES_AT ) );

        Iterator<DITContentRule> list = registries.getDitContentRuleRegistry().iterator();

        while ( list.hasNext() )
        {
            DITContentRule dcr = list.next();
            attr.add( SchemaUtils.render( dcr ).toString() );
        }
        
        return attr;
    }


    private ServerAttribute generateDitStructureRules() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.DIT_STRUCTURE_RULES_AT ) );

        Iterator<DITStructureRule> list = registries.getDitStructureRuleRegistry().iterator();

        while ( list.hasNext() )
        {
            DITStructureRule dsr = list.next();
            attr.add( SchemaUtils.render( dsr ).toString() );
        }
        
        return attr;
    }


    private ServerAttribute generateNameForms() throws NamingException
    {
        ServerAttribute attr = new DefaultServerAttribute( 
            registries.getAttributeTypeRegistry().lookup( SchemaConstants.NAME_FORMS_AT ) );

        Iterator<NameForm> list = registries.getNameFormRegistry().iterator();

        while ( list.hasNext() )
        {
            NameForm nf = list.next();
            attr.add( SchemaUtils.render( nf ).toString() );
        }
        
        return attr;
    }


    private void generateSchemaSubentry( ServerEntry mods ) throws NamingException
    {
        ServerEntry attrs = new DefaultServerEntry( registries, mods.getDn() );

        // add the objectClass attribute
        attrs.put( SchemaConstants.OBJECT_CLASS_AT, 
            SchemaConstants.TOP_OC,
            SchemaConstants.SUBSCHEMA_OC,
            SchemaConstants.SUBENTRY_OC,
            ApacheSchemaConstants.APACHE_SUBSCHEMA_OC
            );

        // add the cn attribute as required for the RDN
        attrs.put( SchemaConstants.CN_AT, "schema" );

        // generate all the other operational attributes
        attrs.put( generateComparators() );
        attrs.put( generateNormalizers() );
        attrs.put( generateSyntaxCheckers() );
        attrs.put( generateObjectClasses() );
        attrs.put( generateAttributeTypes() );
        attrs.put( generateMatchingRules() );
        attrs.put( generateMatchingRuleUses() );
        attrs.put( generateSyntaxes() );
        attrs.put( generateDitContextRules() );
        attrs.put( generateDitStructureRules() );
        attrs.put( generateNameForms() );
        attrs.put( SchemaConstants.SUBTREE_SPECIFICATION_AT, "{}" );

        // -------------------------------------------------------------------
        // set standard operational attributes for the subentry
        // -------------------------------------------------------------------

        // Add the createTimestamp
        AttributeType createTimestampAT = registries.
            getAttributeTypeRegistry().lookup( SchemaConstants.CREATE_TIMESTAMP_AT );
        EntryAttribute createTimestamp = mods.get( createTimestampAT );
        attrs.put( SchemaConstants.CREATE_TIMESTAMP_AT, createTimestamp.get() );

        // Add the creatorsName
        attrs.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN );

        // Add the modifyTimestamp
        AttributeType schemaModifyTimestampAT = registries.
            getAttributeTypeRegistry().lookup( ApacheSchemaConstants.SCHEMA_MODIFY_TIMESTAMP_AT );
        EntryAttribute schemaModifyTimestamp = mods.get( schemaModifyTimestampAT );
        attrs.put( SchemaConstants.MODIFY_TIMESTAMP_AT, schemaModifyTimestamp.get() );

        // Add the modifiersName
        AttributeType schemaModifiersNameAT = registries.
            getAttributeTypeRegistry().lookup( ApacheSchemaConstants.SCHEMA_MODIFIERS_NAME_AT );
        EntryAttribute schemaModifiersName = mods.get( schemaModifiersNameAT );
        attrs.put( SchemaConstants.MODIFIERS_NAME_AT, schemaModifiersName.get() );

        // don't swap out if a request for the subentry is in progress or we
        // can give back an inconsistent schema back to the client so we block
        synchronized ( lock )
        {
            schemaSubentry = attrs;
        }
    }


    private void addAttribute( ServerEntry attrs, String id ) throws NamingException
    {
        EntryAttribute attr = schemaSubentry.get( id );

        if ( attr != null )
        {
            attrs.put( attr );
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.ISchemaService#getSubschemaEntryImmutable()
     */
    public ServerEntry getSubschemaEntryImmutable() throws Exception
    {
        if ( schemaSubentry == null )
        {
            generateSchemaSubentry( schemaPartition.lookup(
                    new LookupOperationContext( null, schemaModificationAttributesDN ) ) );
        }

        return ( ServerEntry ) schemaSubentry.clone();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.ISchemaService#getSubschemaEntryCloned()
     */
    public ServerEntry getSubschemaEntryCloned() throws Exception
    {
        if ( schemaSubentry == null )
        {
            generateSchemaSubentry( schemaPartition.lookup(
                    new LookupOperationContext( null, schemaModificationAttributesDN ) ) );
        }

        return ( ServerEntry ) schemaSubentry.clone();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.ISchemaService#getSubschemaEntry(java.lang.String[])
     */
    public ServerEntry getSubschemaEntry( String[] ids ) throws Exception
    {
        if ( ids == null )
        {
            ids = EMPTY_STRING_ARRAY;
        }

        Set<String> setOids = new HashSet<String>();
        ServerEntry attrs = new DefaultServerEntry( registries, LdapDN.EMPTY_LDAPDN );
        boolean returnAllOperationalAttributes = false;

        synchronized( lock )
        {
            // ---------------------------------------------------------------
            // Check if we need an update by looking at timestamps on disk
            // ---------------------------------------------------------------

            ServerEntry mods = 
                schemaPartition.lookup( new LookupOperationContext( null, schemaModificationAttributesDN ) );
// @todo enable this optimization at some point but for now it
// is causing some problems so I will just turn it off
//          Attribute modifyTimeDisk = mods.get( SchemaConstants.MODIFY_TIMESTAMP_AT );
//
//          Attribute modifyTimeMemory = null;
//
//            if ( schemaSubentry != null )
//            {
//                modifyTimeMemory = schemaSubentry.get( SchemaConstants.MODIFY_TIMESTAMP_AT );
//                if ( modifyTimeDisk == null && modifyTimeMemory == null )
//                {
//                    // do nothing!
//                }
//                else if ( modifyTimeDisk != null && modifyTimeMemory != null )
//                {
//                    Date disk = DateUtils.getDate( ( String ) modifyTimeDisk.get() );
//                    Date mem = DateUtils.getDate( ( String ) modifyTimeMemory.get() );
//                    if ( disk.after( mem ) )
//                    {
//                        generateSchemaSubentry( mods );
//                    }
//                }
//                else
//                {
//                    generateSchemaSubentry( mods );
//                }
//            }
//            else
//            {
                generateSchemaSubentry( mods );
//            }

            // ---------------------------------------------------------------
            // Prep Work: Transform the attributes to their OID counterpart
            // ---------------------------------------------------------------

            for ( String id:ids )
            {
                // Check whether the set contains a plus, and use it below to include all
                // operational attributes.  Due to RFC 3673, and issue DIREVE-228 in JIRA
                if ( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES.equals( id ) )
                {
                    returnAllOperationalAttributes = true;
                }
                else if ( SchemaConstants.ALL_USER_ATTRIBUTES.equals(  id ) )
                {
                    setOids.add( id );
                }
                else
                {
                    setOids.add( registries.getOidRegistry().getOid( id ) );
                }
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.COMPARATORS_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.COMPARATORS_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.NORMALIZERS_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.NORMALIZERS_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.SYNTAX_CHECKERS_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.SYNTAX_CHECKERS_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.OBJECT_CLASSES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.OBJECT_CLASSES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.ATTRIBUTE_TYPES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.ATTRIBUTE_TYPES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.MATCHING_RULES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.MATCHING_RULES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.MATCHING_RULE_USE_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.MATCHING_RULE_USE_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.LDAP_SYNTAXES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.LDAP_SYNTAXES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.DIT_CONTENT_RULES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.DIT_CONTENT_RULES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.DIT_STRUCTURE_RULES_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.DIT_STRUCTURE_RULES_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.NAME_FORMS_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.NAME_FORMS_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.SUBTREE_SPECIFICATION_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.SUBTREE_SPECIFICATION_AT );
            }

            int minSetSize = 0;
            if ( setOids.contains( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
            {
                minSetSize++;
            }

            if ( setOids.contains( SchemaConstants.ALL_USER_ATTRIBUTES ) )
            {
                minSetSize++;
            }

            if ( setOids.contains( SchemaConstants.REF_AT_OID ) )
            {
                minSetSize++;
            }

            // add the objectClass attribute
            if ( setOids.contains( SchemaConstants.ALL_USER_ATTRIBUTES ) ||
                 setOids.contains( SchemaConstants.OBJECT_CLASS_AT_OID ) ||
                 setOids.size() == minSetSize )
            {
                addAttribute( attrs, SchemaConstants.OBJECT_CLASS_AT );
            }

            // add the cn attribute as required for the RDN
            if ( setOids.contains( SchemaConstants.ALL_USER_ATTRIBUTES ) ||
                 setOids.contains( SchemaConstants.CN_AT_OID ) ||
                 setOids.size() == minSetSize )
            {
                addAttribute( attrs, SchemaConstants.CN_AT );
            }

            // -------------------------------------------------------------------
            // set standard operational attributes for the subentry
            // -------------------------------------------------------------------


            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.CREATE_TIMESTAMP_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.CREATE_TIMESTAMP_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.CREATORS_NAME_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.CREATORS_NAME_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.MODIFY_TIMESTAMP_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.MODIFY_TIMESTAMP_AT );
            }

            if ( returnAllOperationalAttributes || setOids.contains( SchemaConstants.MODIFIERS_NAME_AT_OID ) )
            {
                addAttribute( attrs, SchemaConstants.MODIFIERS_NAME_AT );
            }
        }

        return attrs;
    }


    SchemaOperationControl getSchemaControl()
    {
        return schemaControl;
    }
}
