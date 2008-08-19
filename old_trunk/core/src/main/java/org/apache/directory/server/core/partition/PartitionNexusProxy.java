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
package org.apache.directory.server.core.partition;


import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.SearchControls;
import javax.naming.event.EventContext;
import javax.naming.event.NamingListener;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.changelog.ChangeLogInterceptor;
import org.apache.directory.server.core.collective.CollectiveAttributeInterceptor;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.exception.ExceptionInterceptor;
import org.apache.directory.server.core.interceptor.InterceptorChain;
import org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetMatchedNameOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.GetSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RemoveContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.referral.ReferralInterceptor;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapSizeLimitExceededException;
import org.apache.directory.shared.ldap.exception.LdapTimeLimitExceededException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.name.LdapDN;

/**
 * A decorator that wraps other {@link PartitionNexus} to enable
 * {@link InterceptorChain} and {@link InvocationStack} support.
 * All {@link Invocation}s made to this nexus is automatically pushed to
 * {@link InvocationStack} of the current thread, and popped when
 * the operation ends.  All invocations are filtered by {@link InterceptorChain}.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class PartitionNexusProxy extends PartitionNexus
{
    /**
     * safe to use set of bypass instructions to lookup raw entries
     */
    public static final Collection<String> LOOKUP_BYPASS;

    /**
     * safe to use set of bypass instructions to getMatchedDn
     */
    public static final Collection<String> GETMATCHEDDN_BYPASS;

    /**
     * safe to use set of bypass instructions to lookup raw entries excluding operational attributes
     */
    public static final Collection<String> LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS;
    
    public static final Collection<String> GET_ROOT_DSE_BYPASS;

    /**
     * Bypass String to use when ALL interceptors should be skipped
     */
    public static final String BYPASS_ALL = "*";

    /**
     * Bypass String to use when ALL interceptors should be skipped
     */
    public static final Collection<String> BYPASS_ALL_COLLECTION = Collections.singleton( BYPASS_ALL );

    /**
     * A static object to store the rootDSE entry with all the attributes
     */
    private static ServerEntry ROOT_DSE_ALL;

    /**
     * A static object to store the rootDSE entry without operationnal attributes
     */
    private static ServerEntry ROOT_DSE_NO_OPERATIONNAL;

    /**
     * A mutex to protect the rootDSE construction
     */
    private static final Object ROOT_DSE_ALL_MUTEX = new Object();

    /**
     * A mutex to protect the rootDSE construction
     */
    private static final Object ROOT_DSE_NOOP_MUTEX = new Object();

    private final Context caller;
    private final DirectoryService service;

    static
    {
        Collection<String> c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
//        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
//        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
//        c.add( TriggerInterceptor.class.getName() );
        LOOKUP_BYPASS = Collections.unmodifiableCollection( c );

        c = new HashSet<String>();
//        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
//        c.add( ExceptionInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
//        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
//        c.add( TriggerInterceptor.class.getName() );
        GETMATCHEDDN_BYPASS = Collections.unmodifiableCollection( c );

        c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
//        c.add( ExceptionInterceptor.class.getName() );
//        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
//        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS = Collections.unmodifiableCollection( c );
        
        
        c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        //c.add( AuthenticationInterceptor.class.getName() );
        c.add( ChangeLogInterceptor.class.getName() );
        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        GET_ROOT_DSE_BYPASS = Collections.unmodifiableCollection( c );

    }


    /**
     * Creates a new instance.
     *
     * @param caller  a JNDI {@link Context} object that will call this proxy
     * @param service a JNDI service
     */
    public PartitionNexusProxy( Context caller, DirectoryService service ) throws NamingException
    {
        this.caller = caller;
        this.service = service;
    }


    public LdapContext getLdapContext()
    {
        return service.getPartitionNexus().getLdapContext();
    }


    public String getId()
    {
        throw new UnsupportedOperationException( "Nexus partition proxy objects do not have an Id." );
    }


    public void setId( String id )
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public ServerEntry getContextEntry()
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public void setContextEntry( ServerEntry contextEntry )
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public String getSuffix()
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public void setSuffix( String suffix )
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public void setCacheSize( int cacheSize )
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public int getCacheSize()
    {
        throw new UnsupportedOperationException( "Not supported by PartitionNexusProxy" );
    }


    public void init( DirectoryService core ) throws NamingException
    {
    }


    public void destroy()
    {
    }


    public Partition getSystemPartition()
    {
        return service.getPartitionNexus().getSystemPartition();
    }


    public Partition getPartition( LdapDN dn ) throws NamingException
    {
        return service.getPartitionNexus().getPartition( dn );
    }


    public LdapDN getSuffixDn() throws NamingException
    {
        return service.getPartitionNexus().getSuffixDn();
    }

    public LdapDN getUpSuffixDn() throws NamingException
    {
        return service.getPartitionNexus().getUpSuffixDn();
    }


    public void sync() throws NamingException
    {
        this.service.sync();
    }


    public void close() throws NamingException
    {
        this.service.shutdown();
    }


    public boolean isInitialized()
    {
        return this.service.isStarted();
    }


    public LdapDN getMatchedName( GetMatchedNameOperationContext opContext ) throws NamingException
    {
        return getMatchedName( opContext, null );
    }


    public LdapDN getMatchedName( GetMatchedNameOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "getMatchedName", bypass ) );
        
        try
        {
            return service.getInterceptorChain().getMatchedName( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public LdapDN getSuffix( GetSuffixOperationContext opContext ) throws NamingException
    {
        return getSuffix( opContext, null );
    }


    public LdapDN getSuffix( GetSuffixOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "getSuffixDn", bypass ) );
        
        try
        {
            return service.getInterceptorChain().getSuffix( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public Iterator<String> listSuffixes( ListSuffixOperationContext opContext ) throws NamingException
    {
        return listSuffixes( opContext, null );
    }


    public Iterator<String> listSuffixes( ListSuffixOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "listSuffices", bypass ) );
        
        try
        {
            return service.getInterceptorChain().listSuffixes( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public boolean compare( CompareOperationContext opContext ) throws NamingException
    {
        return compare( opContext, null );
    }


    public boolean compare( CompareOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "compare", bypass ) );
        
        try
        {
            return service.getInterceptorChain().compare( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void delete( DeleteOperationContext opContext ) throws NamingException
    {
        delete( opContext, null );
    }


    public void delete( DeleteOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "delete", bypass ) );
        
        try
        {
            service.getInterceptorChain().delete( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void add( AddOperationContext opContext ) throws NamingException
    {
        add( opContext, null );
    }


    public void add( AddOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "add", bypass ) );
        
        try
        {
            service.getInterceptorChain().add( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void modify( ModifyOperationContext opContext ) throws NamingException
    {
        modify( opContext, null );
    }


    public void modify( ModifyOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "modify", bypass ) );
        
        try
        {
            service.getInterceptorChain().modify( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public NamingEnumeration<ServerSearchResult> list( ListOperationContext opContext ) throws NamingException
    {
        return list( opContext, null );
    }


    public NamingEnumeration<ServerSearchResult> list( ListOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "list", bypass ) );
        
        try
        {
            return service.getInterceptorChain().list( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public NamingEnumeration<ServerSearchResult> search( SearchOperationContext opContext )
            throws NamingException
    {
        NamingEnumeration<ServerSearchResult> ne = search( opContext, null );

        if ( ne instanceof SearchResultFilteringEnumeration )
        {
            SearchResultFilteringEnumeration results = ( SearchResultFilteringEnumeration ) ne;
            SearchControls searchCtls = opContext.getSearchControls();

            if ( searchCtls.getTimeLimit() + searchCtls.getCountLimit() > 0 )
            {
                // this will be the last filter added so other filters before it must
                // have passed/approved of the entry to be returned back to the client
                // so the candidate we have is going to be returned for sure
                results.addResultFilter( new SearchResultFilter()
                {
                    final long startTime = System.currentTimeMillis();
                    int count = 1; // with prefetch we've missed one which is ok since 1 is the minimum


                    public boolean accept( Invocation invocation, ServerSearchResult result, SearchControls controls )
                            throws NamingException
                    {
                        if ( controls.getTimeLimit() > 0 )
                        {
                            long runtime = System.currentTimeMillis() - startTime;
                            if ( runtime > controls.getTimeLimit() )
                            {
                                throw new LdapTimeLimitExceededException();
                            }
                        }

                        if ( controls.getCountLimit() > 0 )
                        {
                            if ( count > controls.getCountLimit() )
                            {
                                throw new LdapSizeLimitExceededException();
                            }
                        }

                        count++;
                        return true;
                    }
                } );
            }
        }

        return ne;
    }


    public NamingEnumeration<ServerSearchResult> search( SearchOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "search", bypass ) );
        
        try
        {
            return service.getInterceptorChain().search( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public ServerEntry lookup( LookupOperationContext opContext ) throws NamingException
    {
        if ( opContext.getDn().size() == 0 )
        {
            List<String> attrs = opContext.getAttrsId();

            if ( ( attrs == null ) || ( attrs.size() == 0 ) )
            {
                synchronized ( ROOT_DSE_NOOP_MUTEX )
                {
                    if ( ROOT_DSE_NO_OPERATIONNAL == null )
                    {
                        ROOT_DSE_NO_OPERATIONNAL = lookup( opContext, null );
                    }
                }

                return ROOT_DSE_NO_OPERATIONNAL;
            } 
            else if ( ( attrs.size() == 1 ) && ( attrs.contains( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) ) )
            {
                synchronized ( ROOT_DSE_ALL_MUTEX )
                {
                    if ( ROOT_DSE_ALL == null )
                    {
                        ROOT_DSE_ALL = lookup( opContext, null );
                    }
                }

                return ROOT_DSE_ALL;
            }

        }

        return lookup( opContext, null );
    }


    public ServerEntry lookup( LookupOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "lookup", bypass ) );
        
        try
        {
            return service.getInterceptorChain().lookup( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }

    public boolean hasEntry( EntryOperationContext opContext ) throws NamingException
    {
        return hasEntry( opContext, null );
    }


    public boolean hasEntry( EntryOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "hasEntry", bypass ) );
        
        try
        {
            return service.getInterceptorChain().hasEntry( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void rename( RenameOperationContext opContext ) throws NamingException
    {
        rename( opContext, null );
    }


    public void rename( RenameOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "rename", bypass ) );
        
        try
        {
            service.getInterceptorChain().rename( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void move( MoveOperationContext opContext ) throws NamingException
    {
        move( opContext, null );
    }


    public void move( MoveOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "move", bypass ) );
        
        try
        {
            service.getInterceptorChain().move( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void moveAndRename( MoveAndRenameOperationContext opContext ) throws NamingException
    {
        moveAndRename( opContext, null );
    }


    public void moveAndRename( MoveAndRenameOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "moveAndRename", bypass ) );
        
        try
        {
            service.getInterceptorChain().moveAndRename( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }

    /**
     * TODO : check if we can find another way to procect ourselves from recursion.
     *
     * @param opContext The operation context
     * @param bypass bypass instructions to skip interceptors
     * @throws NamingException if bind fails
     */
    public void bind( BindOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "bind", bypass ) );
        
        try
        {
            service.getInterceptorChain().bind( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void unbind( UnbindOperationContext opContext, Collection<String> bypass ) throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "unbind", bypass ) );
        
        try
        {
            service.getInterceptorChain().unbind( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void bind( BindOperationContext opContext ) throws NamingException
    {
        bind( opContext, null );
    }


    public void unbind( UnbindOperationContext opContext ) throws NamingException
    {
        unbind( opContext, null );
    }


    public ServerEntry getRootDSE( GetRootDSEOperationContext opContext ) throws NamingException
    {
        if ( opContext.getDn().size() == 0 )
        {
            synchronized ( ROOT_DSE_ALL_MUTEX )
            {
                if ( ROOT_DSE_ALL == null )
                {
                    ROOT_DSE_ALL = getRootDSE( opContext, null );
                }
            }

            return ROOT_DSE_ALL;
        }

        return getRootDSE( opContext, null );
    }


    public ServerEntry getRootDSE( GetRootDSEOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "getRootDSE", GET_ROOT_DSE_BYPASS ) );
        
        try
        {
            return service.getInterceptorChain().getRootDSE( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void addContextPartition( AddContextPartitionOperationContext opContext ) throws NamingException
    {
        addContextPartition( opContext, null );
    }


    public void addContextPartition( AddContextPartitionOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "addContextPartition", bypass ) );
        
        try
        {
            service.getInterceptorChain().addContextPartition( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    public void removeContextPartition( RemoveContextPartitionOperationContext opContext ) throws NamingException
    {
        removeContextPartition( opContext, null );
    }


    public void removeContextPartition( RemoveContextPartitionOperationContext opContext, Collection<String> bypass )
            throws NamingException
    {
        ensureStarted();
        opContext.push( new Invocation( this, caller, "removeContextPartition", bypass ) );
        
        try
        {
            service.getInterceptorChain().removeContextPartition( opContext );
        }
        finally
        {
            opContext.pop();
        }
    }


    private void ensureStarted() throws ServiceUnavailableException
    {
        if ( !service.isStarted() )
        {
            throw new ServiceUnavailableException( "Directory service is not started." );
        }
    }


    public void registerSupportedExtensions( Set<String> extensionOids ) throws NamingException
    {
        service.getPartitionNexus().registerSupportedExtensions( extensionOids );
    }


    public void registerSupportedSaslMechanisms( Set<String> supportedSaslMechanisms ) throws NamingException
    {
        service.getPartitionNexus().registerSupportedSaslMechanisms( supportedSaslMechanisms );
    }


    // -----------------------------------------------------------------------
    // EventContext and EventDirContext notification methods
    // -----------------------------------------------------------------------

    /*
     * All listener registration/deregistration methods can be reduced down to
     * the following methods.  Rather then make these actual intercepted methods
     * we use them as out of band methods to interface with the notification
     * interceptor.
     */

    public void addNamingListener( EventContext ctx, LdapDN name, ExprNode filter, SearchControls searchControls,
            NamingListener namingListener ) throws NamingException
    {
        InterceptorChain chain = service.getInterceptorChain();
        EventInterceptor interceptor = ( EventInterceptor ) chain.get( EventInterceptor.class.getName() );
        interceptor.addNamingListener( ctx, name, filter, searchControls, namingListener );
    }


    public void removeNamingListener( EventContext ctx, NamingListener namingListener ) throws NamingException
    {
        InterceptorChain chain = service.getInterceptorChain();
        if ( chain == null )
        {
            return;
        }
        EventInterceptor interceptor = ( EventInterceptor ) chain.get( EventInterceptor.class.getName() );
        interceptor.removeNamingListener( ctx, namingListener );
    }
}
