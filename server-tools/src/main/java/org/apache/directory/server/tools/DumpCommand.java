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
package org.apache.directory.server.tools;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import jdbm.helper.MRU;
import jdbm.recman.BaseRecordManager;
import jdbm.recman.CacheRecordManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.cursor.Cursor;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmMasterTable;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.schema.PartitionSchemaLoader;
import org.apache.directory.server.schema.SerializableComparator;
import org.apache.directory.server.schema.bootstrap.ApacheSchema;
import org.apache.directory.server.schema.bootstrap.ApachemetaSchema;
import org.apache.directory.server.schema.bootstrap.BootstrapSchemaLoader;
import org.apache.directory.server.schema.bootstrap.CoreSchema;
import org.apache.directory.server.schema.bootstrap.Schema;
import org.apache.directory.server.schema.bootstrap.SystemSchema;
import org.apache.directory.server.schema.bootstrap.partition.DbFileListing;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.DefaultOidRegistry;
import org.apache.directory.server.schema.registries.DefaultRegistries;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.shared.ldap.MultiException;
import org.apache.directory.shared.ldap.exception.LdapConfigurationException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.ldif.LdifUtils;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.Base64;


/**
 * Simple tool used to dump the contents of a jdbm based partition.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 493916 $
 */
public class DumpCommand extends ToolCommand
{
    private Registries bootstrapRegistries = new DefaultRegistries( "bootstrap", new BootstrapSchemaLoader(),
        new DefaultOidRegistry() );
    private Set<String> exclusions = new HashSet<String>();
    private boolean includeOperational = false;


    public DumpCommand()
    {
        super( "dump" );
    }


    private Registries loadRegistries() throws Exception
    {
        // --------------------------------------------------------------------
        // Load the bootstrap schemas to start up the schema partition
        // --------------------------------------------------------------------

        // setup temporary loader and temp registry 
        BootstrapSchemaLoader loader = new BootstrapSchemaLoader();
        OidRegistry oidRegistry = new DefaultOidRegistry();
        final Registries registries = new DefaultRegistries( "bootstrap", loader, oidRegistry );

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
            MultiException e = new MultiException();
            for ( Throwable t : errors )
            {
                e.addThrowable( t );
            }
            
            throw e;
        }

        SerializableComparator.setRegistry( registries.getComparatorRegistry() );

        // --------------------------------------------------------------------
        // Initialize schema partition or bomb out if we cannot find it on disk
        // --------------------------------------------------------------------

        // If not present then we need to abort 
        File schemaDirectory = new File( getLayout().getPartitionsDirectory(), "schema" );

        if ( !schemaDirectory.exists() )
        {
            throw new LdapConfigurationException( "The following schema directory from "
                + "the installation layout could not be found:\n\t" + schemaDirectory );
        }

        JdbmPartition schemaPartition = new JdbmPartition();
        schemaPartition.setId( "schema" );
        schemaPartition.setCacheSize( 1000 );

        DbFileListing listing;
        
        try
        {
            listing = new DbFileListing();
        }
        catch ( IOException e )
        {
            throw new LdapNamingException( "Got IOException while trying to read DBFileListing: " + e.getMessage(),
                ResultCodeEnum.OTHER );
        }

        Set<Index<?,ServerEntry>> indexedAttributes = new HashSet<Index<?,ServerEntry>>();

        for ( String attributeId : listing.getIndexedAttributes() )
        {
            indexedAttributes.add( new JdbmIndex( attributeId ) );
        }

        schemaPartition.setIndexedAttributes( indexedAttributes );
        schemaPartition.setSuffix( ServerDNConstants.OU_SCHEMA_DN );

        DirectoryService directoryService = new DefaultDirectoryService();
        schemaPartition.init( directoryService );

