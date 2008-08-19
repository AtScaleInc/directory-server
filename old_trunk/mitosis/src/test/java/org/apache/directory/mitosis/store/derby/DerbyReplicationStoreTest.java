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
package org.apache.directory.mitosis.store.derby;
 

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Name;
import javax.naming.ldap.LdapName;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.DeepTrimToLowerNormalizer;
import org.apache.directory.shared.ldap.schema.OidNormalizer;
import org.apache.directory.mitosis.common.CSN;
import org.apache.directory.mitosis.common.CSNFactory;
import org.apache.directory.mitosis.common.CSNVector;
import org.apache.directory.mitosis.common.ReplicaId;
import org.apache.directory.mitosis.common.DefaultCSN;
import org.apache.directory.mitosis.common.DefaultCSNFactory;
import org.apache.directory.mitosis.common.DefaultUUIDFactory;
import org.apache.directory.mitosis.common.UUID;
import org.apache.directory.mitosis.common.UUIDFactory;
import org.apache.directory.mitosis.configuration.ReplicationConfiguration;
import org.apache.directory.mitosis.operation.AddAttributeOperation;
import org.apache.directory.mitosis.operation.AddEntryOperation;
import org.apache.directory.mitosis.operation.CompositeOperation;
import org.apache.directory.mitosis.operation.DeleteAttributeOperation;
import org.apache.directory.mitosis.operation.Operation;
import org.apache.directory.mitosis.operation.ReplaceAttributeOperation;
import org.apache.directory.mitosis.store.ReplicationLogIterator;
import org.apache.directory.mitosis.store.ReplicationStoreException;


public class DerbyReplicationStoreTest extends TestCase
{
    private static final ReplicaId REPLICA_ID = new ReplicaId( "TEST_REPLICA" );
    private static final ReplicaId OTHER_REPLICA_ID = new ReplicaId( "OTHER_REPLICA" );
    private static final ReplicaId OTHER_REPLICA_ID_2 = new ReplicaId( "OTHER_REPLICA_2" );
    private static final File DB_PATH = new File( "target/testDB" );

    private final UUIDFactory uuidFactory = new DefaultUUIDFactory();
    private final CSNFactory csnFactory = new DefaultCSNFactory();
    private DerbyReplicationStore store;
    private int testCount;
    private long startTime;
    private DefaultDirectoryService service;


    public void setUp() throws Exception
    {
        dropDatabase();
        startupDatabase( REPLICA_ID );
        initStopWatch();
    }


    private void startupDatabase( ReplicaId replicaId ) throws Exception
    {
        // Prepare configuration
        ReplicationConfiguration cfg = new ReplicationConfiguration();
        cfg.setReplicaId( replicaId );

        // Open store
        store = new DerbyReplicationStore();
        store.setTablePrefix( "TEST_" );
        service = new DefaultDirectoryService();
        service.setWorkingDirectory( DB_PATH );
        store.open( service, cfg );
    }


    public void tearDown() throws Exception
    {
        store.close();
        dropDatabase();
    }


    private void dropDatabase() throws IOException
    {
        FileUtils.deleteDirectory( DB_PATH );
        File logFile = new File( "derby.log" );
        if ( !logFile.delete() )
        {
            logFile.deleteOnExit();
        }
    }


    public void testOperations() throws Exception
    {
        subTestReopen();
        printElapsedTime( "Reopen" );
        subTestUUID();
        printElapsedTime( "UUID" );
        subTestEmptyLog();
        printElapsedTime( "EmptyLog" );
        subTestWriteLog();
        printElapsedTime( "WriteLog" );
        subTestRemoveLogs();
        printElapsedTime( "RemoveLogs" );
        subTestVectors();
        printElapsedTime( "Vectors" );
    }


    private void subTestReopen() throws Exception
    {
        store.close();
        try
        {
            startupDatabase( OTHER_REPLICA_ID );
            Assert.fail( "Store cannot start up with wrong replica ID." );
        }
        catch ( ReplicationStoreException e )
        {
        }
        startupDatabase( REPLICA_ID );
    }


    private void subTestUUID() throws Exception
    {
        UUID uuid = uuidFactory.newInstance();
        Name name = new LdapName( "ou=a, ou=b" );
        Assert.assertTrue( store.putUUID( uuid, name ) );
        Assert.assertEquals( name, store.getDN( uuid ) );
        Assert.assertTrue( store.removeUUID( uuid ) );
        Assert.assertFalse( store.removeUUID( uuid ) );
        Assert.assertNull( store.getDN( uuid ) );
    }


