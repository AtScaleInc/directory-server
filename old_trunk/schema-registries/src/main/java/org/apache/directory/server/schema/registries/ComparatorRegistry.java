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


import java.util.Comparator;
import java.util.Iterator;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.syntax.ComparatorDescription;


/**
 * Comparator registry component's service interface.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface ComparatorRegistry
{
    /**
     * Gets the name of the schema this schema object is associated with.
     *
     * @param oid the object identifier
     * @return the schema name
     * @throws NamingException if the schema object does not exist 
     */
    String getSchemaName( String oid ) throws NamingException;


    /**
     * Registers a Comparator with this registry.
     * 
     * @param description the comparatorDescription for the comparator to register
     * @param comparator the Comparator to register
     * @throws NamingException if the Comparator is already registered or the 
     *      registration operation is not supported
     */
    void register( ComparatorDescription description, Comparator comparator ) throws NamingException;


    /**
     * Looks up a Comparator by its unique Object Identifier.
     * 
     * @param oid the object identifier
     * @return the Comparator for the oid
     * @throws NamingException if there is a backing store failure or the 
     *      Comparator does not exist.
     */
    Comparator lookup( String oid ) throws NamingException;


    /**
     * Checks to see if a Comparator exists.  Backing store failures simply 
     * return false.
     * 
     * @param oid the object identifier
     * @return true if a Comparator definition exists for the oid, false 
     *      otherwise
     */
    boolean hasComparator( String oid );


    /**
     * Iterates over the numeric OID strings of this registry.
     * 
     * @return Iterator of numeric OID strings 
     */
    Iterator<String> oidIterator();

    
    /**
     * Iterates over the numeric OID strings of this registry.
     * 
     * @return Iterator of numeric OID strings 
     */
    Iterator<ComparatorDescription> comparatorDescriptionIterator();

    
    /**
     * Removes a registered comparator from this registry.
     * 
     * @param oid the numeric oid of the comparator to remove.
     * @throws NamingException if the oid is not a numeric id
     */
    void unregister( String oid ) throws NamingException;
    
    
    /**
     * Unregisters comparators from this registry associated with a schema.
     *
     * @param schemaName the name of the schema whose comparators are removed 
     * from this registry
     */
    void unregisterSchemaElements( String schemaName );
    
    
    /**
     * Renames the schemaName associated with entities within this 
     * registry to a new schema name.
     * 
     * @param originalSchemaName the original schema name
     * @param newSchemaName the new name to give to the schema
     */
    void renameSchema( String originalSchemaName, String newSchemaName );
}