        // --------------------------------------------------------------------
        // Initialize schema subsystem and reset registries
        // --------------------------------------------------------------------
        PartitionSchemaLoader schemaLoader = new PartitionSchemaLoader( schemaPartition, registries );
        Registries globalRegistries = new DefaultRegistries( "global", schemaLoader, oidRegistry );
        schemaLoader.loadEnabled( globalRegistries );
        SerializableComparator.setRegistry( globalRegistries.getComparatorRegistry() );
        return globalRegistries;
    }


    public void execute( CommandLine cmdline ) throws Exception
    {
        getLayout().verifyInstallation();
        bootstrapRegistries = loadRegistries();

        includeOperational = cmdline.hasOption( 'o' );
        String[] partitions = cmdline.getOptionValues( 'p' );
        String outputFile = cmdline.getOptionValue( 'f' );
        PrintWriter out = null;

        String[] excludedAttributes = cmdline.getOptionValues( 'e' );
        
        if ( excludedAttributes != null )
        {
            AttributeTypeRegistry registry = bootstrapRegistries.getAttributeTypeRegistry();
            
            for ( String attributeType:excludedAttributes)
            {
                AttributeType type = registry.lookup( attributeType );
                exclusions.add( type.getName() );
            }
        }

        if ( outputFile == null )
        {
            out = new PrintWriter( System.out );
        }
        else
        {
            out = new PrintWriter( new FileWriter( outputFile ) );
        }

        for ( String partition:partitions )
        {
            File partitionDirectory = new File( getLayout().getPartitionsDirectory(), partition );
            out.println( "\n\n" );
            dump( partitionDirectory, out );
        }
    }


    private void dump( File partitionDirectory, PrintWriter out ) throws Exception
    {
        if ( !partitionDirectory.exists() )
        {
            System.err.println( "Partition directory " + partitionDirectory + " does not exist!" );
            System.exit( 1 );
        }

        out.println( "# ========================================================================" );
        out.println( "# ApacheDS Tools Version: " + getVersion() );
        out.println( "# Partition Directory: " + partitionDirectory );
        out.println( "# ========================================================================\n\n" );

        String path = partitionDirectory.getPath() + File.separator + "master";
        BaseRecordManager base = new BaseRecordManager( path );
        base.disableTransactions();
        CacheRecordManager recMan = new CacheRecordManager( base, new MRU( 1000 ) );

        JdbmMasterTable<ServerEntry> master = new JdbmMasterTable<ServerEntry>( recMan, bootstrapRegistries );
        AttributeType attributeType = bootstrapRegistries.getAttributeTypeRegistry().lookup( "apacheUpdn" );
        JdbmIndex idIndex = new JdbmIndex();
        idIndex.setAttributeId( attributeType.getName() );
        idIndex.setWkDirPath( partitionDirectory );
        idIndex.setCacheSize( 1000 );
        idIndex.setNumDupLimit( 1000 );
        idIndex.init( attributeType, partitionDirectory );

        out.println( "#---------------------" );
        Cursor<Tuple<Long,ServerEntry>> list = master.cursor();
        StringBuffer buf = new StringBuffer();
        
        while ( list.next() )
        {
            Tuple<Long,ServerEntry> tuple = list.get();
            Long id = tuple.getKey();
            String dn = ( String ) idIndex.reverseLookup( id );
            Attributes entry = ( Attributes ) tuple.getValue();

            filterAttributes( dn, entry );

            buf.append( "# Entry: " ).append( id ).append( "\n#---------------------\n\n" );
        
            if ( !LdifUtils.isLDIFSafe( dn ) )
            {
                // If the DN isn't LdifSafe, it needs to be Base64 encoded.

                buf.append( "dn:: " ).append( new String( Base64.encode( dn.getBytes() ) ) );
            }
            else
            {
                buf.append( "dn: " ).append( dn );
            }
            
            buf.append( "\n" ).append( LdifUtils.convertToLdif( entry ) );

            if ( list.next() )
            {
                buf.append( "\n\n#---------------------\n" );
            }
            
            out.print( buf.toString() );
            out.flush();
            buf.setLength( 0 );
        }
    }


    private void filterAttributes( String dn, Attributes entry ) throws Exception
    {
        List<String> toRemove = new ArrayList<String>();
        AttributeTypeRegistry registry = bootstrapRegistries.getAttributeTypeRegistry();
        NamingEnumeration<? extends Attribute> attrs = entry.getAll();
        
        while ( attrs.hasMore() )
        {
            Attribute attr = attrs.next();
            
            if ( !registry.hasAttributeType( attr.getID() ) )
            {
                if ( !isQuietEnabled() )
                {
                    System.out
                        .println( "# Cannot properly filter unrecognized attribute " + attr.getID() + " in " + dn );
                }
                continue;
            }

            AttributeType type = registry.lookup( attr.getID() );
            boolean isOperational = type.getUsage() != UsageEnum.USER_APPLICATIONS;
            
            if ( exclusions.contains( attr.getID() ) || ( isOperational && ( !includeOperational ) ) )
            {
                toRemove.add( attr.getID() );
            }
        }
        
        for ( String id:toRemove )
        {
            entry.remove( id );
            
            if ( isDebugEnabled() )
            {
                System.out.println( "# Excluding attribute " + id + " in " + dn );
            }
        }
    }


    public Options getOptions()
    {
        Options opts = new Options();
        Option op = new Option( "f", "file", true, "file to output the dump to" );
        op.setRequired( false );
        opts.addOption( op );
        op = new Option( "p", "partitions", true, "the partitions to dump" );
        op.setRequired( true );
        op.setValueSeparator( File.pathSeparatorChar );
        opts.addOption( op );
        op = new Option( "e", "excluded-attributes", true, "the attributes to exclude" );
        op.setRequired( false );
        op.setValueSeparator( File.pathSeparatorChar );
        opts.addOption( op );
        op = new Option( "o", "include-operational", false, "include operational attributes: defaults to false" );
        op.setRequired( false );
        opts.addOption( op );
        op = new Option( "i", "install-path", true, "path to apacheds installation directory" );
        op.setRequired( true );
        opts.addOption( op );
        return opts;
    }
}