    private void subTestEmptyLog() throws Exception
    {
        ReplicationLogIterator it;

        it = store.getLogs( csnFactory.newInstance( REPLICA_ID ), true );
        Assert.assertFalse( it.next() );
        it.close();
        it = store.getLogs( csnFactory.newInstance( REPLICA_ID ), false );
        Assert.assertFalse( it.next() );
        it.close();
        it = store.getLogs( csnFactory.newInstance( OTHER_REPLICA_ID ), true );
        Assert.assertFalse( it.next() );
        it.close();
        it = store.getLogs( csnFactory.newInstance( OTHER_REPLICA_ID ), false );
        Assert.assertFalse( it.next() );
        it.close();

        Assert.assertEquals( 0, store.getLogSize() );
    }


    private void subTestWriteLog() throws Exception
    {
        Map<String, OidNormalizer> oids = new HashMap<String, OidNormalizer>();

        oids.put( "ou", new OidNormalizer( "ou", new DeepTrimToLowerNormalizer() ) );
        oids.put( "organizationalUnitName", new OidNormalizer( "ou", new DeepTrimToLowerNormalizer() ) );
        oids.put( "2.5.4.11", new OidNormalizer( "ou", new DeepTrimToLowerNormalizer() ) );

        AttributeTypeRegistry atRegistry = service.getRegistries().getAttributeTypeRegistry();

        CSN csn = csnFactory.newInstance( REPLICA_ID );
        CompositeOperation op1 = new CompositeOperation( csn );
        LdapDN ouA =  new LdapDN( "ou=a" ).normalize( oids );
        op1.add( new AddEntryOperation( csn, 
            new DefaultServerEntry( service.getRegistries(), ouA ) ) );
        
        op1.add( new AddAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );
        
        op1.add( new ReplaceAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );
        
        op1.add( new DeleteAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );

        store.putLog( op1 );
        testGetLogs( csn, op1 );

        csn = csnFactory.newInstance( OTHER_REPLICA_ID );
        CompositeOperation op2 = new CompositeOperation( csn );
        op2.add( new AddEntryOperation( csn, 
            new DefaultServerEntry( service.getRegistries(), ouA ) ) );
        
        op2.add( new AddAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );
        
        op2.add( new ReplaceAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );
        
        op2.add( new DeleteAttributeOperation( csn, ouA, 
            new DefaultServerAttribute( "ou", atRegistry.lookup( "ou" ), "valie" ) ) );

        store.putLog( op2 );
        testGetLogs( csn, op2 );

        Assert.assertEquals( 2, store.getLogSize() );
        Assert.assertEquals( 1, store.getLogSize( REPLICA_ID ) );
        Assert.assertEquals( 1, store.getLogSize( OTHER_REPLICA_ID ) );

        // Test getLogs(CSNVector, true)
        List<Operation> expected = new ArrayList<Operation>();
        expected.add( op1 );
        expected.add( op2 );
        CSNVector updateVector = new CSNVector();
        testGetLogs( updateVector, true, expected );

        updateVector = new CSNVector();
        updateVector.setCSN( op1.getCSN() );
        testGetLogs( updateVector, true, expected );
        
        updateVector = new CSNVector();
        updateVector.setCSN( op2.getCSN() );
        testGetLogs( updateVector, true, expected );
        
        updateVector = new CSNVector();
        updateVector.setCSN( op1.getCSN() );
        updateVector.setCSN( op2.getCSN() );
        testGetLogs( updateVector, true, expected );

        // Test getLogs(CSNVector, false)
        expected = new ArrayList<Operation>();
        expected.add( op1 );
        expected.add( op2 );
        updateVector = new CSNVector();
        testGetLogs( updateVector, false, expected );
        expected = new ArrayList<Operation>();
        expected.add( op2 );
        updateVector = new CSNVector();
        updateVector.setCSN( op1.getCSN() );
        testGetLogs( updateVector, false, expected );
        expected = new ArrayList<Operation>();
        expected.add( op1 );
        updateVector = new CSNVector();
        updateVector.setCSN( op2.getCSN() );
        testGetLogs( updateVector, false, expected );
        expected = new ArrayList<Operation>();
        updateVector = new CSNVector();
        updateVector.setCSN( op1.getCSN() );
        updateVector.setCSN( op2.getCSN() );
        testGetLogs( updateVector, false, expected );
    }


