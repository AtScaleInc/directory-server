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
package org.apache.directory.server.core.interceptor;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.naming.ConfigurationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.DirectoryServiceConfiguration;
import org.apache.directory.server.core.configuration.InterceptorConfiguration;
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
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.shared.ldap.exception.LdapConfigurationException;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manages the chain of {@link Interceptor}s.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class InterceptorChain
{
    private static final Logger log = LoggerFactory.getLogger( InterceptorChain.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = log.isDebugEnabled();

    private final Interceptor FINAL_INTERCEPTOR = new Interceptor()
    {
        private PartitionNexus nexus;


        public void init( DirectoryServiceConfiguration factoryCfg, InterceptorConfiguration cfg )
        {
            this.nexus = factoryCfg.getPartitionNexus();
        }


        public void destroy()
        {
            // unused
        }


        public boolean compare( NextInterceptor next, CompareOperationContext opContext ) throws NamingException
        {
            return nexus.compare( opContext );
        }


        public Attributes getRootDSE( NextInterceptor next, GetRootDSEOperationContext opContext ) throws NamingException
        {
            return nexus.getRootDSE( opContext );
        }


        public LdapDN getMatchedName( NextInterceptor next, GetMatchedNameOperationContext opContext ) throws NamingException
        {
            return ( LdapDN ) nexus.getMatchedName( opContext ).clone();
        }


        public LdapDN getSuffix( NextInterceptor next, GetSuffixOperationContext opContext ) throws NamingException
        {
            return ( LdapDN ) nexus.getSuffix( opContext ).clone();
        }


        public Iterator<String> listSuffixes( NextInterceptor next, ListSuffixOperationContext opContext ) throws NamingException
        {
            return nexus.listSuffixes( opContext );
        }


        public void delete( NextInterceptor next, DeleteOperationContext opContext ) throws NamingException
        {
            nexus.delete( opContext );
        }


        public void add( NextInterceptor next, AddOperationContext opContext ) throws NamingException
        {
            nexus.add( opContext );
        }


        public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws NamingException
        {
            nexus.modify( opContext );
        }


        public NamingEnumeration<SearchResult> list( NextInterceptor next, ListOperationContext opContext ) throws NamingException
        {
            return nexus.list( opContext );
        }


        public NamingEnumeration<SearchResult> search( NextInterceptor next, SearchOperationContext opContext ) throws NamingException
        {
            return nexus.search( opContext );
        }


        public Attributes lookup( NextInterceptor next, LookupOperationContext opContext ) throws NamingException
        {
            return ( Attributes ) nexus.lookup( opContext ).clone();
        }


        public boolean hasEntry( NextInterceptor next, EntryOperationContext opContext ) throws NamingException
        {
            return nexus.hasEntry( opContext );
        }


        public void rename( NextInterceptor next, RenameOperationContext opContext )
            throws NamingException
        {
            nexus.rename( opContext );
        }


        public void move( NextInterceptor next, MoveOperationContext opContext ) throws NamingException
        {
            nexus.move( opContext );
        }


        public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext opContext )
            throws NamingException
        {
            nexus.moveAndRename( opContext );
        }


        public void addContextPartition( NextInterceptor next, AddContextPartitionOperationContext opContext )
            throws NamingException
        {
            nexus.addContextPartition( opContext );
        }


        public void removeContextPartition( NextInterceptor next, RemoveContextPartitionOperationContext opContext ) throws NamingException
        {
            nexus.removeContextPartition( opContext );
        }


        public void bind( NextInterceptor next, BindOperationContext opContext )  throws NamingException
        {
            nexus.bind( opContext );
        }


        public void unbind( NextInterceptor next, UnbindOperationContext opContext ) throws NamingException
        {
            nexus.unbind( opContext );
        }
    };

    private final Map<String, Entry> name2entry = new HashMap<String, Entry>();

    private final Entry tail;

    private Entry head;

    private DirectoryServiceConfiguration factoryCfg;


    /**
     * Create a new interceptor chain.
     */
    public InterceptorChain()
    {
        tail = new Entry( "tail", null, null, FINAL_INTERCEPTOR );
        head = tail;
    }


    /**
     * Initializes and registers all interceptors according to the specified
     * {@link DirectoryServiceConfiguration}.
     */
    public synchronized void init( DirectoryServiceConfiguration factoryCfg ) throws NamingException
    {
        this.factoryCfg = factoryCfg;

        // Initialize tail first.
        FINAL_INTERCEPTOR.init( factoryCfg, null );

        // And register and initialize all interceptors
        ListIterator<InterceptorConfiguration> i = factoryCfg.getStartupConfiguration().getInterceptorConfigurations().listIterator();
        Interceptor interceptor = null;
        try
        {
            while ( i.hasNext() )
            {
                InterceptorConfiguration cfg = i.next();

                if ( IS_DEBUG )
                {
                    log.debug( "Adding interceptor " + cfg.getName() );
                }

                register( cfg );
            }
        }
        catch ( Throwable t )
        {
            // destroy if failed to initialize all interceptors.
            destroy();

            if ( t instanceof NamingException )
            {
                throw ( NamingException ) t;
            }
            else
            {
                throw new InterceptorException( interceptor, "Failed to initialize interceptor chain.", t );
            }
        }
    }


    /**
     * Deinitializes and deregisters all interceptors this chain contains.
     */
    public synchronized void destroy()
    {
        List<Entry> entries = new ArrayList<Entry>();
        Entry e = tail;
        
        do
        {
            entries.add( e );
            e = e.prevEntry;
        }
        while ( e != null );

        for ( Entry entry:entries )
        {
            if ( entry != tail )
            {
                try
                {
                    deregister( entry.getName() );
                }
                catch ( Throwable t )
                {
                    log.warn( "Failed to deregister an interceptor: " + entry.getName(), t );
                }
            }
        }
    }


    /**
     * Returns the registered interceptor with the specified name.
     * @return <tt>null</tt> if the specified name doesn't exist.
     */
    public Interceptor get( String interceptorName )
    {
        Entry e = name2entry.get( interceptorName );
        if ( e == null )
        {
            return null;
        }

        return e.interceptor;
    }


    /**
     * Returns the list of all registered interceptors.
     */
    public synchronized List<Interceptor> getAll()
    {
        List<Interceptor> result = new ArrayList<Interceptor>();
        Entry e = head;
        
        do
        {
            result.add( e.interceptor );
            e = e.nextEntry;
        }
        while ( e != tail );

        return result;
    }


    public synchronized void addFirst( InterceptorConfiguration cfg ) throws NamingException
    {
        register0( cfg, head );
    }


    public synchronized void addLast( InterceptorConfiguration cfg ) throws NamingException
    {
        register0( cfg, tail );
    }


    public synchronized void addBefore( String nextInterceptorName, InterceptorConfiguration cfg )
        throws NamingException
    {
        Entry e = name2entry.get( nextInterceptorName );
        if ( e == null )
        {
            throw new ConfigurationException( "Interceptor not found: " + nextInterceptorName );
        }
        register0( cfg, e );
    }


    public synchronized String remove( String interceptorName ) throws NamingException
    {
        return deregister( interceptorName );
    }


    public synchronized void addAfter( String prevInterceptorName, InterceptorConfiguration cfg )
        throws NamingException
    {
        Entry e = name2entry.get( prevInterceptorName );
        if ( e == null )
        {
            throw new ConfigurationException( "Interceptor not found: " + prevInterceptorName );
        }
        register0( cfg, e.nextEntry );
    }


    /**
     * Adds and initializes an interceptor with the specified configuration.
     */
    private void register( InterceptorConfiguration cfg ) throws NamingException
    {
        checkAddable( cfg );
        register0( cfg, tail );
    }


    /**
     * Removes and deinitializes the interceptor with the specified name.
     */
    private String deregister( String name ) throws ConfigurationException
    {
        Entry entry = checkOldName( name );
        Entry prevEntry = entry.prevEntry;
        Entry nextEntry = entry.nextEntry;

        if ( nextEntry == null )
        {
            // Don't deregister tail
            return null;
        }

        if ( prevEntry == null )
        {
            nextEntry.prevEntry = null;
            head = nextEntry;
        }
        else
        {
            prevEntry.nextEntry = nextEntry;
            nextEntry.prevEntry = prevEntry;
        }

        name2entry.remove( name );
        entry.interceptor.destroy();

        return entry.getName();
    }

    
    private Interceptor getInterceptorInstance( InterceptorConfiguration interceptorConfiguration ) 
        throws NamingException
    {
        Class<?> interceptorClass = null;
        Interceptor interceptor = null;
        
        // Load the interceptor class and if we cannot find it blow a config exception
        try
        {
            interceptorClass = Class.forName( interceptorConfiguration.getInterceptorClassName() );
        }
        catch( ClassNotFoundException e )
        {
            LdapConfigurationException lce = new LdapConfigurationException( "Failed to load interceptor class '" +
                interceptorConfiguration.getInterceptorClassName() + "' for interceptor named '" +
                interceptorConfiguration.getName() );
            lce.setRootCause( e );
            throw lce;
        }
        
        // Now instantiate the interceptor
        try
        {
            interceptor = ( Interceptor ) interceptorClass.newInstance();
        }
        catch ( Exception e )
        {
            LdapConfigurationException lce = 
                new LdapConfigurationException( "Failed while trying to instantiate interceptor class '" +
                interceptorConfiguration.getInterceptorClassName() + "' for interceptor named '" +
                interceptorConfiguration.getName() );
            lce.setRootCause( e );
            throw lce;
        }
        
        return interceptor;
    }
    

    private void register0( InterceptorConfiguration cfg, Entry nextEntry ) throws NamingException
    {
        String name = cfg.getName();
        Interceptor interceptor = getInterceptorInstance( cfg );
        interceptor.init( factoryCfg, cfg );

        Entry newEntry;
        if ( nextEntry == head )
        {
            newEntry = new Entry( cfg.getName(), null, head, interceptor );
            head.prevEntry = newEntry;
            head = newEntry;
        }
        else if ( head == tail )
        {
            newEntry = new Entry( cfg.getName(), null, tail, interceptor );
            tail.prevEntry = newEntry;
            head = newEntry;
        }
        else
        {
            newEntry = new Entry( cfg.getName(), nextEntry.prevEntry, nextEntry, interceptor );
            nextEntry.prevEntry.nextEntry = newEntry;
            nextEntry.prevEntry = newEntry;
        }

        name2entry.put( name, newEntry );
    }


    /**
     * Throws an exception when the specified interceptor name is not registered in this chain.
     *
     * @return An interceptor entry with the specified name.
     */
    private Entry checkOldName( String baseName ) throws ConfigurationException
    {
        Entry e = name2entry.get( baseName );

        if ( e == null )
        {
            throw new ConfigurationException( "Unknown interceptor name:" + baseName );
        }

        return e;
    }


    /**
     * Checks the specified interceptor name is already taken and throws an exception if already taken.
     */
    private void checkAddable( InterceptorConfiguration cfg ) throws ConfigurationException
    {
        if ( name2entry.containsKey( cfg.getName() ) )
        {
            throw new ConfigurationException( "Other interceptor is using name '" + cfg.getName() + "'" );
        }
    }


    /**
     * Gets the InterceptorEntry to use first with bypass information considered.
     *
     * @return the first entry to use.
     */
    private Entry getStartingEntry()
    {
        if ( InvocationStack.getInstance().isEmpty() )
        {
            return head;
        }

        Invocation invocation = InvocationStack.getInstance().peek();
        if ( !invocation.hasBypass() )
        {
            return head;
        }

        if ( invocation.isBypassed( PartitionNexusProxy.BYPASS_ALL ) )
        {
            return tail;
        }

        Entry next = head;
        while ( next != tail )
        {
            if ( invocation.isBypassed( next.getName() ) )
            {
                next = next.nextEntry;
            }
            else
            {
                return next;
            }
        }

        return tail;
    }


    public Attributes getRootDSE( GetRootDSEOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.getRootDSE( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public LdapDN getMatchedName( GetMatchedNameOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;

        try
        {
            return head.getMatchedName( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public LdapDN getSuffix( GetSuffixOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.getSuffix( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public boolean compare( CompareOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.compare( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public Iterator<String> listSuffixes( ListSuffixOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.listSuffixes( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public void addContextPartition( AddContextPartitionOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.addContextPartition( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public void removeContextPartition( RemoveContextPartitionOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.removeContextPartition( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public void delete( DeleteOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.delete( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void add( AddOperationContext opContext ) throws NamingException
    {
        Entry node = getStartingEntry();
        Interceptor head = node.interceptor;
        NextInterceptor next = node.nextInterceptor;
        
        try
        {
            head.add( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void bind( BindOperationContext opContext ) throws NamingException
    {
        Entry node = getStartingEntry();
        Interceptor head = node.interceptor;
        NextInterceptor next = node.nextInterceptor;
        
        try
        {
            head.bind( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void unbind( UnbindOperationContext opContext ) throws NamingException
    {
        Entry node = getStartingEntry();
        Interceptor head = node.interceptor;
        NextInterceptor next = node.nextInterceptor;
        
        try
        {
            head.unbind( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void modify( ModifyOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.modify( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    /*public void modify( LdapDN name, ModificationItemImpl[] mods ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.configuration.getInterceptor();
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.modify( next, name, mods );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }*/


    public NamingEnumeration<SearchResult> list( ListOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.list( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public NamingEnumeration<SearchResult> search( SearchOperationContext opContext )
        throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.search( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public Attributes lookup( LookupOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.lookup( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public boolean hasEntry( EntryOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            return head.hasEntry( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
            throw new InternalError(); // Should be unreachable
        }
    }


    public void rename( RenameOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.rename( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void move( MoveOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.move( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }


    public void moveAndRename( MoveAndRenameOperationContext opContext ) throws NamingException
    {
        Entry entry = getStartingEntry();
        Interceptor head = entry.interceptor;
        NextInterceptor next = entry.nextInterceptor;
        
        try
        {
            head.moveAndRename( next, opContext );
        }
        catch ( NamingException ne )
        {
            throw ne;
        }
        catch ( Throwable e )
        {
            throwInterceptorException( head, e );
        }
    }

    /**
     * Represents an internal entry of this chain.
     */
    private class Entry
    {
        private Entry prevEntry;

        private Entry nextEntry;

        private final String name;
        
        private final Interceptor interceptor;

        private final NextInterceptor nextInterceptor;

        
        private final String getName()
        {
            return name;
        }

        
        private Entry( String name, Entry prevEntry, Entry nextEntry, Interceptor interceptor )
        {
            this.name = name;
            
            if ( interceptor == null )
            {
                throw new NullPointerException( "interceptor" );
            }

            this.prevEntry = prevEntry;
            this.nextEntry = nextEntry;
            this.interceptor = interceptor;
            this.nextInterceptor = new NextInterceptor()
            {
                private Entry getNextEntry()
                {
                    if ( InvocationStack.getInstance().isEmpty() )
                    {
                        return Entry.this.nextEntry;
                    }

                    Invocation invocation = InvocationStack.getInstance().peek();
                    if ( !invocation.hasBypass() )
                    {
                        return Entry.this.nextEntry;
                    }

                    //  I don't think we really need this since this check is performed by the chain when
                    //  getting the interceptor head to use.
                    //
                    //                    if ( invocation.isBypassed( DirectoryPartitionNexusProxy.BYPASS_ALL ) )
                    //                    {
                    //                        return tail;
                    //                    }

                    Entry next = Entry.this.nextEntry;
                    while ( next != tail )
                    {
                        if ( invocation.isBypassed( next.getName() ) )
                        {
                            next = next.nextEntry;
                        }
                        else
                        {
                            return next;
                        }
                    }

                    return next;
                }

                public boolean compare( CompareOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.compare( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }

                public Attributes getRootDSE( GetRootDSEOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.getRootDSE( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public LdapDN getMatchedName( GetMatchedNameOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.getMatchedName( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public LdapDN getSuffix( GetSuffixOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.getSuffix( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public Iterator<String> listSuffixes( ListSuffixOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.listSuffixes( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public void delete( DeleteOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.delete( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void add( AddOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.add( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void modify( ModifyOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.modify( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }

                
                public NamingEnumeration<SearchResult> list( ListOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.list( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public NamingEnumeration<SearchResult> search( SearchOperationContext opContext )
                    throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.search( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public Attributes lookup( LookupOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.lookup( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public boolean hasEntry( EntryOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        return interceptor.hasEntry( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public void rename( RenameOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.rename( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void move( MoveOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.move( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void moveAndRename( MoveAndRenameOperationContext opContext )
                    throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.moveAndRename( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void bind( BindOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;
    
                    try
                    {
                        interceptor.bind( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void unbind( UnbindOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.unbind( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                    }
                }


                public void addContextPartition( AddContextPartitionOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.addContextPartition( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }


                public void removeContextPartition( RemoveContextPartitionOperationContext opContext ) throws NamingException
                {
                    Entry next = getNextEntry();
                    Interceptor interceptor = next.interceptor;

                    try
                    {
                        interceptor.removeContextPartition( next.nextInterceptor, opContext );
                    }
                    catch ( NamingException ne )
                    {
                        throw ne;
                    }
                    catch ( Throwable e )
                    {
                        throwInterceptorException( interceptor, e );
                        throw new InternalError(); // Should be unreachable
                    }
                }
            };
        }
    }


    private static void throwInterceptorException( Interceptor interceptor, Throwable e ) throws InterceptorException
    {
        throw new InterceptorException( interceptor, "Unexpected exception.", e );
    }
}
