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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.DirectoryServiceConfiguration;
import org.apache.directory.server.core.configuration.InterceptorConfiguration;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.shared.ldap.exception.LdapAttributeInUseException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeIdentifierException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.message.LockableAttributeImpl;
import org.apache.directory.shared.ldap.message.LockableAttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
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
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.EmptyEnumeration;
import org.apache.directory.shared.ldap.util.SingletonEnumeration;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An {@link org.apache.directory.server.core.interceptor.Interceptor} that manages and enforces schemas.
 *
 * @todo Better interceptor description required.
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SchemaService extends BaseInterceptor
{
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String BINARY_KEY = "java.naming.ldap.attributes.binary";

    /** The LoggerFactory used by this Interceptor */
    private static Logger log = LoggerFactory.getLogger( SchemaService.class );

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

    private List filters = new ArrayList();

    /**
     * the global schema object registries
     */
    private GlobalRegistries globalRegistries;

    private Set binaries;

    /**
     * subschemaSubentry attribute's value from Root DSE
     */
    private LdapDN subschemaSubentryDn;

    /**
     * The time when the server started up.
     */
    private String startUpTimeStamp;

    /** A map used to store all the objectClasses superiors */
    private Map superiors;

    /** A map used to store all the objectClasses may attributes */
    private Map allMay;

    /** A map used to store all the objectClasses must */
    private Map allMust;

    /** A map used to store all the objectClasses allowed attributes (may + must) */
    private Map allowed;

    /**
     * Creates a schema service interceptor.
     */
    public SchemaService()
    {
        startUpTimeStamp = DateUtils.getGeneralizedTime();
    }

    /**
     * Initialize the Schema Service
     * 
     * @param factoryCfg
     * @param cfg
     * 
     * @throws NamingException
     */
    public void init( DirectoryServiceConfiguration factoryCfg, InterceptorConfiguration cfg ) throws NamingException
    {
        if ( IS_DEBUG )
        {
            log.debug( "Initializing SchemaService..." );
        }
        
        nexus = factoryCfg.getPartitionNexus();
        globalRegistries = factoryCfg.getGlobalRegistries();
        binaryAttributeFilter = new BinaryAttributeFilter();
        topFilter = new TopFilter();
        filters.add( binaryAttributeFilter );
        filters.add( topFilter );
        binaries = ( Set ) factoryCfg.getEnvironment().get( BINARY_KEY );

        // stuff for dealing with subentries (garbage for now)
        String subschemaSubentry = ( String ) nexus.getRootDSE().get( "subschemaSubentry" ).get();
        subschemaSubentryDn = new LdapDN( subschemaSubentry );
        subschemaSubentryDn.normalize( globalRegistries.getAttributeTypeRegistry().getNormalizerMapping() );
        
        computeSuperiors();

        if ( IS_DEBUG )
        {
            log.debug( "SchemaService Initialized !" );
        }
    }

    /**
     * Compute the MUST attributes for an objectClass. This method gather all the
     * MUST from all the objectClass and its superors.
     */
    private void computeMustAttributes( ObjectClass objectClass, Set atSeen ) throws NamingException
    {
        List parents = (List)superiors.get( objectClass.getOid() );
        
        List mustList = new ArrayList();
        List allowedList = new ArrayList();
        Set mustSeen = new HashSet();
        
        allMust.put( objectClass.getOid(), mustList );
        allowed.put( objectClass.getOid(), allowedList );

        Iterator objectClasses = parents.iterator();
        
        while ( objectClasses.hasNext() )
        {
            ObjectClass parent = (ObjectClass)objectClasses.next();
            
            AttributeType[] mustParent = parent.getMustList();
            
            if ( ( mustParent != null ) && ( mustParent.length != 0 ) )
            {
                for ( int i = 0; i < mustParent.length; i++ )
                {
                    AttributeType attributeType = mustParent[i];
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
     */
    private void computeMayAttributes( ObjectClass objectClass, Set atSeen ) throws NamingException
    {
        List parents = (List)superiors.get( objectClass.getOid() );
        
        List mayList = new ArrayList();
        Set maySeen = new HashSet();
        List allowedList = (List)allowed.get( objectClass.getOid() );

        
        allMay.put( objectClass.getOid(), mayList );

        Iterator objectClasses = parents.iterator();
        
        while ( objectClasses.hasNext() )
        {
            ObjectClass parent = (ObjectClass)objectClasses.next();
            
            AttributeType[] mustParent = parent.getMustList();
            
            if ( ( mustParent != null ) && ( mustParent.length != 0 ) )
            {
                for ( int i = 0; i < mustParent.length; i++ )
                {
                    AttributeType attributeType = mustParent[i];
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
    private void computeOCSuperiors( ObjectClass objectClass, List superiors, Set ocSeen ) throws NamingException
    {
        ObjectClass[] parents = objectClass.getSuperClasses();
        
        // Loop on all the objectClass superiors
        if ( ( parents != null ) && ( parents.length != 0 ) )
        {
            for ( int i = 0; i < parents.length; i++ )
            {
                ObjectClass parent = parents[i];
                
                // Top is not added
                if ( "top".equals( parent.getName() ) )
                {
                    continue;
                }
                
                // For each one, recurse
                computeOCSuperiors( parent, superiors, ocSeen );
                
                String oid = parents[i].getOid();
                
                if ( !ocSeen.contains( oid ) )
                {
                    superiors.add( parent );
                    ocSeen.add( oid );
                }
            }
        }
        
        return;
    }
    
    /**
     * Compute all ObjectClasses superiors, MAY and MUST attributes.
     * @throws NamingException
     */
    private void computeSuperiors() throws NamingException
    {
        Iterator objectClasses = globalRegistries.getObjectClassRegistry().list();
        superiors = new HashMap();
        allMust = new HashMap();
        allMay = new HashMap();
        allowed = new HashMap();
        
        while ( objectClasses.hasNext() )
        {
            List ocSuperiors = new ArrayList();
            
            ObjectClass objectClass = (ObjectClass)objectClasses.next();
            superiors.put( objectClass.getOid(), ocSuperiors );
            
            computeOCSuperiors( objectClass, ocSuperiors, new HashSet() );
            
            Set atSeen = new HashSet();
            computeMustAttributes( objectClass, atSeen );
            computeMayAttributes( objectClass, atSeen );
            
            superiors.put( objectClass.getName(), ocSuperiors );
        }
    }

    /**
     * 
     */
    public NamingEnumeration list( NextInterceptor nextInterceptor, LdapDN base ) throws NamingException
    {
        NamingEnumeration e = nextInterceptor.list( base );
        Invocation invocation = InvocationStack.getInstance().peek();
        return new SearchResultFilteringEnumeration( e, new SearchControls(), invocation, binaryAttributeFilter );
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
    private void filterAttributesToReturn( SearchControls searchCtls ) throws NamingException
    {
        String[] attributes = searchCtls.getReturningAttributes();

        if ( ( attributes == null ) || ( attributes.length == 0 ) )
        {
            return;
        }
        
        Map filteredAttrs = new HashMap(); 
        
        for ( int i = 0; i < attributes.length; i++ )
        {
            String attribute = attributes[i];
            
            // Skip special attributes
            if ( ( "*".equals( attribute ) ) || ( "+".equals( attribute ) ) || ( "1.1".equals( attribute ) ) )
            {
                if ( !filteredAttrs.containsKey( attribute ) )
                {
                    filteredAttrs.put( attribute, attribute );
                }

                continue;
            }
            
            if ( globalRegistries.getAttributeTypeRegistry().hasAttributeType( attribute ) )
            {
                String oid = globalRegistries.getOidRegistry().getOid( attribute );
                
                if ( !filteredAttrs.containsKey( oid ) )
                {
                    filteredAttrs.put( oid, attribute );
                }
            }
            else
            {
                throw new LdapInvalidAttributeIdentifierException( 
                    "The attribute " + attribute + " was not recognized as a valid attributeType." );
            }
        }
        
        // If we still have the same attribute number, then we can just get out the method
        if ( filteredAttrs.size() == attributes.length )
        {
            return;
        }
        
        // Some attributes have been removed. let's modify the searchControl
        String[] newAttributesList = new String[filteredAttrs.size()];
        
        int pos = 0;
        Iterator keys = filteredAttrs.keySet().iterator();
        
        while ( keys.hasNext() )
        {
            newAttributesList[pos++] = (String)filteredAttrs.get( keys.next() );
        }
        
        searchCtls.setReturningAttributes( newAttributesList );
    }

    
    /**
     * 
     */
    public NamingEnumeration search( NextInterceptor nextInterceptor, LdapDN base, Map env, ExprNode filter,
        SearchControls searchCtls ) throws NamingException
    {
        // We have to eliminate bad attributes from the request, accordingly
        // to RFC 2251, chap. 4.5.1. Basically, all unknown attributes are removed
        // from the list
        filterAttributesToReturn( searchCtls );

        // Deal with the normal case : searching for a normal value (not subSchemaSubEntry
        if ( !subschemaSubentryDn.toNormName().equals( base.toNormName() ) )
        {
            NamingEnumeration e = nextInterceptor.search( base, env, filter, searchCtls );
            
            Invocation invocation = InvocationStack.getInstance().peek();

            if ( searchCtls.getReturningAttributes() != null )
            {
                return new SearchResultFilteringEnumeration( e, new SearchControls(), invocation, topFilter );
            }

            return new SearchResultFilteringEnumeration( e, searchCtls, invocation, filters );
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
                String objectClass = null;
                
                if ( node.getValue() instanceof String )
                {
                    objectClass = ( String ) node.getValue();
                }
                else
                {
                    objectClass = node.getValue().toString();
                }
    
                String objectClassOid = null;
                
                if ( globalRegistries.getObjectClassRegistry().hasObjectClass( objectClass ) )
                {
                    objectClassOid = globalRegistries.getObjectClassRegistry().lookup( objectClass ).getName();
                }
                
                // see if node attribute is objectClass
                if ( node.getAttribute().equalsIgnoreCase( "2.5.4.0" )
                    && ( "top".equalsIgnoreCase( objectClassOid ) || "subschema".equalsIgnoreCase( objectClassOid ) )
                    && ( node.getAssertionType() == SimpleNode.EQUALITY ) )
                {
                    // call.setBypass( true );
                    Attributes attrs = getSubschemaEntry( searchCtls.getReturningAttributes() );
                    SearchResult result = new SearchResult( base.toString(), null, attrs );
                    return new SingletonEnumeration( result );
                }
                else
                {
                    return new EmptyEnumeration();
                }
            }
            else if ( filter instanceof PresenceNode )
            {
                PresenceNode node = ( PresenceNode ) filter;

                // see if node attribute is objectClass
                if ( node.getAttribute().equalsIgnoreCase( "2.5.4.0" ) )
                {
                    // call.setBypass( true );
                    Attributes attrs = getSubschemaEntry( searchCtls.getReturningAttributes() );
                    SearchResult result = new SearchResult( base.toString(), null, attrs );
                    return new SingletonEnumeration( result );
                }
            }
        }

        // In any case not handled previously, just return an empty result
        return new EmptyEnumeration();
    }


    /**
     * 
     * @param ids
     * @return
     * @throws NamingException
     */
    private Attributes getSubschemaEntry( String[] ids ) throws NamingException
    {
        if ( ids == null )
        {
            ids = EMPTY_STRING_ARRAY;
        }

        Set set = new HashSet();
        LockableAttributesImpl attrs = new LockableAttributesImpl();
        LockableAttributeImpl attr;

        for ( int ii = 0; ii < ids.length; ii++ )
        {
            set.add( ids[ii].toLowerCase() );
        }

        // Check whether the set contains a plus, and use it below to include all
        // operational attributes.  Due to RFC 3673, and issue DIREVE-228 in JIRA
        boolean returnAllOperationalAttributes = set.contains( "+" );

        if ( returnAllOperationalAttributes || set.contains( "objectclasses" ) )
        {
            attr = new LockableAttributeImpl( "objectClasses" );
            Iterator list = globalRegistries.getObjectClassRegistry().list();
            while ( list.hasNext() )
            {
                ObjectClass oc = ( ObjectClass ) list.next();
                attr.add( SchemaUtils.render( oc ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "attributetypes" ) )
        {
            attr = new LockableAttributeImpl( "attributeTypes" );
            Iterator list = globalRegistries.getAttributeTypeRegistry().list();
            while ( list.hasNext() )
            {
                AttributeType at = ( AttributeType ) list.next();
                attr.add( SchemaUtils.render( at ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "matchingrules" ) )
        {
            attr = new LockableAttributeImpl( "matchingRules" );
            Iterator list = globalRegistries.getMatchingRuleRegistry().list();
            while ( list.hasNext() )
            {
                MatchingRule mr = ( MatchingRule ) list.next();
                attr.add( SchemaUtils.render( mr ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "matchingruleuse" ) )
        {
            attr = new LockableAttributeImpl( "matchingRuleUse" );
            Iterator list = globalRegistries.getMatchingRuleUseRegistry().list();
            while ( list.hasNext() )
            {
                MatchingRuleUse mru = ( MatchingRuleUse ) list.next();
                attr.add( SchemaUtils.render( mru ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "ldapsyntaxes" ) )
        {
            attr = new LockableAttributeImpl( "ldapSyntaxes" );
            Iterator list = globalRegistries.getSyntaxRegistry().list();
            while ( list.hasNext() )
            {
                Syntax syntax = ( Syntax ) list.next();
                attr.add( SchemaUtils.render( syntax ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "ditcontentrules" ) )
        {
            attr = new LockableAttributeImpl( "dITContentRules" );
            Iterator list = globalRegistries.getDitContentRuleRegistry().list();
            while ( list.hasNext() )
            {
                DITContentRule dcr = ( DITContentRule ) list.next();
                attr.add( SchemaUtils.render( dcr ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "ditstructurerules" ) )
        {
            attr = new LockableAttributeImpl( "dITStructureRules" );
            Iterator list = globalRegistries.getDitStructureRuleRegistry().list();
            while ( list.hasNext() )
            {
                DITStructureRule dsr = ( DITStructureRule ) list.next();
                attr.add( SchemaUtils.render( dsr ).toString() );
            }
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "nameforms" ) )
        {
            attr = new LockableAttributeImpl( "nameForms" );
            Iterator list = globalRegistries.getNameFormRegistry().list();
            while ( list.hasNext() )
            {
                NameForm nf = ( NameForm ) list.next();
                attr.add( SchemaUtils.render( nf ).toString() );
            }
            attrs.put( attr );
        }

        // timeestamps are hacks for now until the schema is actually updateable these
        // use the servers startup time stamp for both modify and create timestamps

        if ( returnAllOperationalAttributes || set.contains( "createtimestamp" ) )
        {
            attr = new LockableAttributeImpl( "createTimestamp" );
            attr.add( startUpTimeStamp );
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "modifytimestamp" ) )
        {
            attr = new LockableAttributeImpl( "modifyTimestamp" );
            attr.add( startUpTimeStamp );
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "creatorsname" ) )
        {
            attr = new LockableAttributeImpl( "creatorsName" );
            attr.add( PartitionNexus.ADMIN_PRINCIPAL );
            attrs.put( attr );
        }

        if ( returnAllOperationalAttributes || set.contains( "modifiersname" ) )
        {
            attr = new LockableAttributeImpl( "modifiersName" );
            attr.add( PartitionNexus.ADMIN_PRINCIPAL );
            attrs.put( attr );
        }

        int minSetSize = 0;
        if ( set.contains( "+" ) )
        {
            minSetSize++;
        }
        if ( set.contains( "*" ) )
        {
            minSetSize++;
        }
        if ( set.contains( "ref" ) )
        {
            minSetSize++;
        }

        // add the objectClass attribute
        if ( set.contains( "*" ) || set.contains( "objectclass" ) || set.size() == minSetSize )
        {
            attr = new LockableAttributeImpl( "objectClass" );
            attr.add( "top" );
            attr.add( "subschema" );
            attrs.put( attr );
        }

        // add the cn attribute as required for the RDN
        if ( set.contains( "*" ) || set.contains( "cn" ) || set.contains( "commonname" ) || set.size() == minSetSize )
        {
            attrs.put( "cn", "schema" );
        }

        return attrs;
    }


    /**
     * Search for an entry, using its DN. Binary attributes and ObjectClass attribute are removed.
     */
    public Attributes lookup( NextInterceptor nextInterceptor, LdapDN name ) throws NamingException
    {
        Attributes result = nextInterceptor.lookup( name );
        filterBinaryAttributes( result );
        filterObjectClass( result );
        return result;
    }

    /**
     * 
     */
    public Attributes lookup( NextInterceptor nextInterceptor, LdapDN name, String[] attrIds ) throws NamingException
    {
        Attributes result = nextInterceptor.lookup( name, attrIds );
        if ( result == null )
        {
            return null;
        }

        filterBinaryAttributes( result );
        // filterTop( result );
        filterObjectClass( result );
        
        return result;
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
        OidRegistry oidRegistry = globalRegistries.getOidRegistry();
        ObjectClassRegistry registry = globalRegistries.getObjectClassRegistry();

        if ( !oidRegistry.hasOid( attrId ) )
        {
            return false;
        }

        String attrOid = oidRegistry.getOid( attrId );
        for ( int ii = 0; ii < objectClass.size(); ii++ )
        {
            ObjectClass ocSpec = registry.lookup( ( String ) objectClass.get( ii ) );
            AttributeType[] mustList = ocSpec.getMustList();
            for ( int jj = 0; jj < mustList.length; jj++ )
            {
                if ( mustList[jj].getOid().equals( attrOid ) )
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
        if ( changes == null && existing == null )
        {
            return new LockableAttributeImpl( "objectClass" );
        }

        if ( changes == null )
        {
            return existing;
        }

        if ( existing == null && modOp == DirContext.ADD_ATTRIBUTE )
        {
            return changes;
        }
        else if ( existing == null )
        {
            return new LockableAttributeImpl( "objectClasses" );
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
    
    private void getSuperiors( ObjectClass oc, Set ocSeen, List result ) throws NamingException
    {
        ObjectClass[] superiors = oc.getSuperClasses();
        
        for ( int i = 0; i < superiors.length; i++ )
        {
            ObjectClass parent = superiors[i];
            
            // Skip 'top'
            if ( "top".equals( parent.getName() ) )
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
    
    private boolean getObjectClasses( Attribute objectClasses, List result ) throws NamingException
    {
        Set ocSeen = new HashSet();
        ObjectClassRegistry registry = globalRegistries.getObjectClassRegistry();
        
        // We must select all the ObjectClasses, except 'top',
        // but including all the inherited ObjectClasses
        NamingEnumeration ocs = objectClasses.getAll();
        boolean hasExtensibleObject = false;

        
        while ( ocs.hasMoreElements() )
        {
            String objectClassName = (String)ocs.nextElement();

            if ( "top".equals( objectClassName ) )
            {
                continue;
            }
            
            if ( "extensibleObject".equalsIgnoreCase( objectClassName ) )
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

    private Set getAllMust( NamingEnumeration objectClasses ) throws NamingException
    {
        Set must = new HashSet();
        
        // Loop on all objectclasses
        while ( objectClasses.hasMoreElements() )
        {
            String ocName = (String)objectClasses.nextElement();
            ObjectClass oc = globalRegistries.getObjectClassRegistry().lookup( ocName );
            
            AttributeType[] types = oc.getMustList();
            
            // For each objectClass, loop on all MUST attributeTypes, if any
            if ( ( types != null ) && ( types.length > 0 ) )
            {
                for ( int j = 0; j < types.length; j++ )
                {
                    String oid = types[j].getOid();
                    
                    must.add( oid );
                }
            }
        }
        
        return must;
    }

    private Set getAllAllowed( NamingEnumeration objectClasses, Set must ) throws NamingException
    {
        Set allowed = new HashSet( must );
        
        // Add the 'ObjectClass' attribute ID
        allowed.add( globalRegistries.getOidRegistry().getOid( "ObjectClass" ) );
        
        // Loop on all objectclasses
        while ( objectClasses.hasMoreElements() )
        {
            String ocName = (String)objectClasses.nextElement();
            ObjectClass oc = globalRegistries.getObjectClassRegistry().lookup( ocName );
            
            AttributeType[] types = oc.getMayList();
            
            // For each objectClass, loop on all MUST attributeTypes, if any
            if ( ( types != null ) && ( types.length > 0 ) )
            {
                for ( int j = 0; j < types.length; j++ )
                {
                    String oid = types[j].getOid();
                    
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
    private void alterObjectClasses( Attribute objectClassAttr )
        throws NamingException
    {
        Set objectClasses = new HashSet();
        
        // Init the objectClass list with 'top'
        objectClasses.add( "top" );

        // Construct the new list of ObjectClasses
        NamingEnumeration ocList = objectClassAttr.getAll();
        
        while ( ocList.hasMoreElements() )
        {
            String ocName = ( String ) ocList.nextElement();
            
            if ( !ocName.equalsIgnoreCase( "top" ) )
            {
                String ocLowerName = ocName.toLowerCase();
                
                ObjectClass objectClass = globalRegistries.getObjectClassRegistry().lookup( ocLowerName );
                
                if ( !objectClasses.contains( ocLowerName ) )
                {
                    objectClasses.add( ocLowerName );
                }
                
                List ocSuperiors = (List)superiors.get( objectClass.getOid() );
                
                if ( ( ocSuperiors != null ) && ( ocSuperiors.size() != 0 ) )
                {
                    Iterator iter = ocSuperiors.iterator();
                    
                    while ( iter.hasNext() )
                    {
                        ObjectClass oc = (ObjectClass)iter.next();
                        
                        if ( !objectClasses.contains( oc.getName().toLowerCase() ) )
                        {
                            objectClasses.add( oc.getName() );
                        }
                    }
                }
            }
        }
        
        // Now, reset the ObjectClass attribute and put the new list into it
        objectClassAttr.clear();
        
        Iterator iter = objectClasses.iterator();
        
        while ( iter.hasNext() )
        {
            objectClassAttr.add( iter.next() );
        }
    }

    /**
     * Check that the modify operations are allowed, and the conform to
     * the schema.
     * 
     * @param next The next interceptor to call when we are done with the local operation
     * @param name The DN on which the modification is being done 
     * @param modOp The modification. One of :
     *   DirContext.ADD_ATTRIBUTE
     *   DirContext.REMOVE_ATTRIBUTE
     *   DirContext.REPLACE_ATTRIBUTE
     * @param mods The modifications to check. Each operation is atomic, and should
     * be applied to a copy of the entry, in order to check that the schema is not
     * violated at the end. For instance, we can't delete an attribute that does
     * not exist and add it later. The opposite is legal.
     * 
     * @throws NamingException The generic exception we get if an illegal operation occurs
     * @throws LdapNameNotFoundException If we don't find the entry, then this exception is thrown.
     * @throws LdapInvalidAttributeIdentifierException The modified attribute is not known
     * by the schema, or the Entry is not extensible.
     * @throws LdapNoSuchAttributeException The modified Attribute does not exist in the 
     * current entry or is not added by a previous modification operation.
     * @throws LdapSchemaViolationException Another schema violation occured.
     */
    public void modify( NextInterceptor next, LdapDN name, int modOp, Attributes mods ) throws NamingException
    {
        // First, we get the entry from the backend. If it does not exist, then we throw an exception
        Attributes entry = nexus.lookup( name );
        
        if ( entry == null )
        {
            log.error( "No entry with this name :{}", name );
            throw new LdapNameNotFoundException( "The entry which name is " + name + " is not found." );
        }
        
        Attribute objectClass = getResultantObjectClasses( modOp, mods.get( "objectClass" ), entry.get( "objectClass" ) );
        ObjectClassRegistry ocRegistry = this.globalRegistries.getObjectClassRegistry();
        AttributeTypeRegistry atRegistry = this.globalRegistries.getAttributeTypeRegistry();

        NamingEnumeration changes = mods.getIDs();
        
        Attributes tmpEntryForAdd = (Attributes)entry.clone();
        
        while ( changes.hasMore() )
        {
            String id = ( String ) changes.next();
            Attribute change = mods.get( id );

            if ( !atRegistry.hasAttributeType( change.getID() ) && !AttributeUtils.containsValueCaseIgnore( objectClass, "extensibleObject" ) )
            {
                throw new LdapInvalidAttributeIdentifierException( "unrecognized attributeID " + change.getID() );
            }
            
            if ( modOp == DirContext.ADD_ATTRIBUTE )
            {
                tmpEntryForAdd.put( change );
                if ( change.size() == 0 )
                {
                    // not ok for add but ok for replace and delete
                    throw new LdapInvalidAttributeValueException( "No value is not a valid value for an attribute.", 
                        ResultCodeEnum.INVALIDATTRIBUTESYNTAX );
                }
            }

            if ( modOp == DirContext.REMOVE_ATTRIBUTE && entry.get( change.getID() ) == null )
            {
                throw new LdapNoSuchAttributeException();
            }

            // for required attributes we need to check if all values are removed
            // if so then we have a schema violation that must be thrown
            if ( modOp == DirContext.REMOVE_ATTRIBUTE && isRequired( change.getID(), objectClass )
                && isCompleteRemoval( change, entry ) )
            {
                throw new LdapSchemaViolationException( ResultCodeEnum.OBJECTCLASSVIOLATION );
            }
        }

        if ( modOp == DirContext.ADD_ATTRIBUTE )
        {
            assertNumberOfAttributeValuesValid( tmpEntryForAdd );
        }
        
        if ( modOp == DirContext.REMOVE_ATTRIBUTE )
        {
            SchemaChecker.preventRdnChangeOnModifyRemove( name, modOp, mods, this.globalRegistries.getOidRegistry() );
            SchemaChecker.preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, modOp, mods, objectClass );
        }

        if ( modOp == DirContext.REPLACE_ATTRIBUTE )
        {
            SchemaChecker.preventRdnChangeOnModifyReplace( name, modOp, mods, this.globalRegistries.getOidRegistry() );
            SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, modOp, mods );
            assertNumberOfAttributeValuesValid( mods );
        }

        // let's figure out if we need to add or take away from mods to maintain 
        // the objectClass attribute with it's hierarchy of ancestors 
        if ( mods.get( "objectClass" ) != null )
        {
            Attribute alteredObjectClass = ( Attribute ) objectClass.clone();
            alterObjectClasses( alteredObjectClass );

            if ( !alteredObjectClass.equals( objectClass ) )
            {
                Attribute ocMods = mods.get( "objectClass" );
                switch ( modOp )
                {
                    case ( DirContext.ADD_ATTRIBUTE  ):
                        if ( AttributeUtils.containsValueCaseIgnore( ocMods, "top" ) )
                        {
                            ocMods.remove( "top" );
                        }
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                        
                    case ( DirContext.REMOVE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.remove( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                        
                    case ( DirContext.REPLACE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                        
                    default:
                        break;
                }
            }
        }

        next.modify( name, modOp, mods );
    }

    public void modify( NextInterceptor next, LdapDN name, ModificationItemImpl[] mods ) throws NamingException
    {
        // First, we get the entry from the backend. If it does not exist, then we throw an exception
        Attributes entry = nexus.lookup( name );

        if ( entry == null )
        {
            log.error( "No entry with this name :{}", name );
            throw new LdapNameNotFoundException( "The entry which name is " + name + " is not found." );
        }
        
        // We will use this temporary entry to check that the modifications
        // can be applied as atomic operations
        Attributes tmpEntry = (Attributes)entry.clone();
        
        Set modset = new HashSet();
        ModificationItemImpl objectClassMod = null;
        
        // Check that we don't have two times the same modification.
        // This is somehow useless, as modification operations are supposed to
        // be atomic, so we may have a sucession of Add, DEL, ADD operations
        // for the same attribute, and this will be legal.
        // @TODO : check if we can remove this test.
        for ( int ii = 0; ii < mods.length; ii++ )
        {
            if ( mods[ii].getAttribute().getID().equalsIgnoreCase( "objectclass" ) )
            {
                objectClassMod = mods[ii];
            }
            
            // Freak out under some weird cases
            if ( mods[ii].getAttribute().size() == 0 )
            {
                // not ok for add but ok for replace and delete
                if ( mods[ii].getModificationOp() == DirContext.ADD_ATTRIBUTE )
                {
                    throw new LdapInvalidAttributeValueException( "No value is not a valid value for an attribute.", 
                        ResultCodeEnum.INVALIDATTRIBUTESYNTAX );
                }
            }

            StringBuffer keybuf = new StringBuffer();
            keybuf.append( mods[ii].getModificationOp() );
            keybuf.append( mods[ii].getAttribute().getID() );

            for ( int jj = 0; jj < mods[ii].getAttribute().size(); jj++ )
            {
                keybuf.append( mods[ii].getAttribute().get( jj ) );
            }
            
            if ( !modset.add( keybuf.toString() ) && mods[ii].getModificationOp() == DirContext.ADD_ATTRIBUTE )
            {
                throw new LdapAttributeInUseException( "found two copies of the following modification item: "
                    + mods[ii] );
            }
        }
        
        // Get the objectClass attribute.
        Attribute objectClass;

        if ( objectClassMod == null )
        {
            objectClass = entry.get( "objectClass" );
        }
        else
        {
            objectClass = getResultantObjectClasses( objectClassMod.getModificationOp(), objectClassMod.getAttribute(),
                entry.get( "objectClass" ) );
        }

        ObjectClassRegistry ocRegistry = this.globalRegistries.getObjectClassRegistry();
        AttributeTypeRegistry atRegistry = this.globalRegistries.getAttributeTypeRegistry();

        // -------------------------------------------------------------------
        // DIRSERVER-646 Fix: Replacing an unknown attribute with no values 
        // (deletion) causes an error
        // -------------------------------------------------------------------
        
        if ( mods.length == 1 && 
             mods[0].getAttribute().size() == 0 && 
             mods[0].getModificationOp() == DirContext.REPLACE_ATTRIBUTE &&
             ! atRegistry.hasAttributeType( mods[0].getAttribute().getID() ) )
        {
            return;
        }
        
        // Now, apply the modifications on the cloned entry before applyong it to the
        // real object.
        for ( int ii = 0; ii < mods.length; ii++ )
        {
            int modOp = mods[ii].getModificationOp();
            Attribute change = mods[ii].getAttribute();

            if ( !atRegistry.hasAttributeType( change.getID() ) && !AttributeUtils.containsValueCaseIgnore( objectClass, "extensibleObject" ) )
            {
                throw new LdapInvalidAttributeIdentifierException();
            }
            
            // We will forbid modification of operationnal attributes which are not
            // user modifiable.
            AttributeType attributeType = atRegistry.lookup( change.getID() );
            
            if //( ( attributeType.getUsage() == UsageEnum.DIRECTORYOPERATION ) &&
                 ( !attributeType.isCanUserModify() ) //)
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
                        attr = new LockableAttributeImpl( change.getID() );
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
                        log.error( "Trying to remove an inexistant attribute: " + change.getID() );
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
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECTCLASSVIOLATION );
                        }
                    }
                    else
                    {
                        // for required attributes we need to check if all values are removed
                        // if so then we have a schema violation that must be thrown
                        if ( isRequired( change.getID(), objectClass ) && isCompleteRemoval( change, entry ) )
                        {
                            log.error( "Trying to remove a required attribute: " + change.getID() );
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECTCLASSVIOLATION );
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
                            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECTCLASSVIOLATION );
                        }
                        
                        // Put back the attribute in the entry
                        tmpEntry.put( modified );
                    }
                    
                    SchemaChecker.preventRdnChangeOnModifyRemove( name, modOp, change, 
                        this.globalRegistries.getOidRegistry() ); 
                    SchemaChecker
                        .preventStructuralClassRemovalOnModifyRemove( ocRegistry, name, modOp, change, objectClass );
                    break;
                        
                case DirContext.REPLACE_ATTRIBUTE :
                    SchemaChecker.preventRdnChangeOnModifyReplace( name, modOp, change, 
                        globalRegistries.getOidRegistry() );
                    SchemaChecker.preventStructuralClassRemovalOnModifyReplace( ocRegistry, name, modOp, change );
                    
                    attr = tmpEntry.get( change.getID() );
                    
                    if ( attr != null )
                    {
                        tmpEntry.remove( change.getID() );
                    }
                    attr = new LockableAttributeImpl( change.getID() );
                    
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
                        if ( AttributeUtils.containsValueCaseIgnore( ocMods, "top" ) )
                        {
                            ocMods.remove( "top" );
                        }
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                    case ( DirContext.REMOVE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.remove( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                    case ( DirContext.REPLACE_ATTRIBUTE  ):
                        for ( int ii = 0; ii < alteredObjectClass.size(); ii++ )
                        {
                            if ( !AttributeUtils.containsValueCaseIgnore( objectClass, alteredObjectClass.get( ii ) ) )
                            {
                                ocMods.add( alteredObjectClass.get( ii ) );
                            }
                        }
                        break;
                    default:
                }
            }
        }
        
        //assertNumberOfAttributeValuesValid( tmpEntry );

        next.modify( name, mods );
    }


    private void filterObjectClass( Attributes entry ) throws NamingException
    {
        List objectClasses = new ArrayList();
        Attribute oc = entry.get( "objectClass" );
        
        if ( oc != null )
        {
            getObjectClasses( oc, objectClasses );

            entry.remove( "objectClass" );
            
            Attribute newOc = new LockableAttributeImpl( "ObjectClass" );
            
            for ( int i = 0; i < objectClasses.size(); i++ )
            {
                Object currentOC = objectClasses.get(i);
                
                if ( currentOC instanceof String )
                {
                    newOc.add( currentOC );
                }
                else
                {
                    newOc.add( ( (ObjectClass)currentOC ).getName() );
                }
            }
            
            newOc.add( "top" );
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
            boolean asBinary = false;

            if ( globalRegistries.getAttributeTypeRegistry().hasAttributeType( id ) )
            {
                type = globalRegistries.getAttributeTypeRegistry().lookup( id );
            }
            else
            {
                continue;
            }

            asBinary = !type.getSyntax().isHumanReadible();
            asBinary = asBinary || binaries.contains( type );

            if ( asBinary )
            {
                Attribute attribute = entry.get( id );
                Attribute binary = new LockableAttributeImpl( id );

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
            //filterTop( result.getAttributes() );
            filterObjectClass( result.getAttributes() );
            
            return true;
        }
    }

    private void check( LdapDN dn, Attributes entry ) throws NamingException
    {
        NamingEnumeration attrEnum = entry.getIDs();
        
        // ---------------------------------------------------------------
        // First, make sure all attributes are valid schema defined attributes
        // ---------------------------------------------------------------

        while ( attrEnum.hasMoreElements() )
        {
            String name = ( String ) attrEnum.nextElement();
            
            if ( !globalRegistries.getAttributeTypeRegistry().hasAttributeType( name ) )
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
        Attribute objectClassAttr = entry.get( "objectClass" );
        List ocs = new ArrayList();
        
        alterObjectClasses( objectClassAttr );

        Set must = getAllMust( objectClassAttr.getAll() );
        Set allowed = getAllAllowed( objectClassAttr.getAll(), must );

        boolean hasExtensibleObject = getObjectClasses( objectClassAttr, ocs );
        
        assertRequiredAttributesPresent( dn, entry, must );
        assertNumberOfAttributeValuesValid( entry );

        if ( !hasExtensibleObject )
        {
            assertAllAttributesAllowed( dn, entry, allowed );
        }
        
        // Check the attributes values and transform them to String if necessary
        assertHumanReadible( entry );
    }
    
    /**
     * Check that all the attributes exist in the schema for this entry.
     */
    public void add( NextInterceptor next, LdapDN normName, Attributes attrs ) throws NamingException
    {
        check( normName, attrs );
        next.add( normName, attrs );
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
        AttributeTypeRegistry registry = this.globalRegistries.getAttributeTypeRegistry();

        if ( attribute.size() > 1 && registry.lookup( attribute.getID() ).isSingleValue() )
        {                
            throw new LdapInvalidAttributeValueException( "More than one value has been provided " +
                "for the single-valued attribute: " + attribute.getID(), ResultCodeEnum.CONSTRAINTVIOLATION );
        }
    }

    /**
     * Checks to see the presence of all required attributes within an entry.
     */
    private void assertRequiredAttributesPresent( LdapDN dn, Attributes entry, Set must ) 
        throws NamingException
    {
        NamingEnumeration attributes = entry.getAll();
        
        while ( attributes.hasMoreElements() && ( must.size() > 0 ) )
        {
            Attribute attribute = ( Attribute ) attributes.nextElement();
            String oid = globalRegistries.getOidRegistry().getOid( attribute.getID() );
            must.remove( oid );
        }
        
        if ( must.size() != 0 )
        {
            throw new LdapSchemaViolationException( "Required attributes " + 
                must + " not found within entry " + dn.getUpName(), 
                ResultCodeEnum.OBJECTCLASSVIOLATION );
        }
    }

    /**
     * Check that all the attribute's values which are Human Readible can be transformed
     * to valid String if they are stored as byte[].
     */
    private void assertHumanReadible( Attributes entry ) throws NamingException
    {
        NamingEnumeration attributes = entry.getAll();
        boolean isEntryModified = false;
        Attributes cloneEntry = null;
        
        // First, loop on all attributes
        while ( attributes.hasMoreElements() )
        {
            Attribute attribute = ( Attribute ) attributes.nextElement();
            
            AttributeType attributeType = globalRegistries.getAttributeTypeRegistry().lookup( attribute.getID() );
            
            // If the attributeType is H-R, check alll of its values
            if ( attributeType.getSyntax().isHumanReadible() )
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
                        throw new NamingException( "The value stored in an Human Readible attribute is not a String" );
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
    
    /**
     * Checks to see if an attribute is required by as determined from an entry's
     * set of objectClass attribute values.
     *
     * @param attrId the attribute to test if required by a set of objectClass values
     * @param objectClass the objectClass values
     * @return true if the objectClass values require the attribute, false otherwise
     * @throws NamingException if the attribute is not recognized
     */
    private void assertAllAttributesAllowed( LdapDN dn, Attributes attributes, Set allowed ) throws NamingException
    {
        // Never check the attributes if the extensibleObject objectClass is
        // declared for this entry
        Attribute objectClass = attributes.get( "objectclass" );
        
        if ( AttributeUtils.containsValueCaseIgnore( objectClass, "extensibleobject" ) )
        {
            return;
        }
        
        NamingEnumeration attrs = attributes.getAll();
        
        while ( attrs.hasMoreElements() )
        {
            Attribute attribute = (Attribute)attrs.nextElement();
            String attrId = attribute.getID();
            String attrOid = globalRegistries.getOidRegistry().getOid( attrId );
            
            AttributeType attributeType = globalRegistries.getAttributeTypeRegistry().lookup( attrOid );
            
            if ( !attributeType.isCollective() && ( attributeType.getUsage() == UsageEnum.USERAPPLICATIONS ) )
            {
                if ( !allowed.contains( attrOid ) )
                {
                    throw new LdapSchemaViolationException( "Attribute " + 
                        attribute.getID() + " not declared in objectClasses of entry " + dn.getUpName(), 
                        ResultCodeEnum.OBJECTCLASSVIOLATION );
                }
            }
        }
    }
}