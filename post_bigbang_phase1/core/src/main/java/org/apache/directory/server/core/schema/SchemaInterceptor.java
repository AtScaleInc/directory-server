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


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.*;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.ObjectClassRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.*;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.message.*;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaUtils;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.schema.syntax.AcceptAllSyntaxChecker;
import org.apache.directory.shared.ldap.schema.syntax.SyntaxChecker;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.EmptyEnumeration;
import org.apache.directory.shared.ldap.util.SingletonEnumeration;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.*;
import java.io.UnsupportedEncodingException;
import java.util.*;


/**
 * An {@link org.apache.directory.server.core.interceptor.Interceptor} that manages and enforces schemas.
 *
 * @todo Better interceptor description required.

 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SchemaInterceptor extends BaseInterceptor
{
    /** The LoggerFactory used by this Interceptor */
    private static Logger log = LoggerFactory.getLogger( SchemaInterceptor.class );

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String[] SCHEMA_SUBENTRY_RETURN_ATTRIBUTES =
            new String[] { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES };




    /** Speedup for logs */
    private static final boolean IS_DEBUG = log.isDebugEnabled();

    /**
     * the root nexus to all database partitions
     */
    private PartitionNexus nexus;

    /**
     * a binary attribute tranforming filter: String -> byte[]
     */
    private BinaryAttributeFilter binaryAttributeFilter;

    private TopFilter topFilter;

    private List<SearchResultFilter> filters = new ArrayList<SearchResultFilter>();

    /**
     * the global schema object registries
     */
    private Registries registries;

    /** A normalized form for the SubschemaSubentry DN */
    private String subschemaSubentryDnNorm;

    /**
     * the normalized name for the schema modification attributes
     */
    private LdapDN schemaModificationAttributesDN;

    private SchemaOperationControl schemaManager;

    private SchemaService schemaService;

    // the base DN (normalized) of the schema partition
    private LdapDN schemaBaseDN;

    /** A map used to store all the objectClasses superiors */
    private Map<String, List<ObjectClass>> superiors;

    /** A map used to store all the objectClasses may attributes */
    private Map<String, List<AttributeType>> allMay;

    /** A map used to store all the objectClasses must */
    private Map<String, List<AttributeType>> allMust;

    /** A map used to store all the objectClasses allowed attributes (may + must) */
    private Map<String, List<AttributeType>> allowed;


    /**
     * Initialize the Schema Service
     *
     * @param directoryService the directory service core
     * @throws NamingException if there are problems during initialization
     */
    public void init( DirectoryService directoryService ) throws NamingException
    {
        if ( IS_DEBUG )
        {
            log.debug( "Initializing SchemaInterceptor..." );
        }

        nexus = directoryService.getPartitionNexus();
        registries = directoryService.getRegistries();
        binaryAttributeFilter = new BinaryAttributeFilter();
        topFilter = new TopFilter();
        filters.add( binaryAttributeFilter );
        filters.add( topFilter );

        schemaBaseDN = new LdapDN( "ou=schema" );
        schemaBaseDN.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
        schemaService = directoryService.getSchemaService();
        schemaManager = directoryService.getSchemaService().getSchemaControl();

        // stuff for dealing with subentries (garbage for now)
        String subschemaSubentry = ( String ) nexus.getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        LdapDN subschemaSubentryDn = new LdapDN( subschemaSubentry );
        subschemaSubentryDn.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
        subschemaSubentryDnNorm = subschemaSubentryDn.getNormName();

        schemaModificationAttributesDN = new LdapDN( "cn=schemaModifications,ou=schema" );
        schemaModificationAttributesDN.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );

        computeSuperiors();

        if ( IS_DEBUG )
        {
            log.debug( "SchemaInterceptor Initialized !" );
        }
    }


    /**
     * Compute the MUST attributes for an objectClass. This method gather all the
     * MUST from all the objectClass and its superors.
     *
     * @param atSeen ???
     * @param objectClass the object class to gather MUST attributes for
     * @throws NamingException if there are problems resolving schema entitites
     */
    private void computeMustAttributes( ObjectClass objectClass, Set<String> atSeen ) throws NamingException
    {
        List<ObjectClass> parents = superiors.get( objectClass.getOid() );

        List<AttributeType> mustList = new ArrayList<AttributeType>();
        List<AttributeType> allowedList = new ArrayList<AttributeType>();
        Set<String> mustSeen = new HashSet<String>();

        allMust.put( objectClass.getOid(), mustList );
        allowed.put( objectClass.getOid(), allowedList );

        for ( ObjectClass parent:parents )
        {
            AttributeType[] mustParent = parent.getMustList();

            if ( ( mustParent != null ) && ( mustParent.length != 0 ) )
            {
                for ( AttributeType attributeType:mustParent )
                {
                    String oid = attributeType.getOid();

                    if ( !mustSeen.contains( oid ) )
                    {
                        mustSeen.add(  oid  );
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
     * @throws NamingException with problems accessing registries
     */
    private void computeMayAttributes( ObjectClass objectClass, Set<String> atSeen ) throws NamingException
    {
        List<ObjectClass> parents = superiors.get( objectClass.getOid() );

        List<AttributeType> mayList = new ArrayList<AttributeType>();
        Set<String> maySeen = new HashSet<String>();
        List<AttributeType> allowedList = allowed.get( objectClass.getOid() );


        allMay.put( objectClass.getOid(), mayList );

        for ( ObjectClass parent:parents )
        {
            AttributeType[] mustParent = parent.getMustList();

            if ( ( mustParent != null ) && ( mustParent.length != 0 ) )
            {
                for ( AttributeType attributeType:mustParent )
                {
                    String oid = attributeType.getOid();

                    if ( !maySeen.contains( oid ) )
                    {
                        maySeen.add(  oid  );
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
    private void computeOCSuperiors( ObjectClass objectClass, List<ObjectClass> superiors, Set<String> ocSeen ) throws NamingException
    {
        ObjectClass[] parents = objectClass.getSuperClasses();

        // Loop on all the objectClass superiors
        if ( ( parents != null ) && ( parents.length != 0 ) )
        {
            for ( ObjectClass parent:parents )
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
     * Compute all ObjectClasses superiors, MAY and MUST attributes.
     * @throws NamingException
     */
    private void computeSuperiors() throws NamingException
    {
        Iterator<ObjectClass> objectClasses = registries.getObjectClassRegistry().iterator();
        superiors = new HashMap<String, List<ObjectClass>>();
        allMust = new HashMap<String, List<AttributeType>>();
        allMay = new HashMap<String, List<AttributeType>>();
        allowed = new HashMap<String, List<AttributeType>>();

        while ( objectClasses.hasNext() )
        {
            List<ObjectClass> ocSuperiors = new ArrayList<ObjectClass>();

            ObjectClass objectClass = objectClasses.next();
            superiors.put( objectClass.getOid(), ocSuperiors );

            computeOCSuperiors( objectClass, ocSuperiors, new HashSet<String>() );

            Set<String> atSeen = new HashSet<String>();
            computeMustAttributes( objectClass, atSeen );
            computeMayAttributes( objectClass, atSeen );

            superiors.put( objectClass.getName(), ocSuperiors );
        }
    }

    /**
     *
     */
    public NamingEnumeration<SearchResult> list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws NamingException
    {
        NamingEnumeration<SearchResult> result = nextInterceptor.list( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();
        return new SearchResultFilteringEnumeration( result, new SearchControls(), invocation, binaryAttributeFilter, "List Schema Filter" );
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

        for ( String attribute:attributes )
        {
            // Skip special attributes
            if ( ( SchemaConstants.ALL_USER_ATTRIBUTES.equals( attribute ) ) ||
                ( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES.equals( attribute ) ) ||
                ( SchemaConstants.NO_ATTRIBUTE.equals( attribute ) ) )
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
            	if ( registries.getOidRegistry().hasOid( attribute ) )
            	{
	                String oid = registries.getOidRegistry().getOid( attribute );

            		// The attribute must be an AttributeType
	                if ( registries.getAttributeTypeRegistry().hasAttributeType( oid ) )
	                {
		                if ( !filteredAttrs.containsKey( oid ) )
		                {
		                	// Ok, we can add the attribute to the list of filtered attributes
		                    filteredAttrs.put( oid, attribute );
		                }
	                }
            	}

                hasAttributes = true;
            }
            catch ( NamingException ne )
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
        if (  filteredAttrs.size() == 0 )
        {
        	// We just have to pass the special 1.1 attribute,
        	// as we don't want to return any attribute
        	searchCtls.setReturningAttributes( SchemaConstants.NO_ATTRIBUTE_ARRAY );
        	return;
        }

        // Some attributes have been removed. let's modify the searchControl
        String[] newAttributesList = new String[filteredAttrs.size()];

        int pos = 0;

        for ( String key:filteredAttrs.keySet() )
        {
            newAttributesList[pos++] = filteredAttrs.get( key );
        }

        searchCtls.setReturningAttributes( newAttributesList );
    }


    /**
     *
     */
    public NamingEnumeration<SearchResult> search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws NamingException
    {
        LdapDN base = opContext.getDn();
        SearchControls searchCtls = opContext.getSearchControls();
        ExprNode filter = opContext.getFilter();

        // We have to eliminate bad attributes from the request, accordingly
        // to RFC 2251, chap. 4.5.1. Basically, all unknown attributes are removed
        // from the list
        filterAttributesToReturn( searchCtls );

        String baseNormForm = ( base.isNormalized() ? base.getNormName() : base.toNormName() );

        // Deal with the normal case : searching for a normal value (not subSchemaSubEntry
        if ( !subschemaSubentryDnNorm.equals( baseNormForm ) )
        {
            NamingEnumeration<SearchResult> result = nextInterceptor.search( opContext );

            Invocation invocation = InvocationStack.getInstance().peek();

            if ( searchCtls.getReturningAttributes() != null )
            {
                return new SearchResultFilteringEnumeration( result, new SearchControls(), invocation, topFilter, "Search Schema Filter top" );
            }

            return new SearchResultFilteringEnumeration( result, searchCtls, invocation, filters, "Search Schema Filter" );
        }

        // The user was searching into the subSchemaSubEntry
        // Thgis kind of search _must_ be limited to OBJECT scope (the subSchemaSubEntry
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

                if ( node.getValue() instanceof String )
                {
                    objectClass = ( String ) node.getValue();
                }
                else
                {
                    objectClass = node.getValue().toString();
                }

                String objectClassOid = null;

                if ( registries.getObjectClassRegistry().hasObjectClass( objectClass ) )
                {
                    objectClassOid = registries.getObjectClassRegistry().lookup( objectClass ).getOid();
                }
                else
                {
                    return new EmptyEnumeration<SearchResult>();
                }

                String nodeOid = registries.getOidRegistry().getOid( node.getAttribute() );

                // see if node attribute is objectClass
                if ( nodeOid.equals( SchemaConstants.OBJECT_CLASS_AT_OID )
                    && ( objectClassOid.equals( SchemaConstants.TOP_OC_OID ) ||
                        objectClassOid.equals( SchemaConstants.SUBSCHEMA_OC_OID ) )
                    && ( node instanceof EqualityNode ) )
                {
                    // call.setBypass( true );
                    Attributes attrs = schemaService.getSubschemaEntry( searchCtls.getReturningAttributes() );
                    SearchResult result = new ServerSearchResult( base.toString(), null, attrs );
                    return new SingletonEnumeration<SearchResult>( result );
                }
                else
                {
                    return new EmptyEnumeration<SearchResult>();
                }
            }
            else if ( filter instanceof PresenceNode )
            {
                PresenceNode node = ( PresenceNode ) filter;

                // see if node attribute is objectClass
                if ( node.getAttribute().equals( SchemaConstants.OBJECT_CLASS_AT_OID ) )
                {
                    // call.setBypass( true );
                    Attributes attrs = schemaService.getSubschemaEntry( searchCtls.getReturningAttributes() );
                    SearchResult result = new ServerSearchResult( base.toString(), null, attrs, false );
                    return new SingletonEnumeration<SearchResult>( result );
                }
            }
        }

        // In any case not handled previously, just return an empty result
        return new EmptyEnumeration<SearchResult>();
    }


    /**
     * Search for an entry, using its DN. Binary attributes and ObjectClass attribute are removed.
     */
    public Attributes lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws NamingException
    {
        Attributes result = nextInterceptor.lookup( opContext );
        
        if ( result == null )
        {
            return null;
        }

        filterBinaryAttributes( result );
        filterObjectClass( result );

        return result;
    }


    private void getSuperiors( ObjectClass oc, Set<String> ocSeen, List<ObjectClass> result ) throws NamingException
    {
        for ( ObjectClass parent:oc.getSuperClasses() )
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

    /**
     * Checks to see if an attribute is required by as determined from an entry's
     * set of objectClass attribute values.
     *
     * @param attrId the attribute to test if required by a set of objectClass values
     * @param objectClass the objectClass values
     * @return true if the objectClass values require the attribute, false otherwise
     * @throws NamingException if the attribute is not recognized
     */
    private boolean isRequired( String attrId, Attribute objectClass ) throws NamingException
    {
        OidRegistry oidRegistry = registries.getOidRegistry();
        ObjectClassRegistry registry = registries.getObjectClassRegistry();

        if ( !oidRegistry.hasOid( attrId ) )
        {
            return false;
        }

        String attrOid = oidRegistry.getOid( attrId );
        
        for ( int ii = 0; ii < objectClass.size(); ii++ )
        {
            ObjectClass ocSpec = registry.lookup( ( String ) objectClass.get( ii ) );
            
            for ( AttributeType must:ocSpec.getMustList() )
            {
                if ( must.getOid().equals( attrOid ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks to see if removing a set of attributes from an entry completely removes
     * that attribute's values.  If change has zero size then all attributes are
     * presumed to be removed.
     *
     * @param change
     * @param entry
     * @return
     * @throws NamingException
     */
    private boolean isCompleteRemoval( Attribute change, Attributes entry ) throws NamingException
    {
        // if change size is 0 then all values are deleted then we're in trouble
        if ( change.size() == 0 )
        {
            return true;
        }

        // can't do math to figure our if all values are removed since some
        // values in the modify request may not be in the entry.  we need to
        // remove the values from a cloned version of the attribute and see
        // if nothing is left.
        Attribute changedEntryAttr = ( Attribute ) entry.get( change.getID() ).clone();
        
        for ( int jj = 0; jj < change.size(); jj++ )
        {
            changedEntryAttr.remove( change.get( jj ) );
        }

        return changedEntryAttr.size() == 0;
    }

    
    /**
     * 
     * @param modOp
     * @param changes
     * @param existing
     * @return
     * @throws NamingException
     */
    private Attribute getResultantObjectClasses( int modOp, Attribute changes, Attribute existing ) throws NamingException
    {
        if ( ( changes == null ) && ( existing == null ) )
        {
            return new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        }

        if ( changes == null )
        {
            return existing;
        }

        if ( (existing == null ) && ( modOp == DirContext.ADD_ATTRIBUTE ) )
        {
            return changes;
        }
        else if ( existing == null )
        {
            return new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        }

        switch ( modOp )
        {
            case ( DirContext.ADD_ATTRIBUTE  ):
                return AttributeUtils.getUnion( existing, changes );
            
            case ( DirContext.REPLACE_ATTRIBUTE  ):
                return ( Attribute ) changes.clone();
            
            case ( DirContext.REMOVE_ATTRIBUTE  ):
                return AttributeUtils.getDifference( existing, changes );
            
            default:
                throw new InternalError( "" );
        }
    }


    private boolean getObjectClasses( Attribute objectClasses, List<ObjectClass> result ) throws NamingException
    {
        Set<String> ocSeen = new HashSet<String>();
        ObjectClassRegistry registry = registries.getObjectClassRegistry();

        // We must select all the ObjectClasses, except 'top',
        // but including all the inherited ObjectClasses
        NamingEnumeration<String> ocs = (NamingEnumeration<String>)objectClasses.getAll();
        boolean hasExtensibleObject = false;


        while ( ocs.hasMoreElements() )
        {
            String objectClassName = ocs.nextElement();

            if ( SchemaConstants.TOP_OC.equals( objectClassName ) )
            {
                continue;
            }

            if ( SchemaConstants.EXTENSIBLE_OBJECT_OC.equalsIgnoreCase( objectClassName ) )
            {
                hasExtensibleObject = true;
            }

            ObjectClass oc = registry.lookup( objectClassName );

            // Add all unseen objectclasses to the list, except 'top'
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

    private Set<String> getAllMust( NamingEnumeration<String> objectClasses ) throws NamingException
    {
        Set<String> must = new HashSet<String>();

        // Loop on all objectclasses
        while ( objectClasses.hasMoreElements() )
        {
            String ocName = objectClasses.nextElement();
            ObjectClass oc = registries.getObjectClassRegistry().lookup( ocName );

            AttributeType[] types = oc.getMustList();

            // For each objectClass, loop on all MUST attributeTypes, if any
            if ( ( types != null ) && ( types.length > 0 ) )
            {
                for ( AttributeType type:types )
                {
                    must.add( type.getOid() );
                }
            }
        }

        return must;
    }

    private Set<String> getAllAllowed( NamingEnumeration<String> objectClasses, Set<String> must ) throws NamingException
    {
        Set<String> allowed = new HashSet<String>( must );

        // Add the 'ObjectClass' attribute ID
        allowed.add( registries.getOidRegistry().getOid( SchemaConstants.OBJECT_CLASS_AT ) );

        // Loop on all objectclasses
        while ( objectClasses.hasMoreElements() )
        {
            String ocName = objectClasses.nextElement();
            ObjectClass oc = registries.getObjectClassRegistry().lookup( ocName );

            AttributeType[] types = oc.getMayList();

            // For each objectClass, loop on all MAY attributeTypes, if any
            if ( ( types != null ) && ( types.length > 0 ) )
            {
                for ( AttributeType type:types )
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
     * @throws NamingException if there are problems 
     */
    private void alterObjectClasses( Attribute objectClassAttr ) throws NamingException
    {
        Set<String> objectClasses = new HashSet<String>();
        Set<String> objectClassesUP = new HashSet<String>();

        // Init the objectClass list with 'top'
        objectClasses.add( SchemaConstants.TOP_OC );
        objectClassesUP.add( SchemaConstants.TOP_OC );
        
        // Construct the new list of ObjectClasses
        NamingEnumeration<String> ocList = (NamingEnumeration<String>)objectClassAttr.getAll();

        while ( ocList.hasMoreElements() )
        {
            String ocName = ocList.nextElement();

            if ( !ocName.equalsIgnoreCase( SchemaConstants.TOP_OC ) )
            {
                String ocLowerName = ocName.toLowerCase();

                ObjectClass objectClass = registries.getObjectClassRegistry().lookup( ocLowerName );

                if ( !objectClasses.contains( ocLowerName ) )
                {
                    objectClasses.add( ocLowerName );
                    objectClassesUP.add( ocName );
                }

                List<ObjectClass> ocSuperiors = superiors.get( objectClass.getOid() );

                if ( ocSuperiors != null )
                {
                    for ( ObjectClass oc:ocSuperiors )
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

        for ( String attribute:objectClassesUP )
        {
            objectClassAttr.add( attribute );
        }
    }

    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext opContext )
        throws NamingException
    {
        LdapDN oriChildName = opContext.getDn();

        Attributes entry = nexus.lookup( new LookupOperationContext( oriChildName ) );

        if ( oriChildName.startsWith( schemaBaseDN ) )
        {
            schemaManager.move( oriChildName, 
                opContext.getParent(), 
                opContext.getNewRdn(), 
                opContext.getDelOldDn(), entry,
                opContext.hasRequestControl( CascadeControl.CONTROL_OID ) );
        }
        
        next.moveAndRename( opContext );
    }


    public void move( NextInterceptor next, MoveOperationContext opContext ) throws NamingException
    {
        LdapDN oriChildName = opContext.getDn();
        
        Attributes entry = nexus.lookup( new LookupOperationContext( oriChildName ) );

        if ( oriChildName.startsWith( schemaBaseDN ) )
        {
            schemaManager.replace( oriChildName, opContext.getParent(), entry, 
                opContext.hasRequestControl( CascadeControl.CONTROL_OID ) );
        }
        
        next.move( opContext );
    }
    

    public void rename( NextInterceptor next, RenameOperationContext opContext ) throws NamingException
    {
        LdapDN name = opContext.getDn();
        Rdn newRdn = opContext.getNewRdn();
        boolean deleteOldRn = opContext.getDelOldDn();
        
        Attributes entry = nexus.lookup( new LookupOperationContext( name ) );

        if ( name.startsWith( schemaBaseDN ) )
        {
            schemaManager.modifyRn( name, newRdn, deleteOldRn, entry, 
                opContext.hasRequestControl( CascadeControl.CONTROL_OID ) );
        }
        
        next.rename( opContext );
    }


    public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws NamingException
    {
        Attributes entry;
        LdapDN name = opContext.getDn();
        List<ModificationItemImpl> mods = opContext.getModItems();

        // handle operations against the schema subentry in the schema service
        // and never try to look it up in the nexus below
        if ( name.getNormName().equalsIgnoreCase( subschemaSubentryDnNorm ) )
        {
            entry = schemaService.getSubschemaEntry( SCHEMA_SUBENTRY_RETURN_ATTRIBUTES );
        }
        else
        {
            entry = nexus.lookup( new LookupOperationContext( name ) );
        }
        
        // First, we get the entry from the backend. If it does not exist, then we throw an exception
        Attributes targetEntry = SchemaUtils.getTargetEntry( mods, entry );

        if ( entry == null )
        {
            log.error( "No entry with this name :{}", name );
            throw new LdapNameNotFoundException( "The entry which name is " + name + " is not found." );
        }
        
        // We will use this temporary entry to check that the modifications
        // can be applied as atomic operations
        Attributes tmpEntry = ( Attributes ) entry.clone();
        
        Set<String> modset = new HashSet<String>();
        ModificationItem objectClassMod = null;
        
        // Check that we don't have two times the same modification.
        // This is somehow useless, as modification operations are supposed to
        // be atomic, so we may have a sucession of Add, DEL, ADD operations
        // for the same attribute, and this will be legal.
        // @TODO : check if we can remove this test.
        for ( ModificationItem mod:mods )
        {
            if ( mod.getAttribute().getID().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) )
            {
                objectClassMod = mod;
            }
            
            // Freak out under some weird cases
            if ( mod.getAttribute().size() == 0 )
            {
                // not ok for add but ok for replace and delete
                if ( mod.getModificationOp() == DirContext.ADD_ATTRIBUTE )
                {
                    throw new LdapInvalidAttributeValueException( "No value is not a valid value for an attribute.", 
                        ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                }
            }

            StringBuffer keybuf = new StringBuffer();
            keybuf.append( mod.getModificationOp() );
            keybuf.append( mod.getAttribute().getID() );

            for ( int jj = 0; jj < mod.getAttribute().size(); jj++ )
            {
                keybuf.append( mod.getAttribute().get( jj ) );
            }
            
            if ( !modset.add( keybuf.toString() ) && ( mod.getModificationOp() == DirContext.ADD_ATTRIBUTE ) )
            {
                throw new LdapAttributeInUseException( "found two copies of the following modification item: " +
                 mod );
            }
        }
        
        // Get the objectClass attribute.
        Attribute objectClass;

        if ( objectClassMod == null )
        {
            objectClass = entry.get( SchemaConstants.OBJECT_CLASS_AT );

            if ( objectClass == null )
            {
                objectClass = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
            }
        }
        else
        {
            objectClass = getResultantObjectClasses( objectClassMod.getModificationOp(), objectClassMod.getAttribute(),
                entry.get( SchemaConstants.OBJECT_CLASS_AT ) );
        }

        ObjectClassRegistry ocRegistry = this.registries.getObjectClassRegistry();
        AttributeTypeRegistry atRegistry = this.registries.getAttributeTypeRegistry();

        // -------------------------------------------------------------------
        // DIRSERVER-646 Fix: Replacing an unknown attribute with no values 
        // (deletion) causes an error
        // -------------------------------------------------------------------
        
        if ( ( mods.size() == 1 ) && 
             ( mods.get( 0 ).getAttribute().size() == 0 ) && 
             ( mods.get( 0 ).getModificationOp() == DirContext.REPLACE_ATTRIBUTE ) &&
             ! atRegistry.hasAttributeType( mods.get( 0 ).getAttribute().getID() ) )
        {
            return;
        }
        
        // Now, apply the modifications on the cloned entry before applying it on the
        // real object.
        for ( ModificationItem mod:mods )
        {
            int modOp = mod.getModificationOp();
            Attribute change = mod.getAttribute();

            if ( !atRegistry.hasAttributeType( change.getID() ) && 
                !objectClass.contains( SchemaConstants.EXTENSIBLE_OBJECT_OC ) )
            {
                throw new LdapInvalidAttributeIdentifierException();
            }

            // We will forbid modification of operational attributes which are not
            // user modifiable.
            AttributeType attributeType = atRegistry.lookup( change.getID() );
            
            if ( !attributeType.isCanUserModify() )
            {
                throw new NoPermissionException( "Cannot modify the attribute '" + change.getID() + "'" );
            }
            
            switch ( modOp )
            {
                case DirContext.ADD_ATTRIBUTE :
                    Attribute attr = tmpEntry.get( change.getID() );
                    
                    if ( attr != null ) 
                    {
                        NamingEnumeration values = change.getAll();
                        
                        while ( values.hasMoreElements() )
                        {
                            attr.add( values.nextElement() );
                        }
                    }
                    else
                    {
                        attr = new AttributeImpl( change.getID() );
                        NamingEnumeration values = change.getAll();
                        
                        while ( values.hasMoreElements() )
                        {
                            attr.add( values.nextElement() );
                        }
                        
                        tmpEntry.put( attr );
                    }
                    
                    break;

                case DirContext.REMOVE_ATTRIBUTE :
                    if ( tmpEntry.get( change.getID() ) == null )
                    {
                        log.error( "Trying to remove an non-existant attribute: " + change.getID() );
                        throw new LdapNoSuchAttributeException();
                    }

                    // We may have to remove the attribute or only some values
                    if ( change.size() == 0 )
                    {
                        // No value : we have to remove the entire attribute
                        // Check that we aren't removing a MUST attribute
                        if ( isRequired( change.getID(), objectClass ) )
                        {
                            log.error( "Trying to remove a required attribute: " + change.getID() );
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION );
                        }
                    }
                    else
                    {
                        // for required attributes we need to check if all values are removed
                        // if so then we have a schema violation that must be thrown
                        if ( isRequired( change.getID(), objectClass ) && isCompleteRemoval( change, entry ) )
                        {
                            log.error( "Trying to remove a required attribute: " + change.getID() );
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION );
                        }

                        // Now remove the attribute and all its values
                        Attribute modified = tmpEntry.remove( change.getID() );
                        
                        // And inject back the values except the ones to remove
                        NamingEnumeration values = change.getAll();
                        
                        while ( values.hasMoreElements() )
                        {
                            modified.remove( values.next() );
                        }
                        
                        // ok, done. Last check : if the attribute does not content any more value;
                        // and if it's a MUST one, we should thow an exception
                        if ( ( modified.size() == 0 ) && isRequired( change.getID(), objectClass ) )
                        {
                            log.error( "Trying to remove a required attribute: " + change.getID() );
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION );
                        }

                        // Put back the attribute in the entry only if it has values left in it
                        if ( modified.size() > 0 )
                        {
                            tmpEntry.put( modified );
                        }
                    }
                    
                    SchemaChecker.preventRdnChangeOnModifyRemove( name, modOp, change, 
                        this.registries.getOidRegistry() ); 
                    SchemaChecker
                        .preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, modOp, change, objectClass );
                    break;
                        
                case DirContext.REPLACE_ATTRIBUTE :
                    SchemaChecker.preventRdnChangeOnModifyReplace( name, modOp, change, 
                        registries.getOidRegistry() );
                    SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, modOp, change );
                    
                    attr = tmpEntry.get( change.getID() );
                    
                    if ( attr != null )
                    {
                        tmpEntry.remove( change.getID() );
                    }
                    
                    attr = new AttributeImpl( change.getID() );
                    
                    NamingEnumeration values = change.getAll();
                    
                    if ( values.hasMoreElements() ) 
                    {
                        while ( values.hasMoreElements() )
                        {
                            attr.add( values.nextElement() );
                        }

                        tmpEntry.put( attr );
                    }
                    
                    break;
            }
        }
        
        check( name, tmpEntry );

        // let's figure out if we need to add or take away from mods to maintain 
        // the objectClass attribute with it's hierarchy of ancestors 
        if ( objectClassMod != null )
        {
            Attribute alteredObjectClass = ( Attribute ) objectClass.clone();
            alterObjectClasses( alteredObjectClass );

            if ( !alteredObjectClass.equals( objectClass ) )
            {
                Attribute ocMods = objectClassMod.getAttribute();
                
                switch ( objectClassMod.getModificationOp() )
                {
                    case ( DirContext.ADD_ATTRIBUTE  ):
                        if ( ocMods.contains( SchemaConstants.TOP_OC ) )
                        {
                            ocMods.remove( SchemaConstants.TOP_OC );
                        }
                    
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !objectClass.contains( alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                        
                        break;
                        
                    case ( DirContext.REMOVE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !objectClass.contains( alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.remove( alteredObjectClass.get( ii ) );
                            }
                        }
                    
                        break;
                        
                    case ( DirContext.REPLACE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !objectClass.contains( alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                    
                        break;
                        
                    default:
                }
            }
        }
        
        if ( name.startsWith( schemaBaseDN ) )
        {
            log.debug( "Modification attempt on schema partition {}: \n{}", name, opContext );
        
            schemaManager.modify( name, mods, entry, targetEntry,
                opContext.hasRequestControl( CascadeControl.CONTROL_OID ));
        }
        else if ( subschemaSubentryDnNorm.equals( name.getNormName() ) )
        {
            log.debug( "Modification attempt on schema subentry {}: \n{}", name, opContext );

            schemaManager.modifySchemaSubentry( name, mods, entry, targetEntry,
                opContext.hasRequestControl( CascadeControl.CONTROL_OID ) );
            return;
        }
        
        next.modify( opContext );
    }


    private void filterObjectClass( Attributes entry ) throws NamingException
    {
        List<ObjectClass> objectClasses = new ArrayList<ObjectClass>();
        Attribute oc = entry.get( SchemaConstants.OBJECT_CLASS_AT );
        
        if ( oc != null )
        {
            getObjectClasses( oc, objectClasses );

            entry.remove( SchemaConstants.OBJECT_CLASS_AT );

            Attribute newOc = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );

            for ( Object currentOC:objectClasses )
            {
                if ( currentOC instanceof String )
                {
                    newOc.add( currentOC );
                }
                else
                {
                    newOc.add( ( (ObjectClass)currentOC ).getName() );
                }
            }

            newOc.add( SchemaConstants.TOP_OC );
            entry.put( newOc );
        }
    }


    private void filterBinaryAttributes( Attributes entry ) throws NamingException
    {
        /*
         * start converting values of attributes to byte[]s which are not
         * human readable and those that are in the binaries set
         */
        NamingEnumeration list = entry.getIDs();

        while ( list.hasMore() )
        {
            String id = ( String ) list.next();
            AttributeType type = null;

            if ( registries.getAttributeTypeRegistry().hasAttributeType( id ) )
            {
                type = registries.getAttributeTypeRegistry().lookup( id );
            }
            else
            {
                continue;
            }

            if ( !type.getSyntax().isHumanReadable() )
            {
                Attribute attribute = entry.get( id );
                Attribute binary = new AttributeImpl( id );

                for ( int i = 0; i < attribute.size(); i++ )
                {
                    Object value = attribute.get( i );
                
                    if ( value instanceof String )
                    {
                        binary.add( i, StringTools.getBytesUtf8( ( String ) value ) );
                    }
                    else
                    {
                        binary.add( i, value );
                    }
                }

                entry.remove( id );
                entry.put( binary );
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
    private class BinaryAttributeFilter implements SearchResultFilter
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
            throws NamingException
        {
            filterBinaryAttributes( result.getAttributes() );
            return true;
        }
    }

    /**
     * Filters objectClass attribute to inject top when not present.
     */
    private class TopFilter implements SearchResultFilter
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
            throws NamingException
        {
            filterObjectClass( result.getAttributes() );
            return true;
        }
    }


    /**
     * Check that all the attributes exist in the schema for this entry.
     * 
     * We also check the syntaxes
     */
    private void check( LdapDN dn, Attributes entry ) throws NamingException
    {
        NamingEnumeration attrEnum = entry.getIDs();

        // ---------------------------------------------------------------
        // First, make sure all attributes are valid schema defined attributes
        // ---------------------------------------------------------------

        while ( attrEnum.hasMoreElements() )
        {
            String name = ( String ) attrEnum.nextElement();
            
            if ( !registries.getAttributeTypeRegistry().hasAttributeType( name ) )
            {
                throw new LdapInvalidAttributeIdentifierException( name + " not found in attribute registry!" );
            }
        }

        // We will check some elements :
        // 1) the entry must have all the MUST attributes of all its ObjectClass
        // 2) The SingleValued attributes must be SingleValued
        // 3) No attributes should be used if they are not part of MUST and MAY
        // 3-1) Except if the extensibleObject ObjectClass is used
        // 3-2) or if the AttributeType is COLLECTIVE
        // 4) We also check that for H-R attributes, we have a valid String in the values
        Attribute objectClassAttr = entry.get( SchemaConstants.OBJECT_CLASS_AT );
        
        // Protect the server against a null objectClassAttr
        // It can be the case if the user forgot to add it to the entry ...
        // In this case, we create an new one, empty
        if ( objectClassAttr == null )
        {
            objectClassAttr = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
        }
        
        List<ObjectClass> ocs = new ArrayList<ObjectClass>();

        alterObjectClasses( objectClassAttr );
        
        // Now we can process the MUST and MAY attributes
        Set<String> must = getAllMust( (NamingEnumeration<String>)objectClassAttr.getAll() );
        Set<String> allowed = getAllAllowed( (NamingEnumeration<String>)objectClassAttr.getAll(), must );

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
    }

    /**
     * Check that all the attributes exist in the schema for this entry.
     */
    public void add( NextInterceptor next, AddOperationContext opContext ) throws NamingException
    {
    	LdapDN name = opContext.getDn();
        Attributes attrs = opContext.getEntry();
        
    	check( name, attrs );

        if ( name.startsWith( schemaBaseDN ) )
        {
            schemaManager.add( name, attrs );
        }

        next.add( opContext );
    }
    

    /**
     * Checks to see if an attribute is required by as determined from an entry's
     * set of objectClass attribute values.
     *
     * @return true if the objectClass values require the attribute, false otherwise
     * @throws NamingException if the attribute is not recognized
     */
    private void assertAllAttributesAllowed( LdapDN dn, Attributes attributes, Set<String> allowed ) throws NamingException
    {
        // Never check the attributes if the extensibleObject objectClass is
        // declared for this entry
        Attribute objectClass = attributes.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( AttributeUtils.containsValueCaseIgnore( objectClass, SchemaConstants.EXTENSIBLE_OBJECT_OC ) )
        {
            return;
        }

        NamingEnumeration attrs = attributes.getAll();

        while ( attrs.hasMoreElements() )
        {
            Attribute attribute = (Attribute)attrs.nextElement();
            String attrId = attribute.getID();
            String attrOid = registries.getOidRegistry().getOid( attrId );

            AttributeType attributeType = registries.getAttributeTypeRegistry().lookup( attrOid );

            if ( !attributeType.isCollective() && ( attributeType.getUsage() == UsageEnum.USER_APPLICATIONS ) )
            {
                if ( !allowed.contains( attrOid ) )
                {
                    throw new LdapSchemaViolationException( "Attribute " +
                        attribute.getID() + " not declared in objectClasses of entry " + dn.getUpName(),
                        ResultCodeEnum.OBJECT_CLASS_VIOLATION );
                }
            }
        }
    }
    
    
    public void delete( NextInterceptor next, DeleteOperationContext opContext ) throws NamingException
    {
    	LdapDN name = opContext.getDn();
        Attributes entry = nexus.lookup( new LookupOperationContext( name ) );
        
        if ( name.startsWith( schemaBaseDN ) )
        {
            schemaManager.delete( name, entry, opContext.hasRequestControl( CascadeControl.CONTROL_OID ) );
        }
        
        next.delete( opContext );
    }


    /**
     * Checks to see number of values of an attribute conforms to the schema
     */
    private void assertNumberOfAttributeValuesValid( Attributes attributes ) throws InvalidAttributeValueException, NamingException
    {
        NamingEnumeration list = attributes.getAll();
        
        while ( list.hasMore() )
        {
            Attribute attribute = ( Attribute ) list.next();
            assertNumberOfAttributeValuesValid( attribute );
        }
    }
    
    /**
     * Checks to see numbers of values of attributes conforms to the schema
     */
    private void assertNumberOfAttributeValuesValid( Attribute attribute ) throws InvalidAttributeValueException, NamingException
    {
        AttributeTypeRegistry registry = this.registries.getAttributeTypeRegistry();

        if ( attribute.size() > 1 && registry.lookup( attribute.getID() ).isSingleValue() )
        {                
            throw new LdapInvalidAttributeValueException( "More than one value has been provided " +
                "for the single-valued attribute: " + attribute.getID(), ResultCodeEnum.CONSTRAINT_VIOLATION );
        }
    }

    /**
     * Checks to see the presence of all required attributes within an entry.
     */
    private void assertRequiredAttributesPresent( LdapDN dn, Attributes entry, Set<String> must )
        throws NamingException
    {
        NamingEnumeration attributes = entry.getAll();

        while ( attributes.hasMoreElements() && ( must.size() > 0 ) )
        {
            Attribute attribute = (Attribute)attributes.nextElement();
            
            String oid = registries.getOidRegistry().getOid( attribute.getID() );

            must.remove( oid );
        }

        if ( must.size() != 0 )
        {
            throw new LdapSchemaViolationException( "Required attributes " +
                must + " not found within entry " + dn.getUpName(),
                ResultCodeEnum.OBJECT_CLASS_VIOLATION );
        }
    }
    
    /**
     * Checck that OC does not conflict :
     * - we can't have more than one STRUCTURAL OC unless they are in the same
     * inheritance tree
     * - we must have at least one STRUCTURAL OC
     */
    private void assertObjectClasses( LdapDN dn, List<ObjectClass> ocs )  throws NamingException
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

    	for ( ObjectClass oc:ocs )
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
    		String message = "Entry " + dn + " does not contain a STRUCTURAL ObjectClass";
    		log.error( message );
    		throw new LdapSchemaViolationException( message, ResultCodeEnum.OBJECT_CLASS_VIOLATION );
    	}
    	
        // --------------------------------------------------------------------
        // Put all structural object classes into new remaining container and
    	// start removing any which are superiors of others in the set.  What
    	// is left in the remaining set will be unrelated structural 
    	/// objectClasses.  If there is more than one then we have a problem.
        // --------------------------------------------------------------------
    	
    	Set<ObjectClass> remaining = new HashSet<ObjectClass>( structuralObjectClasses.size() );
    	remaining.addAll( structuralObjectClasses );
    	for ( ObjectClass oc: structuralObjectClasses )
    	{
    	    if ( oc.getSuperClasses() != null )
    	    {
    	        for ( ObjectClass superClass: oc.getSuperClasses() )
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
            String message = "Entry " + dn + " contains more than one STRUCTURAL ObjectClass: " + remaining;
            log.error( message );
            throw new LdapSchemaViolationException( message, ResultCodeEnum.OBJECT_CLASS_VIOLATION );
    	}
    }

    /**
     * Check the entry attributes syntax, using the syntaxCheckers
     */
    private void assertSyntaxes( Attributes entry ) throws NamingException
    {
        NamingEnumeration attributes = entry.getAll();

        // First, loop on all attributes
        while ( attributes.hasMoreElements() )
        {
            Attribute attribute = ( Attribute ) attributes.nextElement();

            AttributeType attributeType = registries.getAttributeTypeRegistry().lookup( attribute.getID() );
            SyntaxChecker syntaxChecker =  registries.getSyntaxCheckerRegistry().lookup( attributeType.getSyntax().getOid() );
            
            if ( syntaxChecker instanceof AcceptAllSyntaxChecker )
            {
                // This is a speedup : no need to check the syntax of any value
                // if all the sytanxes are accepted...
                continue;
            }
            
            NamingEnumeration<?> values = attribute.getAll();

            // Then loop on all values
            while ( values.hasMoreElements() )
            {
                Object value = values.nextElement();
                
                try
                {
                    syntaxChecker.assertSyntax( value );
                }
                catch ( NamingException ne )
                {
                    String message = "Attribute value '" + 
                        (value instanceof String ? value : StringTools.dumpBytes( (byte[])value ) ) + 
                        "' for attribute '" + attribute.getID() + "' is syntactically incorrect";
                    log.info( message );
                    
                    throw new LdapInvalidAttributeValueException( message, ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );

                }
            }
        }
    }
    
    /**
     * Check that all the attribute's values which are Human Readable can be transformed
     * to valid String if they are stored as byte[].
     */
    private void assertHumanReadable( Attributes entry ) throws NamingException
    {
        NamingEnumeration attributes = entry.getAll();
        boolean isEntryModified = false;
        Attributes cloneEntry = null;

        // First, loop on all attributes
        while ( attributes.hasMoreElements() )
        {
            Attribute attribute = ( Attribute ) attributes.nextElement();

            AttributeType attributeType = registries.getAttributeTypeRegistry().lookup( attribute.getID() );

            // If the attributeType is H-R, check all of its values
            if ( attributeType.getSyntax().isHumanReadable() )
            {
                Enumeration values = attribute.getAll();
                Attribute clone = null;
                boolean isModified = false;

                // Loop on each values
                while ( values.hasMoreElements() )
                {
                    Object value = values.nextElement();

                    if ( value instanceof String )
                    {
                        continue;
                    }
                    else if ( value instanceof byte[] )
                    {
                        // Ve have a byte[] value. It should be a String UTF-8 encoded
                        // Let's transform it
                        try
                        {
                            String valStr = new String( (byte[])value, "UTF-8" );

                            if ( !isModified )
                            {
                                // Don't create useless clones. We only clone
                                // if we have at least one value which is a byte[]
                                isModified = true;
                                clone = (Attribute)attribute.clone();
                            }

                            // Swap the value into the clone
                            clone.remove( value );
                            clone.add( valStr );
                        }
                        catch ( UnsupportedEncodingException uee )
                        {
                            throw new NamingException( "The value is not a valid String" );
                        }
                    }
                    else
                    {
                        throw new NamingException( "The value stored in an Human Readable attribute is not a String" );
                    }
                }

                // The attribute has been checked. If one of its value has been modified,
                // we have to modify the cloned Attributes/
                if ( isModified )
                {
                    if ( !isEntryModified )
                    {
                        // Again, let's avoid useless cloning. If no attribute is H-R
                        // or if no H-R value is stored as a byte[], we don't have to create a clone
                        // of the entry
                        cloneEntry = (Attributes)entry.clone();
                        isEntryModified = true;
                    }

                    // Swap the attribute into the cloned entry
                    cloneEntry.remove( attribute.getID() );
                    cloneEntry.put( clone );
                }
            }
        }

        // At the end, we now have to switch the entries, if it has been modified
        if ( isEntryModified )
        {
            attributes = cloneEntry.getAll();

            // We llop on all the attributes and modify them in the initial entry.
            while ( attributes.hasMoreElements() )
            {
                Attribute attribute = (Attribute)attributes.nextElement();
                entry.remove( attribute.getID() );
                entry.put( attribute );
            }
        }
    }
}
