/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.jndi.ibs;


import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;

import org.apache.ldap.common.schema.AttributeType;
import org.apache.ldap.common.schema.UsageEnum;
import org.apache.ldap.common.util.DateUtils;
import org.apache.ldap.server.RootNexus;
import org.apache.ldap.server.db.SearchResultFilter;
import org.apache.ldap.server.jndi.BaseInterceptor;
import org.apache.ldap.server.jndi.Invocation;
import org.apache.ldap.server.jndi.InvocationStateEnum;
import org.apache.ldap.server.schema.AttributeTypeRegistry;
import org.apache.ldap.server.schema.GlobalRegistries;


/**
 * An interceptor based service which manages the creation and modification of
 * operational attributes as operations are performed.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class OperationalAttributeService extends BaseInterceptor
{
    /** the database search result filter to register with filter service */
    private final SearchResultFilter SEARCH_FILTER = new SearchResultFilter()
    {
        public boolean accept( LdapContext ctx, SearchResult result, SearchControls controls )
            throws NamingException
        {
            if ( controls.getReturningAttributes() == null )
            {
                return filter( result.getAttributes() );
            }

            return true;
        }
    };
    /** the lookup filter to register with filter service */
    private final LookupFilter LOOKUP_FILTER = new LookupFilter()
    {
        public void filter( LdapContext ctx, Name dn, Attributes entry )
                throws NamingException
        {
            OperationalAttributeService.this.filter( entry );
        }

        public void filter( LdapContext ctx, Name dn, Attributes entry, String[] ids )
                throws NamingException
        {
            // still need to protect against returning op attrs when ids is null
            if ( ids == null )
            {
                OperationalAttributeService.this.filter( entry );
                return;
            }

            // do nothing past here since this explicity specifies which
            // attributes to include - backends will automatically populate
            // with right set of attributes using ids array
        }
    };

    /** the root nexus of the system */
    private final RootNexus nexus;
    /** a service used to filter search and lookup operations */
    private final FilterService filteringService;
    private final AttributeTypeRegistry registry;


    /**
     * Creates the operational attribute management service interceptor.
     *
     * @param nexus the root nexus of the system
     * @param globalRegistries the global schema object registries
     */
    public OperationalAttributeService( RootNexus nexus,
                                        GlobalRegistries globalRegistries,
                                        FilterService filteringService )
    {
        this.nexus = nexus;
        if ( this.nexus == null )
        {
            throw new NullPointerException( "the nexus cannot be null" );
        }

        if ( globalRegistries == null )
        {
            throw new NullPointerException( "the global registries cannot be null" );
        }
        this.registry = globalRegistries.getAttributeTypeRegistry();

        this.filteringService = filteringService;
        if ( this.filteringService == null )
        {
            throw new NullPointerException( "the filter service cannot be null" );
        }

        this.filteringService.addLookupFilter( LOOKUP_FILTER );
        this.filteringService.addSearchResultFilter( SEARCH_FILTER );
    }


    /**
     * Adds extra operational attributes to the entry before it is added.
     *
     * @see BaseInterceptor#add(String, Name, Attributes)
     */
    protected void add( String upName, Name normName, Attributes entry ) throws NamingException
    {
        Invocation invocation = getInvocation();

        if ( invocation.getState() == InvocationStateEnum.PREINVOCATION )
        {
            String principal = getPrincipal( invocation ).getName();

            BasicAttribute attribute = new BasicAttribute( "creatorsName" );
            attribute.add( principal );
            entry.put( attribute );

            attribute = new BasicAttribute( "createTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            entry.put( attribute );
        }
    }


    protected void modify( Name dn, int modOp, Attributes mods ) throws NamingException
    {
        Invocation invocation = getInvocation();

        // add operational attributes after call in case the operation fails
        if ( invocation.getState() == InvocationStateEnum.POSTINVOCATION )
        {
            Attributes attributes = new BasicAttributes();
            BasicAttribute attribute = new BasicAttribute( "modifiersName" );
            attribute.add( getPrincipal( invocation ).getName() );
            attributes.put( attribute );

            attribute = new BasicAttribute( "modifyTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            attributes.put( attribute );

            nexus.modify( dn, DirContext.REPLACE_ATTRIBUTE, attributes );
        }
    }


    protected void modify( Name dn, ModificationItem[] mods ) throws NamingException
    {
        super.modify( dn, mods );

        Invocation invocation = getInvocation();

        // add operational attributes after call in case the operation fails
        if ( invocation.getState() == InvocationStateEnum.POSTINVOCATION )
        {
            Attributes attributes = new BasicAttributes();
            BasicAttribute attribute = new BasicAttribute( "modifiersName" );
            attribute.add( getPrincipal( invocation ).getName() );
            attributes.put( attribute );

            attribute = new BasicAttribute( "modifyTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            attributes.put( attribute );

            nexus.modify( dn, DirContext.REPLACE_ATTRIBUTE, attributes );
        }
    }


    protected void modifyRdn( Name dn, String newRdn, boolean deleteOldRdn ) throws NamingException
    {
        Invocation invocation = getInvocation();

        // add operational attributes after call in case the operation fails
        if ( invocation.getState() == InvocationStateEnum.POSTINVOCATION )
        {
            Attributes attributes = new BasicAttributes();
            BasicAttribute attribute = new BasicAttribute( "modifiersName" );
            attribute.add( getPrincipal( invocation ).getName() );
            attributes.put( attribute );

            attribute = new BasicAttribute( "modifyTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            attributes.put( attribute );

            Name newDn = dn.getSuffix( 1 ).add( newRdn );
            nexus.modify( newDn, DirContext.REPLACE_ATTRIBUTE, attributes );
        }
    }


    protected void move( Name oriChildName, Name newParentName ) throws NamingException
    {
        Invocation invocation = getInvocation();

        // add operational attributes after call in case the operation fails
        if ( invocation.getState() == InvocationStateEnum.POSTINVOCATION )
        {
            Attributes attributes = new BasicAttributes();
            BasicAttribute attribute = new BasicAttribute( "modifiersName" );
            attribute.add( getPrincipal( invocation ).getName() );
            attributes.put( attribute );

            attribute = new BasicAttribute( "modifyTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            attributes.put( attribute );

            nexus.modify( newParentName, DirContext.REPLACE_ATTRIBUTE, attributes );
        }
    }


    protected void move( Name oriChildName, Name newParentName, String newRdn,
                         boolean deleteOldRdn ) throws NamingException
    {
        Invocation invocation = getInvocation();

        // add operational attributes after call in case the operation fails
        if ( invocation.getState() == InvocationStateEnum.POSTINVOCATION )
        {
            Attributes attributes = new BasicAttributes();
            BasicAttribute attribute = new BasicAttribute( "modifiersName" );
            attribute.add( getPrincipal( invocation ).getName() );
            attributes.put( attribute );

            attribute = new BasicAttribute( "modifyTimestamp" );
            attribute.add( DateUtils.getGeneralizedTime() );
            attributes.put( attribute );

            nexus.modify( newParentName, DirContext.REPLACE_ATTRIBUTE, attributes );
        }
    }


    /**
     * Filters out the operational attributes within a search results
     * attributes.  The attributes are directly modified.
     *
     * @param attributes the resultant attributes to filter
     * @return true always
     */
    private boolean filter( Attributes attributes ) throws NamingException
    {
        NamingEnumeration list = attributes.getIDs();

        while ( list.hasMore() )
        {
            String attrId = ( String ) list.next();
            AttributeType type = registry.lookup( attrId );

            if ( type.getUsage() != UsageEnum.USERAPPLICATIONS )
            {
                attributes.remove( attrId );
            }
        }
        return true;
    }
}
