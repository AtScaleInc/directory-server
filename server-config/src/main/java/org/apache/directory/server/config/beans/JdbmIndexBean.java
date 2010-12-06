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
package org.apache.directory.server.config.beans;

import org.apache.directory.server.config.AttributeType;


/**
 * A class used to store the JdbmIndex configuration.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class JdbmIndexBean<K, E> extends IndexBean
{
    /** The default cache size */
    private static final int DEFAULT_INDEX_CACHE_SIZE = 100;
    
    /** default duplicate limit before duplicate keys switch to using a btree for values */
    private static final int DEFAULT_DUPLICATE_LIMIT = 512;

    /** the size (number of index entries) for the cache */
    private int indexCacheSize = DEFAULT_INDEX_CACHE_SIZE;

    /** duplicate limit before duplicate keys switch to using a btree for values */
    private int indexNumDupLimit = DEFAULT_DUPLICATE_LIMIT;
    
    /** The index file name */
    @AttributeType("ads-indexFileName")
    private String indexFileName;
    
    /** The index working directory */
    @AttributeType("ads-indexWorkingDir")
    private String indexWorkingDir;

    /**
     * Create a new JdbmIndexBean instance
     */
    public JdbmIndexBean()
    {
    }


    /**
     * Gets the size of the index cache in terms of the number of index entries to be cached.
     *
     * @return the size of the index cache
     */
    public int getIndexCacheSize()
    {
        return indexCacheSize;
    }


    /**
     * Sets the size of the index cache in terms of the number of index entries to be cached.
     *
     * @param IndexCacheSize the size of the index cache
     */
    public void setIndexCacheSize( int indexCacheSize )
    {
        this.indexCacheSize = indexCacheSize;
    }


    /**
     * Gets the threshold at which point duplicate keys use btree indirection to store
     * their values.
     *
     * @return the threshold for storing a keys values in another btree
     */
    public int getIndexNumDupLimit()
    {
        return indexNumDupLimit;
    }


    /**
     * Sets the threshold at which point duplicate keys use btree indirection to store
     * their values.
     *
     * @param indexNumDupLimit the threshold for storing a keys values in another btree
     */
    public void setIndexNumDupLimit( int indexNumDupLimit )
    {
        this.indexNumDupLimit = indexNumDupLimit;
    }


    /**
     * @return the indexFileName
     */
    public String getIndexFileName()
    {
        return indexFileName;
    }


    /**
     * @param indexFileName the indexFileName to set
     */
    public void setIndexFileName( String indexFileName )
    {
        this.indexFileName = indexFileName;
    }


    /**
     * @return the indexWorkingDir
     */
    public String getIndexWorkingDir()
    {
        return indexWorkingDir;
    }


    /**
     * @param indexWorkingDir the indexWorkingDir to set
     */
    public void setIndexWorkingDir( String indexWorkingDir )
    {
        this.indexWorkingDir = indexWorkingDir;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public String toString( String tabs )
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append( tabs ).append( "JdbmIndexBean :\n" );
        sb.append( super.toString( tabs ) );
        sb.append( toString( tabs, "  index file name", indexFileName ) );
        sb.append( toString( tabs, "  index working directory", indexWorkingDir ) );
        sb.append( toString( tabs, "  index cache size", indexCacheSize ) );
        sb.append( toString( tabs, "  index num dup limit", indexNumDupLimit ) );
        
        return sb.toString();
    }
    
    
    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return toString( "" );
    }
}
