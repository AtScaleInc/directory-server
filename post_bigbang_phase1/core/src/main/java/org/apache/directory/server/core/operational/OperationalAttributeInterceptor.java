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
package org.apache.directory.server.core.operational;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.*;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.name.AttributeTypeAndValue;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.DateUtils;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.*;


/**
 * An {@link Interceptor} that adds or modifies the default attributes
 * of entries. There are four default attributes for now;
 * <tt>'creatorsName'</tt>, <tt>'createTimestamp'</tt>, <tt>'modifiersName'</tt>,
 * and <tt>'modifyTimestamp'</tt>.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class OperationalAttributeInterceptor extends BaseInterceptor
{
    private final SearchResultFilter DENORMALIZING_SEARCH_FILTER = new SearchResultFilter()
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls ) 
            throws NamingException
        {
            return controls.getReturningAttributes() == null || filterDenormalized( result.getAttributes() );

        }
    };

    /**
     * the database search result filter to register with filter service
     */
    private final SearchResultFilter SEARCH_FILTER = new SearchResultFilter()
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
            throws NamingException
        {
            return controls.getReturningAttributes() != null || filter( result.getAttributes() );

        }
    };


    private AttributeTypeRegistry registry;

    private DirectoryService service;

    private LdapDN subschemaSubentryDn;


    /**
     * Creates the operational attribute management service interceptor.
     */
    public OperationalAttributeInterceptor()
    {
    }


    public void init( DirectoryService directoryService ) throws NamingException
    {
        service = directoryService;
        registry = directoryService.getRegistries().getAttributeTypeRegistry();

        // stuff for dealing with subentries (garbage for now)
        String subschemaSubentry = ( String ) service.getPartitionNexus()
                .getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        subschemaSubentryDn = new LdapDN( subschemaSubentry );
        subschemaSubentryDn.normalize( directoryService.getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
    }


    public void destroy()
    {
    }


    /**
     * Adds extra operational attributes to the entry before it is added.
     */
    public void add(NextInterceptor nextInterceptor, AddOperationContext opContext )
        throws NamingException
    {
        String principal = getPrincipal().getName();
        Attributes entry = opContext.getEntry();

        Attribute attribute = new AttributeImpl( SchemaConstants.CREATORS_NAME_AT );
        attribute.add( principal );
        entry.put( attribute );

        attribute = new AttributeImpl( SchemaConstants.CREATE_TIMESTAMP_AT );
        attribute.add( DateUtils.getGeneralizedTime() );
        entry.put( attribute );

        nextInterceptor.add( opContext );
    }


    public void modify( NextInterceptor nextInterceptor, ModifyOperationContext opContext )
        throws NamingException
    {
        nextInterceptor.modify( opContext );
        
        if ( opContext.getDn().getNormName().equals( subschemaSubentryDn.getNormName() ) ) 
        {
            return;
        }

        // -------------------------------------------------------------------
        // Add the operational attributes for the modifier first
        // -------------------------------------------------------------------
        
        List<ModificationItemImpl> modItemList = new ArrayList<ModificationItemImpl>(2);
        
        Attribute attribute = new AttributeImpl( SchemaConstants.MODIFIERS_NAME_AT );
        attribute.add( getPrincipal().getName() );
        ModificationItemImpl modifiers = new ModificationItemImpl( DirContext.REPLACE_ATTRIBUTE, attribute );
        modifiers.setServerModified();
        modItemList.add( modifiers );
        
        attribute = new AttributeImpl( SchemaConstants.MODIFY_TIMESTAMP_AT );
        attribute.add( DateUtils.getGeneralizedTime() );
        ModificationItemImpl timestamp = new ModificationItemImpl( DirContext.REPLACE_ATTRIBUTE, attribute );
        timestamp.setServerModified();
        modItemList.add( timestamp );

        // -------------------------------------------------------------------
        // Make the modify() call happen
        // -------------------------------------------------------------------

        ModifyOperationContext newModify = new ModifyOperationContext( opContext.getDn(), modItemList );
        service.getPartitionNexus().modify( newModify );
    }


    public void rename( NextInterceptor nextInterceptor, RenameOperationContext opContext )
        throws NamingException
    {
        nextInterceptor.rename( opContext );

        // add operational attributes after call in case the operation fails
        Attributes attributes = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( SchemaConstants.MODIFIERS_NAME_AT );
        attribute.add( getPrincipal().getName() );
        attributes.put( attribute );

        attribute = new AttributeImpl( SchemaConstants.MODIFY_TIMESTAMP_AT );
        attribute.add( DateUtils.getGeneralizedTime() );
        attributes.put( attribute );

        LdapDN newDn = ( LdapDN ) opContext.getDn().clone();
        newDn.remove( opContext.getDn().size() - 1 );
        newDn.add( opContext.getNewRdn() );
        newDn.normalize( registry.getNormalizerMapping() );
        
        List<ModificationItemImpl> items = ModifyOperationContext.createModItems( attributes, DirContext.REPLACE_ATTRIBUTE );

        ModifyOperationContext newModify = new ModifyOperationContext( newDn, items );
        
        service.getPartitionNexus().modify( newModify );
    }


    public void move( NextInterceptor nextInterceptor, MoveOperationContext opContext ) throws NamingException
    {
        nextInterceptor.move( opContext );

        // add operational attributes after call in case the operation fails
        Attributes attributes = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( SchemaConstants.MODIFIERS_NAME_AT );
        attribute.add( getPrincipal().getName() );
        attributes.put( attribute );

        attribute = new AttributeImpl( SchemaConstants.MODIFY_TIMESTAMP_AT );
        attribute.add( DateUtils.getGeneralizedTime() );
        attributes.put( attribute );

        List<ModificationItemImpl> items = ModifyOperationContext.createModItems( attributes, DirContext.REPLACE_ATTRIBUTE );


        ModifyOperationContext newModify = 
            new ModifyOperationContext( opContext.getParent(), items );
        
        service.getPartitionNexus().modify( newModify );
    }


    public void moveAndRename( NextInterceptor nextInterceptor, MoveAndRenameOperationContext opContext )
        throws NamingException
    {
        nextInterceptor.moveAndRename( opContext );

        // add operational attributes after call in case the operation fails
        Attributes attributes = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( SchemaConstants.MODIFIERS_NAME_AT );
        attribute.add( getPrincipal().getName() );
        attributes.put( attribute );

        attribute = new AttributeImpl( SchemaConstants.MODIFY_TIMESTAMP_AT );
        attribute.add( DateUtils.getGeneralizedTime() );
        attributes.put( attribute );

        List<ModificationItemImpl> items = ModifyOperationContext.createModItems( attributes, DirContext.REPLACE_ATTRIBUTE );

        ModifyOperationContext newModify = 
            new ModifyOperationContext( 
        		opContext.getParent(), items );
        
        service.getPartitionNexus().modify( newModify );
    }


    public Attributes lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws NamingException
    {
        Attributes result = nextInterceptor.lookup( opContext );
        
        if ( result == null )
        {
            return null;
        }

        if ( opContext.getAttrsId() == null )
        {
            filter( result );
        }
        else
        {
            filter( opContext, result );
        }
        
        return result;
    }


    public NamingEnumeration<SearchResult> list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws NamingException
    {
        NamingEnumeration<SearchResult> result = nextInterceptor.list( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();
        
        return new SearchResultFilteringEnumeration( result, new SearchControls(), invocation, SEARCH_FILTER, "List Operational Filter" );
    }


    public NamingEnumeration<SearchResult> search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws NamingException
    {
        Invocation invocation = InvocationStack.getInstance().peek();
        NamingEnumeration<SearchResult> result = nextInterceptor.search( opContext );
        SearchControls searchCtls = opContext.getSearchControls();
        
        if ( searchCtls.getReturningAttributes() != null )
        {
            if ( service.isDenormalizeOpAttrsEnabled() )
            {
                return new SearchResultFilteringEnumeration( result, searchCtls, invocation, DENORMALIZING_SEARCH_FILTER, "Search Operational Filter denormalized" );
            }
                
            return result;
        }

        return new SearchResultFilteringEnumeration( result, searchCtls, invocation, SEARCH_FILTER , "Search Operational Filter");
    }


    /**
     * Filters out the operational attributes within a search results attributes.  The attributes are directly
     * modified.
     *
     * @param attributes the resultant attributes to filter
     * @return true always
     * @throws NamingException if there are failures in evaluation
     */
    private boolean filter( Attributes attributes ) throws NamingException
    {
        NamingEnumeration<String> list = attributes.getIDs();

        while ( list.hasMore() )
        {
            String attrId =  list.next();

            AttributeType type = null;

            if ( registry.hasAttributeType( attrId ) )
            {
                type = registry.lookup( attrId );
            }

            if ( type != null && type.getUsage() != UsageEnum.USER_APPLICATIONS )
            {
                attributes.remove( attrId );
            }
        }
        
        return true;
    }


    private void filter( LookupOperationContext lookupContext, Attributes entry ) throws NamingException
    {
        LdapDN dn = lookupContext.getDn();
        List<String> ids = lookupContext.getAttrsId();
        
        // still need to protect against returning op attrs when ids is null
        if ( ids == null )
        {
            filter( entry );
            return;
        }

        if ( dn.size() == 0 )
        {
            Set<String> idsSet = new HashSet<String>( ids.size() );

            for ( String id:ids  )
            {
                idsSet.add( id.toLowerCase() );
            }

            NamingEnumeration<String> list = entry.getIDs();

            while ( list.hasMore() )
            {
                String attrId = list.nextElement().toLowerCase();

                if ( !idsSet.contains( attrId ) )
                {
                    entry.remove( attrId );
                }
            }
        }

        denormalizeEntryOpAttrs( entry );
        
        // do nothing past here since this explicity specifies which
        // attributes to include - backends will automatically populate
        // with right set of attributes using ids array
    }

    
    public void denormalizeEntryOpAttrs( Attributes entry ) throws NamingException
    {
        if ( service.isDenormalizeOpAttrsEnabled() )
        {
            AttributeType type = registry.lookup( SchemaConstants.CREATORS_NAME_AT );
            Attribute attr = AttributeUtils.getAttribute( entry, type );

            if ( attr != null )
            {
                LdapDN creatorsName = new LdapDN( ( String ) attr.get() );
                
                attr.clear();
                attr.add( denormalizeTypes( creatorsName ).getUpName() );
            }
            
            type = registry.lookup( SchemaConstants.MODIFIERS_NAME_AT );
            attr = AttributeUtils.getAttribute( entry, type );
            
            if ( attr != null )
            {
                LdapDN modifiersName = new LdapDN( ( String ) attr.get() );

                attr.clear();
                attr.add( denormalizeTypes( modifiersName ).getUpName() );
            }

            type = registry.lookup( ApacheSchemaConstants.SCHEMA_MODIFIERS_NAME_AT );
            attr = AttributeUtils.getAttribute( entry, type );
            
            if ( attr != null )
            {
                LdapDN modifiersName = new LdapDN( ( String ) attr.get() );

                attr.clear();
                attr.add( denormalizeTypes( modifiersName ).getUpName() );
            }
        }
    }

    
    /**
     * Does not create a new DN but alters existing DN by using the first
     * short name for an attributeType definition.
     * 
     * @param dn the normalized distinguished name
     * @return the distinuished name denormalized
     * @throws NamingException if there are problems denormalizing
     */
    public LdapDN denormalizeTypes( LdapDN dn ) throws NamingException
    {
        LdapDN newDn = new LdapDN();
        
        for ( int ii = 0; ii < dn.size(); ii++ )
        {
            Rdn rdn = dn.getRdn( ii );
            if ( rdn.size() == 0 )
            {
                newDn.add( new Rdn() );
                continue;
            }
            else if ( rdn.size() == 1 )
            {
            	String name = registry.lookup( rdn.getNormType() ).getName();
            	String value = (String)rdn.getAtav().getValue(); 
                newDn.add( new Rdn( name, name, value, value ) );
                continue;
            }

            // below we only process multi-valued rdns
            StringBuffer buf = new StringBuffer();
        
            for ( Iterator<AttributeTypeAndValue> atavs = rdn.iterator(); atavs.hasNext(); /**/ )
            {
                AttributeTypeAndValue atav = atavs.next();
                String type = registry.lookup( rdn.getNormType() ).getName();
                buf.append( type ).append( '=' ).append( atav.getValue() );
                
                if ( atavs.hasNext() )
                {
                    buf.append( '+' );
                }
            }
            
            newDn.add( new Rdn(buf.toString()) );
        }
        
        return newDn;
    }


    private boolean filterDenormalized( Attributes entry ) throws NamingException
    {
        denormalizeEntryOpAttrs( entry );
        return true;
    }
}