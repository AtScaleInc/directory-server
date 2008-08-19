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
package org.apache.directory.server.core.schema;


import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.schema.SerializableComparator;
import org.apache.directory.server.schema.bootstrap.ApacheSchema;
import org.apache.directory.server.schema.bootstrap.ApachemetaSchema;
import org.apache.directory.server.schema.bootstrap.BootstrapSchemaLoader;
import org.apache.directory.server.schema.bootstrap.CoreSchema;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.bootstrap.SystemSchema;
import org.apache.directory.server.schema.bootstrap.partition.SchemaPartitionExtractor;
import org.apache.directory.server.schema.registries.DefaultOidRegistry;
import org.apache.directory.server.schema.registries.DefaultRegistries;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Tests the partition schema loader.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class PartitionSchemaLoaderTest
{
    private static Registries registries;
    private static DirectoryService directoryService;
    private static JdbmPartition schemaPartition;


    @BeforeClass public static void setUp() throws Exception
    {
        // setup working directory
        directoryService = new DefaultDirectoryService();
        File workingDirectory = new File( System.getProperty( "workingDirectory", System.getProperty( "user.dir" ) ) );
        
        if ( ! workingDirectory.exists() )
        {
            workingDirectory.mkdirs();
        }
        
        directoryService.setWorkingDirectory( workingDirectory );
        
        // --------------------------------------------------------------------
        // Load the bootstrap schemas to start up the schema partition
        // --------------------------------------------------------------------

        // setup temporary loader and temp registry 
        BootstrapSchemaLoader loader = new BootstrapSchemaLoader();
        registries = new DefaultRegistries( "bootstrap", loader, new DefaultOidRegistry() );
        directoryService.setRegistries( registries );
        
        // load essential bootstrap schemas 
        Set<Schema> bootstrapSchemas = new HashSet<Schema>();
        bootstrapSchemas.add( new ApachemetaSchema() );
        bootstrapSchemas.add( new ApacheSchema() );
        bootstrapSchemas.add( new CoreSchema() );
        bootstrapSchemas.add( new SystemSchema() );
        loader.loadWithDependencies( bootstrapSchemas, registries );
        
        // run referential integrity tests
        List<Throwable> errors = registries.checkRefInteg();
        
        if ( !errors.isEmpty() )
        {
            NamingException e = new NamingException();
            e.setRootCause( errors.get( 0 ) );
            throw e;
        }

        SerializableComparator.setRegistry( registries.getComparatorRegistry() );

        // --------------------------------------------------------------------
        // If not present extract schema partition from jar
        // --------------------------------------------------------------------

        SchemaPartitionExtractor extractor = null; 
        try
        {
            extractor = new SchemaPartitionExtractor( directoryService.getWorkingDirectory() );
            extractor.extract();
        }
        catch ( IOException e )
        {
            NamingException ne = new NamingException( "Failed to extract pre-loaded schema partition." );
            ne.setRootCause( e );
            throw ne;
        }
        
        // --------------------------------------------------------------------
        // Initialize schema partition
        // --------------------------------------------------------------------

        schemaPartition = new JdbmPartition();
        schemaPartition.setId( "schema" );
        schemaPartition.setCacheSize( 1000 );

        Set<Index> indexedAttributes = new HashSet<Index>();
        for ( String attributeId : extractor.getDbFileListing().getIndexedAttributes() )
        {
            indexedAttributes.add( new JdbmIndex( attributeId ) );
        }

        schemaPartition.setIndexedAttributes( indexedAttributes );
        schemaPartition.setSuffix( "ou=schema" );
        
        ServerEntry entry = new DefaultServerEntry( registries, new LdapDN( "ou=schema" ) );
        entry.put( "objectClass", "top", "organizationalUnit" );
        entry.put( "ou", "schema" );
        schemaPartition.setContextEntry( entry );
        schemaPartition.init( directoryService );
    }
    
    
    @Test public void testGetSchemas() throws NamingException
    {
        PartitionSchemaLoader loader = new PartitionSchemaLoader( schemaPartition, registries );
        Map<String,Schema> schemas = loader.getSchemas();
        
        Schema schema = schemas.get( "mozilla" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "mozilla" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "core" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "core" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "apachedns" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "apachedns" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "autofs" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "autofs" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "apache" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "apache" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;

        schema = schemas.get( "cosine" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "cosine" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "krb5kdc" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "krb5kdc" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "samba" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "samba" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "collective" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "collective" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "java" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "java" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "dhcp" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "dhcp" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "corba" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "corba" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "nis" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "nis" );
        //assertTrue( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "inetorgperson" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "inetorgperson" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "system" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "system" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
        
        schema = schemas.get( "apachemeta" );
        assertNotNull( schema );
        assertEquals( schema.getSchemaName(), "apachemeta" );
        assertFalse( schema.isDisabled() );
        assertEquals( schema.getOwner(), "uid=admin,ou=system" );
        schema = null;
    }
    
    
    @Test public void testGetSchemaNames() throws NamingException
    {
        PartitionSchemaLoader loader = new PartitionSchemaLoader( schemaPartition, registries );
        Set<String> schemaNames = loader.getSchemaNames();
        assertTrue( schemaNames.contains( "mozilla" ) );
        assertTrue( schemaNames.contains( "core" ) );
        assertTrue( schemaNames.contains( "apachedns" ) );
        assertTrue( schemaNames.contains( "autofs" ) );
        assertTrue( schemaNames.contains( "apache" ) );
        assertTrue( schemaNames.contains( "cosine" ) );
        assertTrue( schemaNames.contains( "krb5kdc" ) );
        assertTrue( schemaNames.contains( "samba" ) );
        assertTrue( schemaNames.contains( "collective" ) );
        assertTrue( schemaNames.contains( "java" ) );
        assertTrue( schemaNames.contains( "dhcp" ) );
        assertTrue( schemaNames.contains( "corba" ) );
        assertTrue( schemaNames.contains( "nis" ) );
        assertTrue( schemaNames.contains( "inetorgperson" ) );
        assertTrue( schemaNames.contains( "system" ) );
        assertTrue( schemaNames.contains( "apachemeta" ) );
    }
}
