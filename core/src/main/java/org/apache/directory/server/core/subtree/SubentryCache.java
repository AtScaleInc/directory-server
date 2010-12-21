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
package org.apache.directory.server.core.subtree;


import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.directory.server.core.administrative.Subentry;
import org.apache.directory.shared.ldap.name.DN;


/**
 * A cache for subtree specifications. It associates a Subentry with a DN,
 * representing its position in the DIT.<br>
 * This cache has a size limit set to 1000 at the moment. We should add a configuration
 * parameter to manage its size.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SubentryCache implements Iterable<DN>
{
    /** The default cache size limit */
    private static final int DEFAULT_CACHE_MAX_SIZE = 1000;
    
    /** The cache size limit */
    private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
    
    /** The current cache size */
    private AtomicInteger cacheSize;
    
    /** The Subentry cache */
    private final Map<DN, Subentry> cache;
    
    /**
     * Creates a new instance of SubentryCache with a default maximum size.
     */
    public SubentryCache()
    {
        cache = new ConcurrentHashMap<DN, Subentry>();
        cacheSize = new AtomicInteger( 0 );
    }
    
    
    /**
     * Creates a new instance of SubentryCache with a specific maximum size.
     */
    public SubentryCache( int maxSize )
    {
        cache = new ConcurrentHashMap<DN, Subentry>();
        cacheSize = new AtomicInteger( 0 );
        cacheMaxSize = maxSize;
    }
    
    
    /**
     * Retrieve a Subentry given a DN. If there is none, null will be returned.
     *
     * @param dn The DN we want to get the Subentry for 
     * @return The found Subentry, or null
     */
    final Subentry getSubentry( DN dn )
    {
        return cache.get( dn );
    }
    
    
    /**
     * Remove a Subentry for a given DN 
     *
     * @param dn The DN for which we want to remove the 
     * associated Subentry
     * @return The removed Subentry, if any
     */
    final Subentry removeSubentry( DN dn )
    {
        Subentry oldSubentry = cache.remove( dn );
        
        if ( oldSubentry != null )
        {
            cacheSize.decrementAndGet();
        }
        
        return oldSubentry;
    }
    
    
    /**
     * Stores a new Subentry into the cache, associated with a DN
     *
     * @param dn The Subentry DN
     * @param ss The SubtreeSpecification
     * @param adminRoles The administrative roles for this Subentry
     * @return The old Subentry, if any
     */
    /* No qualifier */ Subentry addSubentry( DN dn, Subentry subentry )
    {
        if ( cacheSize.get() > cacheMaxSize )
        {
            // TODO : Throw an exception here
        }
        
        Subentry oldSubentry = cache.put( dn, subentry );
        
        if ( oldSubentry == null )
        {
            cacheSize.getAndIncrement();
        }
        
        return oldSubentry;
    }
    
    
    /**
     * Tells if there is a Subentry associated with a DN
     * @param dn The DN
     * @return True if a Subentry is found
     */
    /* No qualifier */ boolean hasSubentry( DN dn )
    {
        return cache.containsKey( dn );
    }
    
    
    /**
     * @return An Iterator over the Subentry's DNs 
     */
    public Iterator<DN> iterator()
    {
        return cache.keySet().iterator();
    }
    
    
    /**
     * @return The number of elements in the cache
     */
    public int getCacheSize()
    {
        return cacheSize.get();
    }
}
