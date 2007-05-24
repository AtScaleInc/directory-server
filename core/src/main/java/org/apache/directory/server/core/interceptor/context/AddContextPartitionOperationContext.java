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
package org.apache.directory.server.core.interceptor.context;

import org.apache.directory.server.core.configuration.PartitionConfiguration;

/**
 * A AddContextPartition context used for Interceptors. It contains all the informations
 * needed for the addContextPartition operation, and used by all the interceptors
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class AddContextPartitionOperationContext  extends EmptyOperationContext
{
    /** The context partition configuration */
    private PartitionConfiguration cfg;
    
    /**
     * Creates a new instance of AddContextPartitionOperationContext.
     */
    public AddContextPartitionOperationContext()
    {
    }
    
    /**
     * Creates a new instance of AddContextPartitionOperationContext.
     *
     * @param entryDn The partition configuration to add
     */
    public AddContextPartitionOperationContext( PartitionConfiguration cfg )
    {
        super();
        this.cfg = cfg;
    }
    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "AddContextPartitionOperationContext for partition context '" + cfg.getName() + "'";
    }

    /**
     * @return The partition configuration
     */
    public PartitionConfiguration getCfg()
    {
        return cfg;
    }

    /**
     * Set the partition configuration
     * 
     * @param cfg The configuration
     */
    public void setCfg( PartitionConfiguration cfg )
    {
        this.cfg = cfg;
    }
}