    private void subTestRemoveLogs()
    {
        CSN csn;
        ReplicationLogIterator it;

        it = store.getLogs( new DefaultCSN( 0, REPLICA_ID, 0 ), false );
        it.next();
        csn = it.getOperation().getCSN();
        it.close();

        Assert.assertEquals( 0, store.removeLogs( csn, false ) );
        Assert.assertEquals( 1, store.removeLogs( csn, true ) );
        Assert.assertEquals( 0, store.getLogSize( REPLICA_ID ) );

        it = store.getLogs( new DefaultCSN( 0, OTHER_REPLICA_ID, 0 ), false );
        Assert.assertTrue( it.next() );
        csn = it.getOperation().getCSN();
        it.close();

        Assert.assertEquals( 0, store.removeLogs( csn, false ) );
        Assert.assertEquals( 1, store.removeLogs( csn, true ) );
        Assert.assertEquals( 0, store.getLogSize( OTHER_REPLICA_ID ) );

        Assert.assertEquals( 0, store.getLogSize() );
    }


    private void subTestVectors() throws Exception
    {
        CSN csnA = new DefaultCSN( 0, REPLICA_ID, 0 );
        CSN csnB = new DefaultCSN( 1, REPLICA_ID, 0 );
        CSN csnC = new DefaultCSN( 0, OTHER_REPLICA_ID_2, 0 );
        CSN csnD = new DefaultCSN( 0, OTHER_REPLICA_ID_2, 1 );
        store.putLog( new Operation( csnA ) );
        store.putLog( new Operation( csnB ) );
        store.putLog( new Operation( csnC ) );
        store.putLog( new Operation( csnD ) );

        Set<ReplicaId> expectedKnownReplicaIds = new HashSet<ReplicaId>();
        expectedKnownReplicaIds.add( REPLICA_ID );
        expectedKnownReplicaIds.add( OTHER_REPLICA_ID );
        expectedKnownReplicaIds.add( OTHER_REPLICA_ID_2 );

        Assert.assertEquals( expectedKnownReplicaIds, store.getKnownReplicaIds() );

        CSNVector expectedUpdateVector = new CSNVector();
        expectedUpdateVector.setCSN( csnB );
        expectedUpdateVector.setCSN( csnD );

        Assert.assertEquals( expectedUpdateVector, store.getUpdateVector() );

        CSNVector expectedPurgeVector = new CSNVector();
        expectedPurgeVector.setCSN( csnA );
        expectedPurgeVector.setCSN( csnC );

        Assert.assertEquals( expectedPurgeVector, store.getPurgeVector() );
    }


    private void testGetLogs( CSN csn, Operation operation )
    {
        List<Operation> operations = new ArrayList<Operation>();
        operations.add( operation );
        testGetLogs( csn, operations );
    }


    private void testGetLogs( CSN csn, List<Operation> operations )
    {
        Iterator<Operation> it = operations.iterator();
        ReplicationLogIterator rit = store.getLogs( csn, true );
        testGetLogs( it, rit );

        rit = store.getLogs( csn, false );
        Assert.assertFalse( rit.next() );
        rit.close();
    }


    private void testGetLogs( CSNVector updateVector, boolean inclusive, List<Operation> operations )
    {
        Iterator<Operation> it = operations.iterator();
        ReplicationLogIterator rit = store.getLogs( updateVector, inclusive );
        testGetLogs( it, rit );
    }


    private void testGetLogs( Iterator<Operation> expectedIt, ReplicationLogIterator actualIt )
    {
        while ( expectedIt.hasNext() )
        {
            Operation expected = expectedIt.next();
            Assert.assertTrue( actualIt.next() );

            Operation actual = actualIt.getOperation();
            Assert.assertEquals( expected.getCSN(), actual.getCSN() );
            assertEquals( expected, actual );
        }
        Assert.assertFalse( actualIt.next() );
        actualIt.close();
    }


    private void initStopWatch()
    {
        startTime = System.currentTimeMillis();
    }


    private void printElapsedTime( String testName )
    {
        long endTime = System.currentTimeMillis();
        System.out.println( "Subtest #" + ( ++testCount ) + " [" + testName + "]: " + ( endTime - startTime ) + " ms" );
        startTime = System.currentTimeMillis();
    }


    private static void assertEquals( Operation expected, Operation actual )
    {
        Assert.assertEquals( expected.toString(), actual.toString() );
    }
}