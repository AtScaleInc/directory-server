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


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A plain old java object implementation of an ObjectClassRegistry.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultObjectClassRegistry implements ObjectClassRegistry
{
    /** static class logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultObjectClassRegistry.class );
    
    /** Speedup for DEBUG mode */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();
    
    /** maps an OID to an ObjectClass */
    private final Map<String,ObjectClass> byOid;
    /** the registry used to resolve names to OIDs */
    private final OidRegistry oidRegistry;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates an empty DefaultObjectClassRegistry.
     *
     * @param oidRegistry used by this registry for OID to name resolution of
     * dependencies and to automatically register and unregister it's aliases and OIDs
     */
    public DefaultObjectClassRegistry( OidRegistry oidRegistry )
    {
        this.byOid = new HashMap<String,ObjectClass>();
        this.oidRegistry = oidRegistry;
    }

    
    // ------------------------------------------------------------------------
    // Service Methods
    // ------------------------------------------------------------------------

    
    public void register( ObjectClass objectClass ) throws NamingException
    {
        if ( byOid.containsKey( objectClass.getOid() ) )
        {
            throw new NamingException( "objectClass w/ OID " + objectClass.getOid()
                + " has already been registered!" );
        }

        if ( objectClass.getNamesRef() != null && objectClass.getNamesRef().length > 0 )
        {
            oidRegistry.register( objectClass.getName(), objectClass.getOid() );
        }
        else
        {
            oidRegistry.register( objectClass.getOid(), objectClass.getOid() );
        }
        
        byOid.put( objectClass.getOid(), objectClass );
        
        if ( IS_DEBUG )
        {
            LOG.debug( "registered objectClass: " + objectClass );
        }
    }


    public ObjectClass lookup( String id ) throws NamingException
    {
        if ( StringTools.isEmpty( id ) )
        {
            throw new NamingException( "name should not be empty" );
        }
        
        String oid = oidRegistry.getOid( id.toLowerCase() );

        if ( !byOid.containsKey( oid ) )
        {
            throw new NamingException( "objectClass w/ OID " + oid + " not registered!" );
        }

        ObjectClass objectClass = byOid.get( oid );
        
        if ( IS_DEBUG )
        {
            LOG.debug( "looked objectClass with OID '" + oid + "' and got back " + objectClass );
        }
        return objectClass;
    }


    public boolean hasObjectClass( String id )
    {
        if ( oidRegistry.hasOid( id ) )
        {
            try
            {
                return byOid.containsKey( oidRegistry.getOid( id ) );
            }
            catch ( NamingException e )
            {
                return false;
            }
        }

        return false;
    }


    public String getSchemaName( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );
        ObjectClass oc = byOid.get( id );
        if ( oc != null )
        {
            return oc.getSchema();
        }

        throw new NamingException( "OID " + id + " not found in oid to " + "ObjectClass map!" );
    }


    public Iterator<ObjectClass> iterator()
    {
        return byOid.values().iterator();
    }
    
    
    public void unregister( String numericOid ) throws NamingException
    {
        if ( ! Character.isDigit( numericOid.charAt( 0 ) ) )
        {
            throw new NamingException( "Looks like the arg is not a numeric OID" );
        }

        byOid.remove( numericOid );
    }
}
