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
package org.apache.directory.server.schema.registries;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.syntax.ComparatorDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple POJO implementation of the ComparatorRegistry service interface.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultComparatorRegistry implements ComparatorRegistry
{
    /** static class logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultComparatorRegistry.class );
    
    /** the comparators in this registry */
    private final Map<String,Comparator> byOid;
    
    /** maps oids to a comparator description */
    private final Map<String,ComparatorDescription> oidToDescription;

    /** A speedup for debug */
    private static final boolean DEBUG = LOG.isDebugEnabled();

    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------


    /**
     * Creates a DefaultComparatorRegistry by initializing the map and the
     * montior.
     */
    public DefaultComparatorRegistry()
    {
        this.byOid = new HashMap<String, Comparator>();
        this.oidToDescription = new HashMap<String,ComparatorDescription>();
    }


    // ------------------------------------------------------------------------
    // Service Methods
    // ------------------------------------------------------------------------

    
    public void register( ComparatorDescription description, Comparator comparator ) throws NamingException
    {
        if ( byOid.containsKey( description.getNumericOid() ) )
        {
            throw new NamingException( "Comparator with OID " + description.getNumericOid() 
                + " already registered!" );
        }

        oidToDescription.put( description.getNumericOid(), description );
        byOid.put( description.getNumericOid(), comparator );
        
        if ( DEBUG )
        {
            LOG.debug( "registed comparator with OID: " + description.getNumericOid() );
        }
    }

    
    private static String getSchema( ComparatorDescription desc )
    {
        List values = desc.getExtensions().get( "X-SCHEMA" );
        
        if ( values == null || values.size() == 0 )
        {
            return "other";
        }
        
        return desc.getExtensions().get( "X-SCHEMA" ).get( 0 );
    }
    

    public Comparator lookup( String oid ) throws NamingException
    {
        if ( byOid.containsKey( oid ) )
        {
            Comparator c = byOid.get( oid );
            
            if ( DEBUG )
            {
                LOG.debug( "looked up comparator with OID: " + oid );
            }
            
            return c;
        }

        throw new NamingException( "Comparator not found for OID: " + oid );
    }


    public boolean hasComparator( String oid )
    {
        return byOid.containsKey( oid );
    }


    public String getSchemaName( String oid ) throws NamingException
    {
        if ( ! Character.isDigit( oid.charAt( 0 ) ) )
        {
            throw new NamingException( "OID " + oid + " is not a numeric OID" );
        }

        if ( oidToDescription.containsKey( oid ) )
        {
            return getSchema( oidToDescription.get( oid ) );
        }

        throw new NamingException( "OID " + oid + " not found in oid to " + "description map!" );
    }


    public Iterator<String> oidIterator()
    {
        return byOid.keySet().iterator();
    }


    public void unregister( String oid ) throws NamingException
    {
        if ( ! Character.isDigit( oid.charAt( 0 ) ) )
        {
            throw new NamingException( "OID " + oid + " is not a numeric OID" );
        }

        this.byOid.remove( oid );
        this.oidToDescription.remove( oid );
    }
    
    
    public void unregisterSchemaElements( String schemaName )
    {
        List<String> oids = new ArrayList<String>( byOid.keySet() );
        for ( String oid : oids )
        {
            ComparatorDescription description = oidToDescription.get( oid );
            String schemaNameForOid = getSchema( description );
            if ( schemaNameForOid.equalsIgnoreCase( schemaName ) )
            {
                byOid.remove( oid );
                oidToDescription.remove( oid );
            }
        }
    }


    public void renameSchema( String originalSchemaName, String newSchemaName )
    {
        List<String> oids = new ArrayList<String>( byOid.keySet() );
        for ( String oid : oids )
        {
            ComparatorDescription description = oidToDescription.get( oid );
            String schemaNameForOid = getSchema( description );
            if ( schemaNameForOid.equalsIgnoreCase( originalSchemaName ) )
            {
                List<String> schemaExt = description.getExtensions().get( "X-SCHEMA" );
                schemaExt.clear();
                schemaExt.add( newSchemaName );
            }
        }
    }


    public Iterator<ComparatorDescription> comparatorDescriptionIterator()
    {
        return oidToDescription.values().iterator();
    }
}
