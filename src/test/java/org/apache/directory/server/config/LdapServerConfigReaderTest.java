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

package org.apache.directory.server.config;


import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.directory.junit.tools.Concurrent;
import org.apache.directory.junit.tools.ConcurrentJunitRunner;
import org.apache.directory.server.config.beans.ConfigBean;
import org.apache.directory.server.config.beans.LdapServerBean;
import org.apache.directory.server.core.partition.ldif.SingleFileLdifPartition;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.schema.registries.SchemaLoader;
import org.apache.directory.shared.ldap.util.LdapExceptionUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test class for ConfigPartitionReader
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(ConcurrentJunitRunner.class)
@Concurrent()
public class LdapServerConfigReaderTest
{
    private static File workDir = new File( System.getProperty( "java.io.tmpdir" ) + "/server-work" );

    private static SchemaManager schemaManager;


    @BeforeClass
    public static void readConfig() throws Exception
    {
        File workDir = new File( System.getProperty( "java.io.tmpdir" ) + "/server-work" );
        FileUtils.deleteDirectory( workDir );
        workDir.mkdir();

        String workingDirectory = workDir.getPath();
        // Extract the schema on disk (a brand new one) and load the registries
        File schemaRepository = new File( workingDirectory, "schema" );
        
        if ( schemaRepository.exists() )
        {
            FileUtils.deleteDirectory( schemaRepository );
        }

        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy();

        SchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        // We have to load the schema now, otherwise we won't be able
        // to initialize the Partitions, as we won't be able to parse 
        // and normalize their suffix DN
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if ( errors.size() != 0 )
        {
            throw new Exception( "Schema load failed : " + LdapExceptionUtils.printErrors( errors ) );
        }
    }


    @Test
    public void testLdapServer() throws Exception
    {
        File configDir = new File( workDir, "ldapServer" ); // could be any directory, cause the config is now in a single file
        String configFile = LdifConfigExtractor.extractSingleFileConfig( configDir, "ldapServer.ldif", true );

        SingleFileLdifPartition configPartition = new SingleFileLdifPartition( configFile );
        configPartition.setId( "config" );
        configPartition.setSuffix( new DN( "ou=config" ) );
        configPartition.setSchemaManager( schemaManager );
        
        configPartition.initialize();
        ConfigPartitionReader cpReader = new ConfigPartitionReader( configPartition, workDir );
        
        ConfigBean configBean = cpReader.readConfig( new DN( "ou=servers,ads-directoryServiceId=default,ou=config" ), ConfigSchemaConstants.ADS_LDAP_SERVER_OC.getValue() );

        assertNotNull( configBean );
        LdapServerBean ldapServerBean = (LdapServerBean)configBean.getDirectoryServiceBeans().get( 0 );
        assertNotNull( ldapServerBean );
    }
}
