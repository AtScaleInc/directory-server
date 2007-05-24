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

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * The Partition Container holds entries which can be either Partitions or 
 * Containers. 
 * 
 * We can see them as directories, where Partitions are the files.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class PartitionContainer extends AbstractPartitionStructure
{
    /** Stores the list of all the descendant partitions and containers */
    private Map<String, PartitionStructure> children;
    
    /**
     * Creates a new instance of PartitionContainer.
     */
    public PartitionContainer()
    {
        children = new HashMap<String, PartitionStructure>();
    }

    /**
     * @see PartitionStructure#isPartition()
     */
    public boolean isPartition()
    {
        return false;
    }
    
    /**
     * @see PartitionStructure#addPartitionHandler( String, PartitionStructure )
     */
    public PartitionStructure addPartitionHandler( String name, PartitionStructure child )
    {
        children.put( name, child );
        return this;
    }
    
    /**
     * @see PartitionStructure#contains( String )     
     */
    public boolean contains( String name )
    {
        return children.containsKey( name );
    }
    
    /**
     * @see PartitionStructure#getPartition()
     * 
     * As this is a container, we just return null;
     */
    public Partition getPartition()
    {
        return null;
    }

    /**
     * @see PartitionStructure#getPartition( String )
     */
    public PartitionStructure getPartition( String name )
    {
        if ( children.containsKey( name ) )
        {
            return children.get( name );
        }
        else
        {
            return null;
        }
    }
    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append( " { " );
        
        boolean isFirst = true;
        
        for ( PartitionStructure child:children.values() )
        {
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                sb.append(  ", " );
            }

            if ( child instanceof PartitionContainer )
            {
                sb.append( "C:").append( child.toString() );
            }
            else
            {
                sb.append( "P: " ).append( "'" ).append( child.toString() ).append( "'" );
            }
        }

        sb.append( " } " );
        
        return sb.toString();
    }
}
