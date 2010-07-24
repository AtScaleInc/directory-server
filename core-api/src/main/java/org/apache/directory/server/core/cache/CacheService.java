/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.server.core.cache;


import java.io.File;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A ehcache based cache service to be used for various caching requirement in the server. 
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class CacheService
{

    private static final Logger LOG = LoggerFactory.getLogger( CacheService.class );

    /** the ehcache configuration file */
    private File configFile;

    /** the ehcache cache manager */
    private CacheManager cacheManager;


    public CacheService()
    {
    }


    public void initialize( DirectoryService dirService )
    {
        if ( ( cacheManager != null ) && ( cacheManager.getStatus() == Status.STATUS_ALIVE ) )
        {
            LOG.warn( "cache service was already initialized and is alive" );
            return;
        }

        if ( configFile == null || !configFile.exists() )
        {
            LOG.info( "no custom cache configuration was set, loading the default cache configuration" );

            cacheManager = new CacheManager( getClass().getClassLoader().getResource( "directory-cacheservice.xml" ) );
        }
        else
        {
            LOG.info( "loading cache configuration from the file {}", configFile );

            cacheManager = new CacheManager( configFile.getAbsolutePath() );
        }
    }


    public void destroy()
    {
        if ( cacheManager.getStatus() == Status.STATUS_ALIVE )
        {
            LOG.info( "destroying the cache service" );

            cacheManager.removalAll();

            cacheManager.shutdown();
        }
    }


    public Cache getCache( String name )
    {
        LOG.info( "fetching the cache named {}", name );

        return cacheManager.getCache( name );
    }


    public void setConfigFile( File configFile )
    {
        if ( configFile == null )
        {
            throw new IllegalArgumentException( "invalid configuration file, null" );
        }

        this.configFile = configFile;
    }

}
