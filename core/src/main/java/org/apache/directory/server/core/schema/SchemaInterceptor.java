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


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.filtering.EntryFilter;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.codec.controls.CascadeControl;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.EmptyCursor;
import org.apache.directory.shared.ldap.cursor.SingletonCursor;
import org.apache.directory.shared.ldap.entry.BinaryValue;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapAttributeInUseException;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeTypeException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.AssertionNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ExtensibleNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.AVA;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.ObjectClassTypeEnum;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.SyntaxChecker;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.shared.ldap.schema.registries.SchemaLoader;
import org.apache.directory.shared.ldap.schema.syntaxCheckers.OctetStringSyntaxChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An {@link org.apache.directory.server.core.interceptor.Interceptor} that manages and enforces schemas.
 *
 * @todo Better interceptor description required.

 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SchemaInterceptor extends BaseInterceptor
{
    /** The LoggerFactory used by this Interceptor */
    private static Logger LOG = LoggerFactory.getLogger( SchemaInterceptor.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /**
     * the root nexus to all database partitions
     */
    private PartitionNexus nexus;

    /**
     * a binary attribute tranforming filter: String -> byte[]
     */
    private BinaryAttributeFilter binaryAttributeFilter;

    private TopFilter topFilter;

    private List<EntryFilter> filters = new ArrayList<EntryFilter>();

    /** the global schema object SchemaManager */
    private SchemaManager schemaManager;

    /** A global reference to the ObjectClass attributeType */
    private AttributeType OBJECT_CLASS_AT;

    /** A normalized form for the SubschemaSubentry DN */
    private String subschemaSubentryDnNorm;

    /** The SubschemaSubentry DN */
    private DN subschemaSubentryDn;

    /**
     * the normalized name for the schema modification attributes
     */
    private DN schemaModificationAttributesDN;

    /** The schema manager */
    private SchemaSubentryManager schemaSubEntryManager;

    private SchemaService schemaService;

    /** the base DN (normalized) of the schema partition */
    private DN schemaBaseDN;

    /** A map used to store all the objectClasses superiors */
    private Map<String, List<ObjectClass>> superiors;

    /** A map used to store all the objectClasses may attributes */
    private Map<String, List<AttributeType>> allMay;

    /** A map used to store all the objectClasses must */
    private Map<String, List<AttributeType>> allMust;

    /** A map used to store all the objectClasses allowed attributes (may + must) */
    private Map<String, List<AttributeType>> allowed;

    private static AttributeType MODIFIERS_NAME_ATTRIBUTE_TYPE;
    private static AttributeType MODIFY_TIMESTAMP_ATTRIBUTE_TYPE;
    

    /**
     * Initialize the Schema Service
     *
     * @param directoryService the directory service core
     * @throws Exception if there are problems during initialization
     */
    public void init( DirectoryService directoryService ) throws LdapException
    {
        if ( IS_DEBUG )
        {
            LOG.debug( "Initializing SchemaInterceptor..." );
        }

        nexus = directoryService.getPartitionNexus();
        schemaManager = directoryService.getSchemaManager();
        OBJECT_CLASS_AT = schemaManager.getAttributeType( SchemaConstants.OBJECT_CLASS_AT );
        binaryAttributeFilter = new BinaryAttributeFilter();
        topFilter = new TopFilter();
        filters.add( binaryAttributeFilter );
        filters.add( topFilter );

        schemaBaseDN = new DN( SchemaConstants.OU_SCHEMA, schemaManager );
        schemaService = directoryService.getSchemaService();

        // stuff for dealing with subentries (garbage for now)
        Value<?> subschemaSubentry = nexus.getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        subschemaSubentryDn = new DN( subschemaSubentry.getString() );
        subschemaSubentryDn.normalize( schemaManager.getNormalizerMapping() );
        subschemaSubentryDnNorm = subschemaSubentryDn.getNormName();

        schemaModificationAttributesDN = new DN( ServerDNConstants.SCHEMA_MODIFICATIONS_DN );
        schemaModificationAttributesDN.normalize( schemaManager.getNormalizerMapping() );

        computeSuperiors();

        // Initialize the schema manager
        SchemaLoader loader = schemaService.getSchemaPartition().getSchemaManager().getLoader();
        schemaSubEntryManager = new SchemaSubentryManager( schemaManager, loader );

        MODIFIERS_NAME_ATTRIBUTE_TYPE = schemaManager.getAttributeType( SchemaConstants.MODIFIERS_NAME_AT );
        MODIFY_TIMESTAMP_ATTRIBUTE_TYPE = schemaManager.getAttributeType( SchemaConstants.MODIFY_TIMESTAMP_AT );
        
        if ( IS_DEBUG )
        {
            LOG.debug( "SchemaInterceptor Initialized !" );
        }
    }


    /**
     * Compute the MUST attributes for an objectClass. This method gather all the
     * MUST from all the objectClass and its superors.
     *
     * @param atSeen ???
     * @param objectClass the object class to gather MUST attributes for
     * @throws Exception if there are problems resolving schema entitites
     */
    private void computeMustAttributes( ObjectClass objectClass, Set<String> atSeen ) throws LdapException
    {
        List<ObjectClass> parents = superiors.get( objectClass.getOid() );

        List<AttributeType> mustList = new ArrayList<AttributeType>();
        List<AttributeType> allowedList = new ArrayList<AttributeType>();
        Set<String> mustSeen = new HashSet<String>();

        allMust.put( objectClass.getOid(), mustList );
        allowed.put( objectClass.getOid(), allowedList );

        for ( ObjectClass parent : parents )
        {
            List<AttributeType> mustParent = parent.getMustAttributeTypes();

            if ( ( mustParent != null ) && ( mustParent.size() != 0 ) )
            {
                for ( AttributeType attributeType : mustParent )
                {
                    String oid = attributeType.getOid();

                    if ( !mustSeen.contains( oid ) )
                    {
                        mustSeen.add( oid );
                        mustList.add( attributeType );
                        allowedList.add( attributeType );
                        atSeen.add( attributeType.getOid() );
                    }
                }
            }
        }
    }


    /**
     * Compute the MAY attributes for an objectClass. This method gather all the
     * MAY from all the objectClass and its superors.
     *
     * The allowed attributes is also computed, it's the union of MUST and MAY
     *
     * @param atSeen ???
     * @param objectClass the object class to get all the MAY attributes for
     * @throws Exception with problems accessing registries
     */
    private void computeMayAttributes( ObjectClass objectClass, Set<String> atSeen ) throws LdapException
    {
        List<ObjectClass> parents = superiors.get( objectClass.getOid() );

        List<AttributeType> mayList = new ArrayList<AttributeType>();
        Set<String> maySeen = new HashSet<String>();
        List<AttributeType> allowedList = allowed.get( objectClass.getOid() );

        allMay.put( objectClass.getOid(), mayList );

        for ( ObjectClass parent : parents )
        {
            List<AttributeType> mustParent = parent.getMustAttributeTypes();

            if ( ( mustParent != null ) && ( mustParent.size() != 0 ) )
            {
                for ( AttributeType attributeType : mustParent )
                {
                    String oid = attributeType.getOid();

                    if ( !maySeen.contains( oid ) )
                    {
                        maySeen.add( oid );
                        mayList.add( attributeType );

                        if ( !atSeen.contains( oid ) )
                        {
                            allowedList.add( attributeType );
                        }
                    }
                }
            }
        }
    }


    /**
     * Recursively compute all the superiors of an object class. For instance, considering
     * 'inetOrgPerson', it's direct superior is 'organizationalPerson', which direct superior
     * is 'Person', which direct superior is 'top'.
     *
     * As a result, we will gather all of these three ObjectClasses in 'inetOrgPerson' ObjectClasse
     * superiors.
     */
    private void computeOCSuperiors( ObjectClass objectClass, List<ObjectClass> superiors, Set<String> ocSeen )
        throws LdapException
    {
        List<ObjectClass> parents = objectClass.getSuperiors();

        // Loop on all the objectClass superiors
        if ( ( parents != null ) && ( parents.size() != 0 ) )
        {
            for ( ObjectClass parent : parents )
            {
                // Top is not added
                if ( SchemaConstants.TOP_OC.equals( parent.getName() ) )
                {
                    continue;
                }

                // For each one, recurse
                computeOCSuperiors( parent, superiors, ocSeen );

                String oid = parent.getOid();

                if ( !ocSeen.contains( oid ) )
                {
                    superiors.add( parent );
                    ocSeen.add( oid );
                }
            }
        }
    }


    /**
     * Compute the superiors and MUST/MAY attributes for a specific
     * ObjectClass
     */
    private void computeSuperior( ObjectClass objectClass ) throws LdapException
    {
        List<ObjectClass> ocSuperiors = new ArrayList<ObjectClass>();

        superiors.put( objectClass.getOid(), ocSuperiors );

        computeOCSuperiors( objectClass, ocSuperiors, new HashSet<String>() );

        Set<String> atSeen = new HashSet<String>();
        computeMustAttributes( objectClass, atSeen );
        computeMayAttributes( objectClass, atSeen );

        superiors.put( objectClass.getName(), ocSuperiors );
    }


    /**
     * Compute all ObjectClasses superiors, MAY and MUST attributes.
     * @throws Exception
     */
    private void computeSuperiors() throws LdapException
    {
        Iterator<ObjectClass> objectClasses = schemaManager.getObjectClassRegistry().iterator();
        superiors = new ConcurrentHashMap<String, List<ObjectClass>>();
        allMust = new ConcurrentHashMap<String, List<AttributeType>>();
        allMay = new ConcurrentHashMap<String, List<AttributeType>>();
        allowed = new ConcurrentHashMap<String, List<AttributeType>>();

        while ( objectClasses.hasNext() )
        {
            ObjectClass objectClass = objectClasses.next();
            computeSuperior( objectClass );
        }
    }


    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext listContext )
        throws LdapException
    {
        EntryFilteringCursor cursor = nextInterceptor.list( listContext );
        cursor.addEntryFilter( binaryAttributeFilter );
        return cursor;
    }

    
    /**
     * {@inheritDoc}
     */
    public boolean compare( NextInterceptor next, CompareOperationContext compareContext ) throws LdapException
    {
        if ( IS_DEBUG )
        {
            LOG.debug( "Operation Context: {}", compareContext );
        }

        // Check that the requested AT exists
        // complain if we do not recognize the attribute being compared
        if ( !schemaManager.getAttributeTypeRegistry().contains( compareContext.getOid() ) )
        {
            throw new LdapInvalidAttributeTypeException( I18n.err( I18n.ERR_266, compareContext.getOid() ) );
        }

        boolean result = next.compare( compareContext );
        
        return result;
    }


    /**
     * Remove all unknown attributes from the searchControls, to avoid an exception.
     *
     * RFC 2251 states that :
     * " Attributes MUST be named at most once in the list, and are returned "
     * " at most once in an entry. "
     * " If there are attribute descriptions in "
     * " the list which are not recognized, they are ignored by the server."
     *
     * @param searchCtls The SearchControls we will filter
     */
    // This will suppress PMD.EmptyCatchBlock warnings in this method
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void filterAttributesToReturn( SearchControls searchCtls )
    {
        String[] attributes = searchCtls.getReturningAttributes();

        if ( ( attributes == null ) || ( attributes.length == 0 ) )
        {
            // We have no attributes, that means "*" (all users attributes)
            searchCtls.setReturningAttributes( SchemaConstants.ALL_USER_ATTRIBUTES_ARRAY );
            return;
        }

        Map<String, String> filteredAttrs = new HashMap<String, String>();
        boolean hasNoAttribute = false;
        boolean hasAttributes = false;

        for ( String attribute : attributes )
        {
            // Skip special attributes
            if ( ( SchemaConstants.ALL_USER_ATTRIBUTES.equals( attribute ) )
                || ( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES.equals( attribute ) )
                || ( SchemaConstants.NO_ATTRIBUTE.equals( attribute ) ) )
            {
                if ( !filteredAttrs.containsKey( attribute ) )
                {
                    filteredAttrs.put( attribute, attribute );
                }

                if ( SchemaConstants.NO_ATTRIBUTE.equals( attribute ) )
                {
                    hasNoAttribute = true;
                }
                else
                {
                    hasAttributes = true;
                }

                continue;
            }

            try
            {
                // Check that the attribute is declared
                AttributeType attributeType = schemaManager.lookupAttributeTypeRegistry( attribute );
                
                String oid = attributeType.getOid();

                // Don't add the AT twice
                if ( !filteredAttrs.containsKey( oid ) )
                {
                    // Ok, we can add the attribute to the list of filtered attributes
                    filteredAttrs.put( oid, attribute );
                }

                hasAttributes = true;
            }
            catch ( Exception ne )
            {
                /* Do nothing, the attribute does not exist */
            }
        }

        // Treat a special case : if we have an attribute and "1.1", then discard "1.1"
        if ( hasAttributes && hasNoAttribute )
        {
            filteredAttrs.remove( SchemaConstants.NO_ATTRIBUTE );
        }

        // If we still have the same attribute number, then we can just get out the method
        if ( filteredAttrs.size() == attributes.length )
        {
            return;
        }

        // Deal with the special case where the attribute list is now empty
        if ( filteredAttrs.size() == 0 )
        {
            // We just have to pass the special 1.1 attribute,
            // as we don't want to return any attribute
            searchCtls.setReturningAttributes( SchemaConstants.NO_ATTRIBUTE_ARRAY );
            return;
        }

        // Some attributes have been removed. let's modify the searchControl
        String[] newAttributesList = new String[filteredAttrs.size()];

        int pos = 0;

        for ( String key : filteredAttrs.keySet() )
        {
            newAttributesList[pos++] = filteredAttrs.get( key );
        }

        searchCtls.setReturningAttributes( newAttributesList );
    }


    private Value<?> convert( AttributeType attributeType, Value<?> value ) throws LdapException
    {
        if ( attributeType.getSyntax().isHumanReadable() )
        {
            if ( value instanceof BinaryValue )
            {
                try
                {
                    return new StringValue( attributeType, new String( (( BinaryValue ) value).getBytes(), "UTF-8" ) );
                }
                catch ( UnsupportedEncodingException uee )
                {
                    String message = I18n.err( I18n.ERR_47 );
                    LOG.error( message );
                    throw new LdapException( message );
                }
            }
        }
        else
        {
            if ( value instanceof StringValue )
            {
                return new BinaryValue( attributeType, ( ( StringValue ) value ).getBytes() );
            }
        }

        return null;
    }


    /**
     * Check that the filter values are compatible with the AttributeType. Typically,
     * a HumanReadible filter should have a String value. The substring filter should
     * not be used with binary attributes.
     */
    private void checkFilter( ExprNode filter ) throws LdapException
    {
        if ( filter == null )
        {
            String message = I18n.err( I18n.ERR_49 );
            LOG.error( message );
            throw new LdapException( message );
        }

        if ( filter.isLeaf() )
        {
            if ( filter instanceof EqualityNode )
            {
                EqualityNode node = ( ( EqualityNode ) filter );
                Value<?> value = node.getValue();

                Value<?> newValue = convert( node.getAttributeType(), value );

                if ( newValue != null )
                {
                    node.setValue( newValue );
                }
            }
            else if ( ( filter instanceof SubstringNode ) || 
                      ( filter instanceof PresenceNode ) || 
                      ( filter instanceof AssertionNode ) || 
                      ( filter instanceof ScopeNode ) )
            {
                // Nothing to do
            }
            else if ( filter instanceof GreaterEqNode )
            {
                GreaterEqNode node = ( ( GreaterEqNode ) filter );
                Value<?> value = node.getValue();

                Value<?> newValue = convert( node.getAttributeType(), value );

                if ( newValue != null )
                {
                    node.setValue( newValue );
                }

            }
            else if ( filter instanceof LessEqNode )
            {
                LessEqNode node = ( ( LessEqNode ) filter );
                Value<?> value = node.getValue();

                Value<?> newValue = convert( node.getAttributeType(), value );

                if ( newValue != null )
                {
                    node.setValue( newValue );
                }
            }
            else if ( filter instanceof ExtensibleNode )
            {
                ExtensibleNode node = ( ( ExtensibleNode ) filter );
            }
            else if ( filter instanceof ApproximateNode )
            {
                ApproximateNode node = ( ( ApproximateNode ) filter );
                Value<?> value = node.getValue();

                Value<?> newValue = convert( node.getAttributeType(), value );

                if ( newValue != null )
                {
                    node.setValue( newValue );
                }
            }
        }
        else
        {
            // Recursively iterate through all the children.
            for ( ExprNode child : ( ( BranchNode ) filter ).getChildren() )
            {
                checkFilter( child );
            }
        }
    }


    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext searchContext )
        throws LdapException
    {
        DN base = searchContext.getDn();
        SearchControls searchCtls = searchContext.getSearchControls();
        ExprNode filter = searchContext.getFilter();

        // We have to eliminate bad attributes from the request, accordingly
        // to RFC 2251, chap. 4.5.1. Basically, all unknown attributes are removed
        // from the list
        if ( searchCtls.getReturningAttributes() != null )
        {
            filterAttributesToReturn( searchCtls );
        }

        // We also have to check the H/R flag for the filter attributes
        checkFilter( filter );

        String baseNormForm = ( base.isNormalized() ? base.getNormName() : base.getNormName() );

        // Deal with the normal case : searching for a normal value (not subSchemaSubEntry)
        if ( !subschemaSubentryDnNorm.equals( baseNormForm ) )
        {
            EntryFilteringCursor cursor = nextInterceptor.search( searchContext );

            if ( searchCtls.getReturningAttributes() != null )
            {
                cursor.addEntryFilter( topFilter );
                return cursor;
            }

            for ( EntryFilter ef : filters )
            {
                cursor.addEntryFilter( ef );
            }

            return cursor;
        }

        // The user was searching into the subSchemaSubEntry
        // This kind of search _must_ be limited to OBJECT scope (the subSchemaSubEntry
        // does not have any sub level)
        if ( searchCtls.getSearchScope() == SearchControls.OBJECT_SCOPE )
        {
            // The filter can be an equality or a presence, but nothing else
            if ( filter instanceof SimpleNode )
            {
                // We should get the value for the filter.
                // only 'top' and 'subSchema' are valid values
                SimpleNode node = ( SimpleNode ) filter;
                String objectClass;

                objectClass = node.getValue().getString();

                String objectClassOid = null;

                if ( schemaManager.getObjectClassRegistry().contains( objectClass ) )
                {
                    objectClassOid = schemaManager.getObjectClassRegistry().lookup( objectClass ).getOid();
                }
                else
                {
                    return new BaseEntryFilteringCursor( new EmptyCursor<Entry>(), searchContext );
                }

                AttributeType nodeAt = node.getAttributeType();

                // see if node attribute is objectClass
                if ( nodeAt.equals( OBJECT_CLASS_AT )
                    && ( objectClassOid.equals( SchemaConstants.TOP_OC_OID ) || objectClassOid
                        .equals( SchemaConstants.SUBSCHEMA_OC_OID ) ) && ( node instanceof EqualityNode ) )
                {
                    // call.setBypass( true );
                    Entry serverEntry = schemaService.getSubschemaEntry( searchCtls.getReturningAttributes() );
                    serverEntry.setDn( base );
                    return new BaseEntryFilteringCursor( new SingletonCursor<Entry>( serverEntry ), searchContext );
                }
                else
                {
                    return new BaseEntryFilteringCursor( new EmptyCursor<Entry>(), searchContext );
                }
            }
            else if ( filter instanceof PresenceNode )
            {
                PresenceNode node = ( PresenceNode ) filter;

                // see if node attribute is objectClass
                if ( node.getAttributeType().equals( OBJECT_CLASS_AT ) )
                {
                    // call.setBypass( true );
                    Entry serverEntry = schemaService.getSubschemaEntry( searchCtls.getReturningAttributes() );
                    serverEntry.setDn( base );
                    EntryFilteringCursor cursor = new BaseEntryFilteringCursor(
                        new SingletonCursor<Entry>( serverEntry ), searchContext );
                    return cursor;
                }
            }
        }

        // In any case not handled previously, just return an empty result
        return new BaseEntryFilteringCursor( new EmptyCursor<Entry>(), searchContext );
    }


    /**
     * Search for an entry, using its DN. Binary attributes and ObjectClass attribute are removed.
     */
    public Entry lookup( NextInterceptor nextInterceptor, LookupOperationContext lookupContext ) throws LdapException
    {
        Entry result = nextInterceptor.lookup( lookupContext );

        if ( result == null )
        {
            return null;
        }

        filterBinaryAttributes( result );

        return result;
    }


    private void getSuperiors( ObjectClass oc, Set<String> ocSeen, List<ObjectClass> result ) throws LdapException
    {
        for ( ObjectClass parent : oc.getSuperiors() )
        {
            // Skip 'top'
            if ( SchemaConstants.TOP_OC.equals( parent.getName() ) )
            {
                continue;
            }

            if ( !ocSeen.contains( parent.getOid() ) )
            {
                ocSeen.add( parent.getOid() );
                result.add( parent );
            }

            // Recurse on the parent
            getSuperiors( parent, ocSeen, result );
        }
    }


    private boolean getObjectClasses( EntryAttribute objectClasses, List<ObjectClass> result ) throws LdapException
    {
        Set<String> ocSeen = new HashSet<String>();

        // We must select all the ObjectClasses, except 'top',
        // but including all the inherited ObjectClasses
        boolean hasExtensibleObject = false;

        for ( Value<?> objectClass : objectClasses )
        {
            String objectClassName = objectClass.getString();

            if ( SchemaConstants.TOP_OC.equals( objectClassName ) )
            {
                continue;
            }

            if ( SchemaConstants.EXTENSIBLE_OBJECT_OC.equalsIgnoreCase( objectClassName ) )
            {
                hasExtensibleObject = true;
            }

            ObjectClass oc = schemaManager.getObjectClassRegistry().lookup( objectClassName );

            // Add all unseen objectClasses to the list, except 'top'
            if ( !ocSeen.contains( oc.getOid() ) )
            {
                ocSeen.add( oc.getOid() );
                result.add( oc );
            }

            // Find all current OC parents
            getSuperiors( oc, ocSeen, result );
        }

        return hasExtensibleObject;
    }


    private Set<String> getAllMust( EntryAttribute objectClasses ) throws LdapException
    {
        Set<String> must = new HashSet<String>();

        // Loop on all objectclasses
        for ( Value<?> value : objectClasses )
        {
            String ocName = value.getString();
            ObjectClass oc = schemaManager.getObjectClassRegistry().lookup( ocName );

            List<AttributeType> types = oc.getMustAttributeTypes();

            // For each objectClass, loop on all MUST attributeTypes, if any
            if ( ( types != null ) && ( types.size() > 0 ) )
            {
                for ( AttributeType type : types )
                {
                    must.add( type.getOid() );
                }
            }
        }

        return must;
    }


    private Set<String> getAllAllowed( EntryAttribute objectClasses, Set<String> must ) throws LdapException
    {
        Set<String> allowed = new HashSet<String>( must );

        // Add the 'ObjectClass' attribute ID
        allowed.add( SchemaConstants.OBJECT_CLASS_AT_OID );

        // Loop on all objectclasses
        for ( Value<?> objectClass : objectClasses )
        {
            String ocName = objectClass.getString();
            ObjectClass oc = schemaManager.getObjectClassRegistry().lookup( ocName );

            List<AttributeType> types = oc.getMayAttributeTypes();

            // For each objectClass, loop on all MAY attributeTypes, if any
            if ( ( types != null ) && ( types.size() > 0 ) )
            {
                for ( AttributeType type : types )
                {
                    String oid = type.getOid();

                    allowed.add( oid );
                }
            }
        }

        return allowed;
    }


    /**
     * Given the objectClasses for an entry, this method adds missing ancestors 
     * in the hierarchy except for top which it removes.  This is used for this
     * solution to DIREVE-276.  More information about this solution can be found
     * <a href="http://docs.safehaus.org:8080/x/kBE">here</a>.
     * 
     * @param objectClassAttr the objectClass attribute to modify
     * @throws Exception if there are problems 
     */
    private void alterObjectClasses( EntryAttribute objectClassAttr ) throws LdapException
    {
        Set<String> objectClasses = new HashSet<String>();
        Set<String> objectClassesUP = new HashSet<String>();

        // Init the objectClass list with 'top'
        objectClasses.add( SchemaConstants.TOP_OC );
        objectClassesUP.add( SchemaConstants.TOP_OC );

        // Construct the new list of ObjectClasses
        for ( Value<?> ocValue : objectClassAttr )
        {
            String ocName = ocValue.getString();

            if ( !ocName.equalsIgnoreCase( SchemaConstants.TOP_OC ) )
            {
                String ocLowerName = ocName.toLowerCase();

                ObjectClass objectClass = schemaManager.getObjectClassRegistry().lookup( ocLowerName );

                if ( !objectClasses.contains( ocLowerName ) )
                {
                    objectClasses.add( ocLowerName );
                    objectClassesUP.add( ocName );
                }

                List<ObjectClass> ocSuperiors = superiors.get( objectClass.getOid() );

                if ( ocSuperiors != null )
                {
                    for ( ObjectClass oc : ocSuperiors )
                    {
                        if ( !objectClasses.contains( oc.getName().toLowerCase() ) )
                        {
                            objectClasses.add( oc.getName() );
                            objectClassesUP.add( oc.getName() );
                        }
                    }
                }
            }
        }

        // Now, reset the ObjectClass attribute and put the new list into it
        objectClassAttr.clear();

        for ( String attribute : objectClassesUP )
        {
            objectClassAttr.add( attribute );
        }
    }


    public void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        DN oldDn = renameContext.getDn();
        RDN newRdn = renameContext.getNewRdn();
        boolean deleteOldRn = renameContext.getDeleteOldRdn();
        Entry entry = renameContext.getEntry().getClonedEntry();

        /*
         *  Note: This is only a consistency checks, to the ensure that all
         *  mandatory attributes are available after deleting the old RDN.
         *  The real modification is done in the XdbmStore class.
         *  - TODO: this check is missing in the moveAndRename() method
         */
        if ( deleteOldRn )
        {
            RDN oldRDN = oldDn.getRdn();

            // Delete the old RDN means we remove some attributes and values.
            // We must make sure that after this operation all must attributes
            // are still present in the entry.
            for ( AVA atav : oldRDN )
            {
                AttributeType type = schemaManager.lookupAttributeTypeRegistry( atav.getUpType() );
                entry.remove( type, atav.getUpValue() );
            }

            // Check that no operational attributes are removed
            for ( AVA atav : oldRDN )
            {
                AttributeType attributeType = schemaManager.lookupAttributeTypeRegistry( atav.getUpType() );

                if ( !attributeType.isUserModifiable() )
                {
                    throw new LdapNoPermissionException( "Cannot modify the attribute '" + atav.getUpType() + "'" );
                }
            }
        }
        
        for ( AVA atav : newRdn )
        {
            AttributeType type = schemaManager.lookupAttributeTypeRegistry( atav.getUpType() );

            if ( !entry.contains( type, atav.getNormValue() ) )
            {
                entry.add( new DefaultEntryAttribute( type, atav.getUpValue() ) );
            }
        }

        // Substitute the RDN and check if the new entry is correct
        entry.setDn( renameContext.getNewDn() );

        check( renameContext.getNewDn(), entry );

        next.rename( renameContext );
    }


    /**
     * Create a new attribute using the given values
     */
    private EntryAttribute createNewAttribute( EntryAttribute attribute )
    {
        AttributeType attributeType = attribute.getAttributeType();

        // Create the new Attribute
        EntryAttribute newAttribute = new DefaultEntryAttribute( attribute.getUpId(), attributeType );

        for ( Value<?> value : attribute )
        {
            newAttribute.add( value );
        }

        return newAttribute;
    }


    /**
     * Modify an entry, applying the given modifications, and check if it's OK
     */
    private void checkModifyEntry( DN dn, Entry currentEntry, List<Modification> mods ) throws LdapException
    {
        // The first step is to check that the modifications are valid :
        // - the ATs are present in the schema
        // - The value is syntaxically correct
        //
        // While doing that, we will apply the modification to a copy of the current entry
        Entry tempEntry = ( Entry ) currentEntry.clone();

        // Now, apply each mod one by one
        for ( Modification mod : mods )
        {
            EntryAttribute attribute = mod.getAttribute();
            AttributeType attributeType = attribute.getAttributeType();

            // We don't allow modification of operational attributes
            if ( !attributeType.isUserModifiable()
                && ( !attributeType.equals( MODIFIERS_NAME_ATTRIBUTE_TYPE ) 
                && ( !attributeType.equals( MODIFY_TIMESTAMP_ATTRIBUTE_TYPE ) )
                && ( !PWD_POLICY_STATE_ATTRIBUTE_TYPES.contains( attributeType ) ) ) )
            {
                String msg = I18n.err( I18n.ERR_52, attributeType );
                LOG.error( msg );
                throw new LdapNoPermissionException( msg );
            }

            switch ( mod.getOperation() )
            {
                case ADD_ATTRIBUTE:
                    // Check the syntax here
                    if ( !attribute.isValid() )
                    {
                        // The value syntax is incorrect : this is an error
                        String msg = I18n.err( I18n.ERR_53, attributeType );
                        LOG.error( msg );
                        throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, msg );
                    }

                    EntryAttribute currentAttribute = tempEntry.get( attributeType );

                    // First check if the added Attribute is already present in the entry
                    // If not, we have to create the entry
                    if ( currentAttribute != null )
                    {
                        for ( Value<?> value : attribute )
                        {
                            // At this point, we know that the attribute's syntax is correct
                            // We just have to check that the current attribute does not 
                            // contains the value already
                            if ( currentAttribute.contains( value ) )
                            {
                                // This is an error. 
                                String msg = I18n.err( I18n.ERR_54, value );
                                LOG.error( msg );
                                throw new LdapAttributeInUseException( msg );
                            }

                            currentAttribute.add( value );
                        }
                    }
                    else
                    {
                        // We don't check if the attribute is not in the MUST or MAY at this
                        // point, as one of the following modification can change the 
                        // ObjectClasses.
                        EntryAttribute newAttribute = createNewAttribute( attribute );

                        tempEntry.put( newAttribute );
                    }

                    break;

                case REMOVE_ATTRIBUTE:
                    // First check that the removed attribute exists
                    if ( !tempEntry.containsAttribute( attributeType ) )
                    {
                        String msg = I18n.err( I18n.ERR_55, attributeType );
                        LOG.error( msg );
                        throw new LdapNoSuchAttributeException( msg );
                    }

                    // We may have to remove the attribute or only some values
                    if ( attribute.size() == 0 )
                    {
                        // No value : we have to remove the entire attribute
                        tempEntry.removeAttributes( attributeType );
                    }
                    else
                    {
                        currentAttribute = tempEntry.get( attributeType );

                        // Now remove all the values
                        for ( Value<?> value : attribute )
                        {
                            // We can only remove existing values.
                            if ( currentAttribute.contains( value ) )
                            {
                                currentAttribute.remove( value );
                            }
                            else
                            {
                                String msg = I18n.err( I18n.ERR_56, attributeType );
                                LOG.error( msg );
                                throw new LdapNoSuchAttributeException( msg );
                            }
                        }

                        // If the current attribute is empty, we have to remove
                        // it from the entry
                        if ( currentAttribute.size() == 0 )
                        {
                            tempEntry.removeAttributes( attributeType );
                        }
                    }

                    break;

                case REPLACE_ATTRIBUTE:
                    // The replaced attribute might not exist, it will then be a Add
                    // If there is no value, then the attribute will be removed
                    if ( !tempEntry.containsAttribute( attributeType ) )
                    {
                        if ( attribute.size() == 0 )
                        {
                            // Ignore the modification, as the attributeType does not
                            // exists in the entry
                            break;
                        }
                        else
                        {
                            // Create the new Attribute
                            EntryAttribute newAttribute = createNewAttribute( attribute );

                            tempEntry.put( newAttribute );
                        }
                    }
                    else
                    {
                        if ( attribute.size() == 0 )
                        {
                            // Remove the attribute from the entry
                            tempEntry.removeAttributes( attributeType );
                        }
                        else
                        {
                            // Replace the existing values with the new values
                            // This is done by removing the Attribute
                            tempEntry.removeAttributes( attributeType );

                            // Create the new Attribute
                            EntryAttribute newAttribute = createNewAttribute( attribute );

                            tempEntry.put( newAttribute );
                        }
                    }

                    break;
            }
        }

        // Ok, we have created the modified entry. We now have to check that it's a valid 
        // entry wrt the schema.
        // We have to check that :
        // - the rdn values are present in the entry
        // - the objectClasses inheritence is correct
        // - all the MUST are present
        // - all the attribute are in MUST and MAY, except fo the extensibleObeject OC
        // is present
        // - We haven't removed a part of the RDN
        check( dn, tempEntry );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        // A modification on a simple entry will be done in three steps :
        // - get the original entry (it should already been in the context)
        // - apply the modification on it
        // - check that the entry is still correct
        // - add the operational attributes (modifiersName/modifyTimeStamp)
        // - store the modified entry on the backend.
        //
        // A modification done on the schema is a bit different, as there is two more
        // steps
        // - We have to update the registries
        // - We have to modify the ou=schemaModifications entry
        //

        // First, check that the entry is either a subschemaSubentry or a schema element.
        // This is the case if it's a child of cn=schema or ou=schema
        DN dn = modifyContext.getDn();

        // Gets the stored entry on which the modification must be applied
        if ( dn.equals( subschemaSubentryDn ) )
        {
            LOG.debug( "Modification attempt on schema subentry {}: \n{}", dn, modifyContext );

            // We can get rid of the modifiersName and modifyTimestamp, they are useless.
            List<Modification> mods = modifyContext.getModItems();
            List<Modification> cleanMods = new ArrayList<Modification>();

            for ( Modification mod : mods )
            {
                AttributeType at = ( ( DefaultModification ) mod ).getAttribute().getAttributeType();

                if ( !MODIFIERS_NAME_ATTRIBUTE_TYPE.equals( at ) && !MODIFY_TIMESTAMP_ATTRIBUTE_TYPE.equals( at ) )
                {
                    cleanMods.add( mod );
                }
            }

            modifyContext.setModItems( cleanMods );

            // Now that the entry has been modified, update the SSSE
            schemaSubEntryManager.modifySchemaSubentry( modifyContext, modifyContext
                .hasRequestControl( CascadeControl.CONTROL_OID ) );

            return;
        }

        Entry entry = modifyContext.getEntry();
        List<Modification> modifications = modifyContext.getModItems();
        checkModifyEntry( dn, entry, modifications );

        next.modify( modifyContext );
    }


    /**
     * Filter the attributes by removing the ones which are not allowed
     */
    // This will suppress PMD.EmptyCatchBlock warnings in this method
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void filterAttributeTypes( SearchingOperationContext operation, ClonedServerEntry result )
    {
        if ( operation.getReturningAttributes() == null )
        {
            return;
        }

        for ( AttributeTypeOptions attrOptions : operation.getReturningAttributes() )
        {
            EntryAttribute attribute = result.get( attrOptions.getAttributeType() );

            if ( attrOptions.hasOption() )
            {
                for ( String option : attrOptions.getOptions() )
                {
                    if ( "binary".equalsIgnoreCase( option ) )
                    {
                        continue;
                    }
                    else
                    {
                        try
                        {
                            if ( result.contains( attribute ) )
                            {
                                result.remove( attribute );
                            }
                        }
                        catch ( LdapException ne )
                        {
                            // Do nothings
                        }
                        break;
                    }
                }
            }
        }
    }


    private void filterBinaryAttributes( Entry entry ) throws LdapException
    {
        /*
         * start converting values of attributes to byte[]s which are not
         * human readable and those that are in the binaries set
         */
        for ( EntryAttribute attribute : entry )
        {
            if ( !attribute.getAttributeType().getSyntax().isHumanReadable() )
            {
                List<Value<?>> binaries = new ArrayList<Value<?>>();

                for ( Value<?> value : attribute )
                {
                    attribute.add( value );
                    binaries.add( new BinaryValue( attribute.getAttributeType(), value.getBytes() ) );
                }

                attribute.clear();

                for ( Value<?> value : binaries )
                {
                    attribute.add( value );
                }
            }
        }
    }

    /**
     * A special filter over entry attributes which replaces Attribute String values with their respective byte[]
     * representations using schema information and the value held in the JNDI environment property:
     * <code>java.naming.ldap.attributes.binary</code>.
     *
     * @see <a href= "http://java.sun.com/j2se/1.4.2/docs/guide/jndi/jndi-ldap-gl.html#binary">
     *      java.naming.ldap.attributes.binary</a>
     */
    private class BinaryAttributeFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry result ) throws Exception
        {
            filterBinaryAttributes( result );
            return true;
        }
    }

    
    /**
     * Filters objectClass attribute to inject top when not present.
     */
    private class TopFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry result ) throws Exception
        {
            filterAttributeTypes( operation, result );
            return true;
        }
    }


    /**
     * Check that all the attributes exist in the schema for this entry.
     * 
     * We also check the syntaxes
     */
    private void check( DN dn, Entry entry ) throws LdapException
    {
        // ---------------------------------------------------------------
        // First, make sure all attributes are valid schema defined attributes
        // ---------------------------------------------------------------

        for ( AttributeType attributeType : entry.getAttributeTypes() )
        {
            if ( !schemaManager.getAttributeTypeRegistry().contains( attributeType.getName() ) )
            {
                throw new LdapInvalidAttributeTypeException( I18n.err( I18n.ERR_275, attributeType.getName() ) );
            }
        }

        // We will check some elements :
        // 1) the entry must have all the MUST attributes of all its ObjectClass
        // 2) The SingleValued attributes must be SingleValued
        // 3) No attributes should be used if they are not part of MUST and MAY
        // 3-1) Except if the extensibleObject ObjectClass is used
        // 3-2) or if the AttributeType is COLLECTIVE
        // 4) We also check that for H-R attributes, we have a valid String in the values
        EntryAttribute objectClassAttr = entry.get( OBJECT_CLASS_AT );

        // Protect the server against a null objectClassAttr
        // It can be the case if the user forgot to add it to the entry ...
        // In this case, we create an new one, empty
        if ( objectClassAttr == null )
        {
            objectClassAttr = new DefaultEntryAttribute( OBJECT_CLASS_AT );
        }

        List<ObjectClass> ocs = new ArrayList<ObjectClass>();

        alterObjectClasses( objectClassAttr );

        // Now we can process the MUST and MAY attributes
        Set<String> must = getAllMust( objectClassAttr );
        Set<String> allowed = getAllAllowed( objectClassAttr, must );

        boolean hasExtensibleObject = getObjectClasses( objectClassAttr, ocs );

        // As we now have all the ObjectClasses updated, we have
        // to check that we don't have conflicting ObjectClasses
        assertObjectClasses( dn, ocs );

        assertRequiredAttributesPresent( dn, entry, must );
        assertNumberOfAttributeValuesValid( entry );

        if ( !hasExtensibleObject )
        {
            assertAllAttributesAllowed( dn, entry, allowed );
        }

        // Check the attributes values and transform them to String if necessary
        assertHumanReadable( entry );

        // Now check the syntaxes
        assertSyntaxes( entry );

        assertRdn( dn, entry );
    }


    private void checkOcSuperior( Entry entry ) throws LdapException
    {
        // handle the m-supObjectClass meta attribute
        EntryAttribute supOC = entry.get( MetaSchemaConstants.M_SUP_OBJECT_CLASS_AT );

        if ( supOC != null )
        {
            ObjectClassTypeEnum ocType = ObjectClassTypeEnum.STRUCTURAL;

            if ( entry.get( MetaSchemaConstants.M_TYPE_OBJECT_CLASS_AT ) != null )
            {
                String type = entry.get( MetaSchemaConstants.M_TYPE_OBJECT_CLASS_AT ).getString();
                ocType = ObjectClassTypeEnum.getClassType( type );
            }

            // First check that the inheritence scheme is correct.
            // 1) If the ocType is ABSTRACT, it should not have any other SUP not ABSTRACT
            for ( Value<?> sup : supOC )
            {
                try
                {
                    String supName = sup.getString();

                    ObjectClass superior = schemaManager.getObjectClassRegistry().lookup( supName );

                    switch ( ocType )
                    {
                        case ABSTRACT:
                            if ( !superior.isAbstract() )
                            {
                                String message = I18n.err( I18n.ERR_57 );
                                LOG.error( message );
                                throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, message );
                            }

                            break;

                        case AUXILIARY:
                            if ( !superior.isAbstract() && !superior.isAuxiliary() )
                            {
                                String message = I18n.err( I18n.ERR_58 );
                                LOG.error( message );
                                throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, message );
                            }

                            break;

                        case STRUCTURAL:
                            break;
                    }
                }
                catch ( LdapException ne )
                {
                    // The superior OC does not exist : this is an error
                    String message = I18n.err( I18n.ERR_59 );
                    LOG.error( message );
                    throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, message );
                }
            }
        }
    }


    /**
     * Check that all the attributes exist in the schema for this entry.
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        DN name = addContext.getDn();
        Entry entry = addContext.getEntry();

        check( name, entry );

        // Special checks for the MetaSchema branch
        if ( name.isChildOf( schemaBaseDN ) )
        {
            // get the schema name
            String schemaName = getSchemaName( name );

            if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.META_SCHEMA_OC ) )
            {
                next.add( addContext );

                if ( schemaManager.isSchemaLoaded( schemaName ) )
                {
                    // Update the OC superiors for each added ObjectClass
                    computeSuperiors();
                }
            }
            else if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.META_OBJECT_CLASS_OC ) )
            {
                // This is an ObjectClass addition
                checkOcSuperior( addContext.getEntry() );

                next.add( addContext );

                // Update the structures now that the schema element has been added
                Schema schema = schemaManager.getLoadedSchema( schemaName );

                if ( ( schema != null ) && schema.isEnabled() )
                {
                    String ocName = entry.get( MetaSchemaConstants.M_NAME_AT ).getString();
                    ObjectClass addedOC = schemaManager.getObjectClassRegistry().lookup( ocName );
                    computeSuperior( addedOC );
                }
            }
            else if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.META_ATTRIBUTE_TYPE_OC ) )
            {

                // This is an AttributeType addition
                next.add( addContext );
            }
            else
            {
                next.add( addContext );
            }

        }
        else
        {
            next.add( addContext );
        }
    }


    private String getSchemaName( DN dn ) throws LdapException
    {
        if ( dn.size() < 2 )
        {
            throw new LdapException( I18n.err( I18n.ERR_276 ) );
        }

        RDN rdn = dn.getRdn( 1 );
        return rdn.getNormValue().getString();
    }


    /**
     * Checks to see if an attribute is required by as determined from an entry's
     * set of objectClass attribute values.
     *
     * @return true if the objectClass values require the attribute, false otherwise
     * @throws Exception if the attribute is not recognized
     */
    private void assertAllAttributesAllowed( DN dn, Entry entry, Set<String> allowed ) throws LdapException
    {
        // Never check the attributes if the extensibleObject objectClass is
        // declared for this entry
        EntryAttribute objectClass = entry.get( OBJECT_CLASS_AT );

        if ( objectClass.contains( SchemaConstants.EXTENSIBLE_OBJECT_OC ) )
        {
            return;
        }

        for ( EntryAttribute attribute : entry )
        {
            String attrOid = attribute.getAttributeType().getOid();

            AttributeType attributeType = attribute.getAttributeType();

            if ( !attributeType.isCollective() && ( attributeType.getUsage() == UsageEnum.USER_APPLICATIONS )
                && !allowed.contains( attrOid ) )
            {
                throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, I18n.err( I18n.ERR_277,
                    attribute.getUpId(), dn.getName() ) );
            }
        }
    }


    /**
     * Checks to see number of values of an attribute conforms to the schema
     */
    private void assertNumberOfAttributeValuesValid( Entry entry ) throws LdapInvalidAttributeValueException
    {
        for ( EntryAttribute attribute : entry )
        {
            assertNumberOfAttributeValuesValid( attribute );
        }
    }


    /**
     * Checks to see numbers of values of attributes conforms to the schema
     */
    private void assertNumberOfAttributeValuesValid( EntryAttribute attribute )
        throws LdapInvalidAttributeValueException
    {
        if ( attribute.size() > 1 && attribute.getAttributeType().isSingleValued() )
        {
            throw new LdapInvalidAttributeValueException( ResultCodeEnum.CONSTRAINT_VIOLATION, I18n.err( I18n.ERR_278,
                attribute.getUpId() ) );
        }
    }


    /**
     * Checks to see the presence of all required attributes within an entry.
     */
    private void assertRequiredAttributesPresent( DN dn, Entry entry, Set<String> must ) throws LdapException
    {
        for ( EntryAttribute attribute : entry )
        {
            must.remove( attribute.getAttributeType().getOid() );
        }

        if ( must.size() != 0 )
        {
            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, I18n.err( I18n.ERR_279,
                must, dn.getName() ) );
        }
    }


    /**
     * Checck that OC does not conflict :
     * - we can't have more than one STRUCTURAL OC unless they are in the same
     * inheritance tree
     * - we must have at least one STRUCTURAL OC
     */
    private void assertObjectClasses( DN dn, List<ObjectClass> ocs ) throws LdapException
    {
        Set<ObjectClass> structuralObjectClasses = new HashSet<ObjectClass>();

        /*
         * Since the number of ocs present in an entry is small it's not 
         * so expensive to take two passes while determining correctness
         * since it will result in clear simple code instead of a deep nasty
         * for loop with nested loops.  Plus after the first pass we can
         * quickly know if there are no structural object classes at all.
         */

        // --------------------------------------------------------------------
        // Extract all structural objectClasses within the entry
        // --------------------------------------------------------------------
        for ( ObjectClass oc : ocs )
        {
            if ( oc.isStructural() )
            {
                structuralObjectClasses.add( oc );
            }
        }

        // --------------------------------------------------------------------
        // Throw an error if no STRUCTURAL objectClass are found.
        // --------------------------------------------------------------------

        if ( structuralObjectClasses.isEmpty() )
        {
            String message = I18n.err( I18n.ERR_60, dn );
            LOG.error( message );
            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, message );
        }

        // --------------------------------------------------------------------
        // Put all structural object classes into new remaining container and
        // start removing any which are superiors of others in the set.  What
        // is left in the remaining set will be unrelated structural 
        /// objectClasses.  If there is more than one then we have a problem.
        // --------------------------------------------------------------------

        Set<ObjectClass> remaining = new HashSet<ObjectClass>( structuralObjectClasses.size() );
        remaining.addAll( structuralObjectClasses );

        for ( ObjectClass oc : structuralObjectClasses )
        {
            if ( oc.getSuperiors() != null )
            {
                for ( ObjectClass superClass : oc.getSuperiors() )
                {
                    if ( superClass.isStructural() )
                    {
                        remaining.remove( superClass );
                    }
                }
            }
        }

        // Like the highlander there can only be one :).
        if ( remaining.size() > 1 )
        {
            String message = I18n.err( I18n.ERR_61, dn, remaining );
            LOG.error( message );
            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION, message );
        }
    }


    /**
     * Check the entry attributes syntax, using the syntaxCheckers
     */
    private void assertSyntaxes( Entry entry ) throws LdapException
    {
        // First, loop on all attributes
        for ( EntryAttribute attribute : entry )
        {
            AttributeType attributeType = attribute.getAttributeType();
            SyntaxChecker syntaxChecker = attributeType.getSyntax().getSyntaxChecker();

            if ( syntaxChecker instanceof OctetStringSyntaxChecker )
            {
                // This is a speedup : no need to check the syntax of any value
                // if all the syntaxes are accepted...
                continue;
            }

            // Then loop on all values
            for ( Value<?> value : attribute )
            {
                if ( value.isValid() )
                {
                    // No need to validate something which is already ok
                    continue;
                }

                try
                {
                    syntaxChecker.assertSyntax( value.get() );
                }
                catch ( Exception ne )
                {
                    String message = I18n.err( I18n.ERR_280, value.getString(), attribute.getUpId() );
                    LOG.info( message );

                    throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, message );
                }
            }
        }
    }


    private void assertRdn( DN dn, Entry entry ) throws LdapException
    {
        for ( AVA atav : dn.getRdn() )
        {
            EntryAttribute attribute = entry.get( atav.getNormType() );

            if ( ( attribute == null ) || ( !attribute.contains( atav.getNormValue() ) ) )
            {
                String message = I18n.err( I18n.ERR_62, dn, atav.getUpType() );
                LOG.error( message );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, message );
            }
        }
    }


    /**
     * Check a String attribute to see if there is some byte[] value in it.
     * 
     * If this is the case, try to change it to a String value.
     */
    private boolean checkHumanReadable( EntryAttribute attribute ) throws LdapException
    {
        boolean isModified = false;

        // Loop on each values
        for ( Value<?> value : attribute )
        {
            if ( value instanceof StringValue )
            {
                continue;
            }
            else if ( value instanceof BinaryValue )
            {
                // we have a byte[] value. It should be a String UTF-8 encoded
                // Let's transform it
                try
                {
                    String valStr = new String( value.getBytes(), "UTF-8" );
                    attribute.remove( value );
                    attribute.add( valStr );
                    isModified = true;
                }
                catch ( UnsupportedEncodingException uee )
                {
                    throw new LdapException( I18n.err( I18n.ERR_281 ) );
                }
            }
            else
            {
                throw new LdapException( I18n.err( I18n.ERR_282 ) );
            }
        }

        return isModified;
    }


    /**
     * Check a binary attribute to see if there is some String value in it.
     * 
     * If this is the case, try to change it to a binary value.
     */
    private boolean checkNotHumanReadable( EntryAttribute attribute ) throws LdapException
    {
        boolean isModified = false;

        // Loop on each values
        for ( Value<?> value : attribute )
        {
            if ( value instanceof BinaryValue )
            {
                continue;
            }
            else if ( value instanceof StringValue )
            {
                // We have a String value. It should be a byte[]
                // Let's transform it
                try
                {
                    byte[] valBytes = value.getString().getBytes( "UTF-8" );

                    attribute.remove( value );
                    attribute.add( valBytes );
                    isModified = true;
                }
                catch ( UnsupportedEncodingException uee )
                {
                    String message = I18n.err( I18n.ERR_63 );
                    LOG.error( message );
                    throw new LdapException( message );
                }
            }
            else
            {
                String message = I18n.err( I18n.ERR_64 );
                LOG.error( message );
                throw new LdapException( message );
            }
        }

        return isModified;
    }


    /**
     * Check that all the attribute's values which are Human Readable can be transformed
     * to valid String if they are stored as byte[], and that non Human Readable attributes
     * stored as String can be transformed to byte[]
     */
    private void assertHumanReadable( Entry entry ) throws LdapException
    {
        boolean isModified = false;

        Entry clonedEntry = null;

        // Loops on all attributes
        for ( EntryAttribute attribute : entry )
        {
            AttributeType attributeType = attribute.getAttributeType();

            // If the attributeType is H-R, check all of its values
            if ( attributeType.getSyntax().isHumanReadable() )
            {
                isModified = checkHumanReadable( attribute );
            }
            else
            {
                isModified = checkNotHumanReadable( attribute );
            }

            // If we have a returned attribute, then we need to store it
            // into a new entry
            if ( isModified )
            {
                if ( clonedEntry == null )
                {
                    clonedEntry = ( Entry ) entry.clone();
                }

                // Switch the attributes
                clonedEntry.put( attribute );

                isModified = false;
            }
        }

        if ( clonedEntry != null )
        {
            entry = clonedEntry;
        }
    }
}
