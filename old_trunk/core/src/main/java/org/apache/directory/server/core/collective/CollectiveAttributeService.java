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
package org.apache.directory.server.core.collective;


import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.DirectoryServiceConfiguration;
import org.apache.directory.server.core.configuration.InterceptorConfiguration;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.ServerSearchResult;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.AttributeUtils;


/**
 * An interceptor based service dealing with collective attribute
 * management.  This service intercepts read operations on entries to
 * inject collective attribute value pairs into the response based on
 * the entires inclusion within collectiveAttributeSpecificAreas and
 * collectiveAttributeInnerAreas.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class CollectiveAttributeService extends BaseInterceptor
{
    public static final String EXCLUDE_ALL_COLLECTIVE_ATTRIBUTES_OID = "2.5.18.0";
    public static final String EXCLUDE_ALL_COLLECTIVE_ATTRIBUTES = "excludeAllCollectiveAttributes";
    
    /**
     * the search result filter to use for collective attribute injection
     */
    private final SearchResultFilter SEARCH_FILTER = new SearchResultFilter()
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
            throws NamingException
        {
            LdapDN name = ((ServerSearchResult)result).getDn();
            
            if ( !name.isNormalized() )
            {
            	name = LdapDN.normalize( name, attrTypeRegistry.getNormalizerMapping() );
            }
            
            Attributes entry = result.getAttributes();
            String[] retAttrs = controls.getReturningAttributes();
            addCollectiveAttributes( name, entry, retAttrs );
            return true;
        }
    };

    private AttributeTypeRegistry attrTypeRegistry = null;
    private PartitionNexus nexus = null;
    
    private CollectiveAttributesSchemaChecker collectiveAttributesSchemaChecker = null;


    public void init( DirectoryServiceConfiguration factoryCfg, InterceptorConfiguration cfg ) throws NamingException
    {
        super.init( factoryCfg, cfg );
        nexus = factoryCfg.getPartitionNexus();
        attrTypeRegistry = factoryCfg.getRegistries().getAttributeTypeRegistry();
        collectiveAttributesSchemaChecker = new CollectiveAttributesSchemaChecker(nexus, attrTypeRegistry);
    }


    /**
     * Adds the set of collective attributes requested in the returning attribute list
     * and contained in subentries referenced by the entry. Excludes collective
     * attributes that are specified to be excluded via the 'collectiveExclusions'
     * attribute in the entry.
     *
     * @param normName name of the entry being processed
     * @param entry the entry to have the collective attributes injected
     * @param retAttrs array or attribute type to be specifically included in the result entry(s)
     * @throws NamingException if there are problems accessing subentries
     */
    private void addCollectiveAttributes( LdapDN normName, Attributes entry, String[] retAttrs ) throws NamingException
    {
        Attribute caSubentries = null;
        
        if ( ( retAttrs == null ) || ( retAttrs.length != 1 ) || ( retAttrs[0] != SchemaConstants.ALL_USER_ATTRIBUTES ) )
        {
            Attributes entryWithCAS = nexus.lookup( new LookupOperationContext( normName, new String[] { 
            	SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT } ) );
            caSubentries = entryWithCAS.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );
        }
        else
        {
            caSubentries = entry.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );
        }
        
        /*
         * If there are no collective attribute subentries referenced
         * then we have no collective attributes to inject to this entry.
         */
        if ( caSubentries == null )
        {
            return;
        }
    
        /*
         * Before we proceed we need to lookup the exclusions within the
         * entry and build a set of exclusions for rapid lookup.  We use
         * OID values in the exclusions set instead of regular names that
         * may have case variance.
         */
        Attribute collectiveExclusions = entry.get( SchemaConstants.COLLECTIVE_EXCLUSIONS_AT );
        Set<String> exclusions = new HashSet<String>();
        
        if ( collectiveExclusions != null )
        {
            if ( AttributeUtils.containsValueCaseIgnore( collectiveExclusions, EXCLUDE_ALL_COLLECTIVE_ATTRIBUTES_OID )
                || AttributeUtils.containsValue( collectiveExclusions, EXCLUDE_ALL_COLLECTIVE_ATTRIBUTES, attrTypeRegistry.lookup( SchemaConstants.COLLECTIVE_EXCLUSIONS_AT_OID ) ) )
            {
                /*
                 * This entry does not allow any collective attributes
                 * to be injected into itself.
                 */
                return;
            }

            exclusions = new HashSet<String>();
            
            for ( int ii = 0; ii < collectiveExclusions.size(); ii++ )
            {
                AttributeType attrType = attrTypeRegistry.lookup( ( String ) collectiveExclusions.get( ii ) );
                exclusions.add( attrType.getOid() );
            }
        }
        
        /*
         * If no attributes are requested specifically
         * then it means all user attributes are requested.
         * So populate the array with all user attributes indicator: "*".
         */
        if ( retAttrs == null )
        {
            retAttrs = SchemaConstants.ALL_USER_ATTRIBUTES_ARRAY;
        }
        
        /*
         * Construct a set of requested attributes for easier tracking.
         */ 
        Set<String> retIdsSet = new HashSet<String>( retAttrs.length );
        
        for ( String retAttr:retAttrs )
        {
            retIdsSet.add( retAttr.toLowerCase() );
        }

        /*
         * For each collective subentry referenced by the entry we lookup the
         * attributes of the subentry and copy collective attributes from the
         * subentry into the entry.
         */
        for ( int ii = 0; ii < caSubentries.size(); ii++ )
        {
            String subentryDnStr = ( String ) caSubentries.get( ii );
            LdapDN subentryDn = new LdapDN( subentryDnStr );
            Attributes subentry = nexus.lookup( new LookupOperationContext( subentryDn ) );
            NamingEnumeration<String> attrIds = subentry.getIDs();
            
            while ( attrIds.hasMore() )
            {
                String attrId = attrIds.next();
                AttributeType attrType = attrTypeRegistry.lookup( attrId );

                if ( !attrType.isCollective() )
                {
                    continue;
                }
                
                /*
                 * Skip the addition of this collective attribute if it is excluded
                 * in the 'collectiveAttributes' attribute.
                 */
                if ( exclusions.contains( attrType.getOid() ) )
                {
                    continue;
                }
                
                Set<AttributeType> allSuperTypes = getAllSuperTypes( attrType );
                Iterator<String> it = retIdsSet.iterator();
                
                while ( it.hasNext() )
                {
                    String retId = it.next();
                    
                    if ( retId.equals( SchemaConstants.ALL_USER_ATTRIBUTES ) || retId.equals( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
                    {
                        continue;
                    }
                    
                    AttributeType retType = attrTypeRegistry.lookup( retId );
                    
                    if ( allSuperTypes.contains( retType ) )
                    {
                        retIdsSet.add( attrId );
                        break;
                    }
                }

                /*
                 * If not all attributes or this collective attribute requested specifically
                 * then bypass the inclusion process.
                 */
                if ( !( retIdsSet.contains( SchemaConstants.ALL_USER_ATTRIBUTES ) || retIdsSet.contains( attrId ) ) )
                {
                    continue;
                }
                
                Attribute subentryColAttr = subentry.get( attrId );
                Attribute entryColAttr = entry.get( attrId );

                /*
                 * If entry does not have attribute for collective attribute then create it.
                 */
                if ( entryColAttr == null )
                {
                    entryColAttr = new AttributeImpl( attrId );
                    entry.put( entryColAttr );
                }

                /*
                 *  Add all the collective attribute values in the subentry
                 *  to the currently processed collective attribute in the entry.
                 */
                for ( int jj = 0; jj < subentryColAttr.size(); jj++ )
                {
                    entryColAttr.add( subentryColAttr.get( jj ) );
                }
            }
        }
    }
    
    
    private Set<AttributeType> getAllSuperTypes( AttributeType id ) throws NamingException
    {
        Set<AttributeType> allSuperTypes = new HashSet<AttributeType>();
        AttributeType superType = id;
        
        while ( superType != null )
        {
            superType = superType.getSuperior();
            
            if ( superType != null )
            {
                allSuperTypes.add( superType );
            }
        }
        
        return allSuperTypes;
    }


    // ------------------------------------------------------------------------
    // Interceptor Method Overrides
    // ------------------------------------------------------------------------
    public Attributes lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws NamingException
    {
        Attributes result = nextInterceptor.lookup( opContext );
        
        if ( result == null )
        {
            return null;
        }
        
        if ( ( opContext.getAttrsId() == null ) || ( opContext.getAttrsId().size() == 0 ) ) 
        {
            addCollectiveAttributes( opContext.getDn(), result, SchemaConstants.ALL_USER_ATTRIBUTES_ARRAY );
        }
        else
        {
            addCollectiveAttributes( opContext.getDn(), result, opContext.getAttrsIdArray() );
        }

        return result;
    }


    public NamingEnumeration<SearchResult> list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws NamingException
    {
        NamingEnumeration<SearchResult> result = nextInterceptor.list( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();
        
        return new SearchResultFilteringEnumeration( result, new SearchControls(), invocation, SEARCH_FILTER, "List collective Filter" );
    }


    public NamingEnumeration<SearchResult> search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws NamingException
    {
    	/*
        SearchControls sc = opContext.getSearchControls();
        String[] returnedAttrs = sc.getReturningAttributes();
        
        String[] newReturnedAttrs = new String[returnedAttrs.length + 1];
        System.arraycopy( returnedAttrs, 0, newReturnedAttrs, 0, returnedAttrs.length );
        newReturnedAttrs[returnedAttrs.length] = SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT;
        
        sc.setReturningAttributes( newReturnedAttrs );
        */
        
        NamingEnumeration<SearchResult> result = nextInterceptor.search( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();
        
        return new SearchResultFilteringEnumeration( 
            result, opContext.getSearchControls(), invocation, SEARCH_FILTER, "Search collective Filter" );
    }
    
    // ------------------------------------------------------------------------
    // Partial Schema Checking
    // ------------------------------------------------------------------------
    
    public void add( NextInterceptor next, AddOperationContext opContext ) throws NamingException
    {
        collectiveAttributesSchemaChecker.checkAdd( opContext.getDn(), opContext.getEntry() );
        
        next.add( opContext );
        //super.add( next, opContext );
    }


    public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws NamingException
    {
        collectiveAttributesSchemaChecker.checkModify( opContext.getDn(), opContext.getModItems() );

        next.modify( opContext );
        //super.modify( next, opContext );
    }
}
