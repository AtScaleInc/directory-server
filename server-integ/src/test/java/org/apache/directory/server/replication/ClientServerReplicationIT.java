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

package org.apache.directory.server.replication;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.directory.server.annotations.CreateConsumer;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.replication.consumer.ReplicationConsumer;
import org.apache.directory.server.ldap.replication.consumer.ReplicationConsumerImpl;
import org.apache.directory.server.ldap.replication.provider.SyncReplRequestHandler;
import org.apache.directory.shared.ldap.model.entry.DefaultEntry;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.message.ModifyRequest;
import org.apache.directory.shared.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.shared.ldap.model.message.SearchRequest;
import org.apache.directory.shared.ldap.model.message.SearchRequestImpl;
import org.apache.directory.shared.ldap.model.message.SearchScope;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.apache.directory.shared.ldap.model.name.Rdn;
import org.apache.directory.shared.ldap.model.schema.SchemaManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for replication subsystem in client-server mode.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ClientServerReplicationIT
{
    private static final Logger CONSUMER_LOG = LoggerFactory.getLogger( "CONSUMER_LOG" );
    private static LdapServer providerServer;

    private static LdapServer consumerServer;

    private static SchemaManager schemaManager;
    
    private static CoreSession providerSession;
    
    private static CoreSession consumerSession;
    
    private static AtomicInteger entryCount = new AtomicInteger();
    
    @BeforeClass
    public static void setUp() throws Exception
    {
        Class<?> justLoadToSetControlProperties = Class.forName( FrameworkRunner.class.getName() );
        startProvider();
        startConsumer();
    }


    @AfterClass
    public static void tearDown()
    {
        consumerServer.stop();
        providerServer.stop();
    }
    
    
    private void dump( CoreSession session, Dn entryDn )
    {
        try
        {
            SearchRequest searchRequest = new SearchRequestImpl();
            
            searchRequest.setBase( new Dn( schemaManager, "dc=example,dc=com" ) );
            searchRequest.setFilter( "(objectClass=*)" );
            searchRequest.setScope( SearchScope.SUBTREE );
            searchRequest.addAttributes( "entryUuid" );
            
            CONSUMER_LOG.debug( "-----------> Dumping the server <-----------" );
            CONSUMER_LOG.debug( "-----------> Looking for " + entryDn.getNormName() + " <-----------" );
            
            EntryFilteringCursor cursor = session.search( searchRequest );
            
            while ( cursor.next() )
            {
                Entry entry = cursor.get();
                
                if ( entry.getDn().equals( entryDn ) )
                {
                    CONSUMER_LOG.debug( "The searched entry exists !!!" );
                    CONSUMER_LOG.debug( "found Entry " + entry.getDn().getNormName() + " exists, entrtyUuid = " + entry.get( "entryUuid" ) );
                    continue;
                }
                
                CONSUMER_LOG.debug( "Entry " + entry.getDn().getNormName() + " exists, entrtyUuid = " + entry.get( "entryUuid" ) );
            }
            
            cursor.close();

            CONSUMER_LOG.debug( "-----------> Dump done <-----------" );
            //new Exception().printStackTrace();
        }
        catch ( Exception le )
        {
            // Do nothing
            le.printStackTrace();
        }
    }

    
    /**
     * Check that the entry exists in the target server. We wait up to 10 seconds, by
     * 100ms steps, until either the entry s found, or we have exhausted the 10 seconds delay.
     */
    private boolean checkEntryExistence( CoreSession session, Dn entryDn ) throws Exception
    {
        return checkEntryExistence( session, entryDn, false );
    }
    
    
    /**
     * Check that the entry exists in the target server. We wait up to 10 seconds, by
     * 100ms steps, until either the entry s found, or we have exhausted the 10 seconds delay.
     */
    private boolean checkEntryExistence( CoreSession session, Dn entryDn, boolean print ) throws Exception
    {
        boolean replicated = false;
        
        for ( int i = 0; i < 100; i++ )
        {
            Thread.sleep( 50 );
            
            if ( session.exists( entryDn ) )
            {
                if ( print )
                {      
                    System.out.println( entryDn.getName() + " exists " );
                }
                
                replicated = true;
                break;
            }
            
            Thread.sleep( 50 );
        }
        
        if ( replicated == false )
        {
            dump( session, entryDn );
        }
        
        return replicated;
    }
    
    
    /**
     * Check that the entry exists and has been deleted in the target server. We wait up to 10 seconds, by
     * 100ms steps, until either the entry is deleted, or we have exhausted the 10 seconds delay,
     * or the entry was never found to start with.
     */
    private boolean checkEntryDeletion( CoreSession session, Dn entryDn ) throws Exception
    {
        boolean exists = session.exists( entryDn );
        
        if ( ! exists )
        {
            return true;
        }
        
        for ( int i = 0; i < 100; i++ )
        {
            Thread.sleep( 50 );

            exists = session.exists( entryDn );
            
            if ( !exists )
            {
                return true;
            }

            Thread.sleep( 50 );
        }
        
        dump( session, entryDn );
        
        return false;
    }

    
    @Test
    public void testModify() throws Exception
    {
        Entry provUser = createEntry();
        
        assertFalse( consumerSession.exists( provUser.getDn() ) );
        
        providerSession.add( provUser );
        
        assertTrue( providerSession.exists( provUser.getDn() ) );
        
        ModifyRequest modReq = new ModifyRequestImpl();
        modReq.setName( provUser.getDn() );
        modReq.add( "userPassword", "secret" );
        
        providerSession.modify( modReq );
        
        assertTrue( checkEntryExistence( consumerSession, provUser.getDn() ) );
        waitAndCompareEntries( provUser.getDn() );
    }
    
    
    @Test
    public void testModDn() throws Exception
    {
        Entry provUser = createEntry();
        
        assertFalse( consumerSession.exists( provUser.getDn() ) );
        
        // Add entry : "cn=entryN,dc=example,dc=com"
        providerSession.add( provUser ); // 1
        
        Dn usersContainer = new Dn( schemaManager, "ou=users,dc=example,dc=com" );
        
        DefaultEntry entry = new DefaultEntry( schemaManager, usersContainer,
            "objectClass: organizationalUnit",
            "ou: users" );
        
        // Add entry "ou=users,dc=example,dc=com"
        providerSession.add( entry ); // 2
        
        assertTrue( checkEntryExistence( consumerSession, usersContainer ) );
        waitAndCompareEntries( entry.getDn() );
        
        // Move entry "cn=entryN,dc=example,dc=com" to "ou=users,dc=example,dc=com"
        Dn userDn = provUser.getDn();
        providerSession.move( userDn, usersContainer );
        
        // The moved entry : "cn=entryN,ou=users,dc=example,dc=com"
        Dn movedEntryDn = usersContainer.add( userDn.getRdn() );
        
        assertTrue( checkEntryExistence( consumerSession, movedEntryDn ) );
        waitAndCompareEntries( movedEntryDn );
        
        Rdn newName = new Rdn( schemaManager, movedEntryDn.getRdn().getName() + "renamed");
        
        // Rename "cn=entryN,ou=users,dc=example,dc=com" to "cn=entryNrenamed,ou=users,dc=example,dc=com"
        providerSession.rename( movedEntryDn, newName, true );
        
        Dn renamedEntryDn = usersContainer.add( newName );
        
        assertTrue( checkEntryExistence( consumerSession, renamedEntryDn ) );
        waitAndCompareEntries( renamedEntryDn );
        
        // now move and rename
        Dn newParent = usersContainer.getParent();
        
        newName = new Rdn( schemaManager, renamedEntryDn.getRdn().getName() + "MovedAndRenamed");
        
        // Move and rename "cn=entryNrenamed,ou=users,dc=example,dc=com" to
        // "cn=entryNMovedAndRenamed,dc=example,dc=com"
        providerSession.moveAndRename( renamedEntryDn, newParent, newName, false ); //4
        
        Dn movedAndRenamedEntry = newParent.add( newName );

        assertTrue( checkEntryExistence( consumerSession, movedAndRenamedEntry ) );
        waitAndCompareEntries( movedAndRenamedEntry );
    }
    
    
    @Test
    @Ignore
    public void testModDnLoop() throws Exception
    {
        for ( int i = 0; i < 10000; i++ )
        {
            System.out.println( ">>>>>> loop " + ( i + 1 ) + " <<<<<<" );
            Entry newuser = createEntry();
            
            assertFalse( consumerSession.exists( newuser.getDn() ) );
            
            // Add entry : "cn=entryN,dc=example,dc=com"
            providerSession.add( newuser ); // 1
            
            Dn usersContainer = new Dn( schemaManager, "ou=users,dc=example,dc=com" );
            
            DefaultEntry usersEntry = new DefaultEntry( schemaManager, usersContainer,
                "objectClass: organizationalUnit",
                "ou: users" );
            
            // Add entry "ou=users,dc=example,dc=com"
            providerSession.add( usersEntry ); // 2
            
            assertTrue( checkEntryExistence( consumerSession, usersContainer ) );
            waitAndCompareEntries( usersEntry.getDn() );
            
            // Move entry "cn=entryN,dc=example,dc=com" to "ou=users,dc=example,dc=com"
            Dn userDn = newuser.getDn();
            providerSession.move( userDn, usersContainer );
            
            // The moved entry : "cn=entryN,ou=users,dc=example,dc=com"
            Dn movedEntryDn = usersContainer.add( userDn.getRdn() );
            
            assertTrue( checkEntryExistence( consumerSession, movedEntryDn ) );
            waitAndCompareEntries( movedEntryDn );
            
            Rdn newName = new Rdn( schemaManager, movedEntryDn.getRdn().getName() + "renamed");
            
            // Rename "cn=entryN,ou=users,dc=example,dc=com" to "cn=entryNrenamed,ou=users,dc=example,dc=com"
            providerSession.rename( movedEntryDn, newName, true );
            
            Dn renamedEntryDn = usersContainer.add( newName );
            
            assertTrue( checkEntryExistence( consumerSession, renamedEntryDn ) );
            waitAndCompareEntries( renamedEntryDn );
            
            // now move and rename
            Dn newParent = usersContainer.getParent();
            
            newName = new Rdn( schemaManager, renamedEntryDn.getRdn().getName() + "MovedAndRenamed");
            
            // Move and rename "cn=entryNrenamed,ou=users,dc=example,dc=com" to
            // "cn=entryNMovedAndRenamed,dc=example,dc=com"
            providerSession.moveAndRename( renamedEntryDn, newParent, newName, false ); //4
            
            Dn movedAndRenamedEntry = newParent.add( newName );
    
            assertTrue( checkEntryExistence( consumerSession, movedAndRenamedEntry ) );
            waitAndCompareEntries( movedAndRenamedEntry );
            
            // Ok, no failure, revert everything
            providerSession.delete( movedAndRenamedEntry );
            providerSession.delete( usersContainer );
        }
    }
    
    
    /**
     * Test the replication of a deleted entry
     */
    @Test
    public void testDelete() throws Exception
    {
        Entry provUser = createEntry();
        
        providerSession.add( provUser );
        
        assertTrue( checkEntryExistence( consumerSession, provUser.getDn() ) );
        waitAndCompareEntries( provUser.getDn() );
        
        assertTrue( providerSession.exists( provUser.getDn() ) );
        assertTrue( consumerSession.exists( provUser.getDn() ) );

        providerSession.delete( provUser.getDn() );
        
        assertTrue( checkEntryDeletion( consumerSession, provUser.getDn() ) );
        assertFalse( providerSession.exists( provUser.getDn() ) );
    }
    
    
    private Entry restartConsumer( Entry provUser ) throws Exception
    {
        // Now stop the consumer
        CONSUMER_LOG.debug( "--------------------------------------------------------------" );
        CONSUMER_LOG.debug( "----> 1 Stopping the consumer --------------------------------" );
        CONSUMER_LOG.debug( "--------------------------------------------------------------" );
        consumerServer.stop();
        
        // And delete the entry in the provider
        Dn deletedUserDn = provUser.getDn();
        //System.out.println( "----> 5 deleting entry " + deletedUserDn + " from provider --------------------------------" );
        providerSession.delete( deletedUserDn );
        
        // Create a new entry
        provUser = createEntry();
        Dn addedUserDn = provUser.getDn();
        //System.out.println( "----> 6 adding entry " + provUser.getDn() + " into provider --------------------------------" );
        providerSession.add( provUser );
        
        // let the provider log the events before the consumer sends a request
        // we are dealing with fraction of seconds cause of the programmatic simulation
        // it is impossible in the real world scenario
        Thread.sleep( 1000 );
        
        // Restart the consumer
        CONSUMER_LOG.debug( "--------------------------------------------------------------" );
        CONSUMER_LOG.debug( "----> 2 Restarting the consumer --------------------------------" );
        CONSUMER_LOG.debug( "--------------------------------------------------------------" );
        consumerServer.start();
        
        //assertTrue( consumerSession.exists( deletedUserDn ) );
        //System.out.println( "----> 7bis entry " + deletedUserDn + " is still present in consumer --------------------------------" );
        
        assertTrue( checkEntryDeletion( consumerSession, deletedUserDn ) );
        //System.out.println( "----> 8 Entry " + deletedUserDn + " deleted from consumer --------------------------------" );
        
        assertTrue( checkEntryExistence( consumerSession, addedUserDn ) );
        waitAndCompareEntries( addedUserDn );
        //System.out.println( "----> 8 Entry " + addedUserDn + " added into consumer --------------------------------" );

        return provUser;
    }
    
    
    @Test
    @Ignore( "we have some random failures" )
    public void testRebootConsumer() throws Exception
    {
        System.out.println( "----> 1 testRebootConsumer started --------------------------------" );
        Entry provUser = createEntry();
        
        assertFalse( providerSession.exists(provUser.getDn() ) );
        assertFalse( consumerSession.exists(provUser.getDn() ) );
        
        System.out.println( "----> 2 Adding entry " + provUser.getDn() +" in provider --------------------------------" );
        providerSession.add( provUser );
        
        assertTrue( checkEntryExistence( consumerSession, provUser.getDn() ) );
        waitAndCompareEntries( provUser.getDn() );

        System.out.println( "----> 3 entry " + provUser.getDn() +" present in consumer --------------------------------" );

        assertTrue( providerSession.exists(provUser.getDn() ) );
        assertTrue( consumerSession.exists(provUser.getDn() ) );
        
        for ( int i = 0; i < 1000; i++ )
        {
            CONSUMER_LOG.debug( "=============================== Loop " + i );
            System.out.println( "=============================== Loop " + i );
            provUser = restartConsumer( provUser );
        }
        
        CONSUMER_LOG.debug( "===========> Dumping the provider <===========" );
        dump( providerSession, Dn.ROOT_DSE );
        CONSUMER_LOG.debug( "===========> Dumping the consumer <===========" );
        dump( consumerSession, Dn.ROOT_DSE );
    }
    
    
    private void waitAndCompareEntries( Dn dn ) throws Exception
    {
        // sleep for 2 sec (twice the refresh interval), just to let the first refresh request succeed
        Entry providerEntry = providerSession.lookup( dn, "*", "+" );
        
        Entry consumerEntry = consumerSession.lookup( dn, "*", "+" );
        assertEquals( providerEntry, consumerEntry );
    }
    
    
    private Entry createEntry() throws Exception
    {
        String user = "user"+ entryCount.incrementAndGet();
        
        String dn = "cn=" + user + ",dc=example,dc=com";
        
        DefaultEntry entry = new DefaultEntry( schemaManager, dn,
            "objectClass", "person",
            "cn", user,
            "sn", user );
        
        return entry;
    }
    
    
    @CreateDS(
        allowAnonAccess = true, 
        name = "provider-replication", 
        enableChangeLog = false,
        partitions =
        {
            @CreatePartition(
                name = "example",
                suffix = "dc=example,dc=com",
                indexes =
                {
                    @CreateIndex(attribute = "objectClass"),
                    @CreateIndex(attribute = "dc"),
                    @CreateIndex(attribute = "ou")
                },
                contextEntry=@ContextEntry( entryLdif = 
                    "dn: dc=example,dc=com\n" +
                    "objectClass: domain\n" +
                    "dc: example" ) )
             })
    @CreateLdapServer(transports =
        { @CreateTransport( port=16000, protocol = "LDAP") })
    public static void startProvider() throws Exception
    {
        DirectoryService provDirService = DSAnnotationProcessor.getDirectoryService();

        providerServer = ServerAnnotationProcessor.getLdapServer( provDirService );
        providerServer.setReplicationReqHandler( new SyncReplRequestHandler() );
        providerServer.startReplicationProducer();
        
        Runnable r = new Runnable()
        {
            
            public void run()
            {
                try
                {
                    schemaManager = providerServer.getDirectoryService().getSchemaManager();
                    providerSession = providerServer.getDirectoryService().getAdminSession();
                }
                catch( Exception e )
                {
                    e.printStackTrace();
                }
            }
        };

        Thread t = new Thread( r );
        t.setDaemon( true );
        t.start();
        t.join();
    }
    
    
    @CreateDS(
        allowAnonAccess = true, 
        enableChangeLog = false,
        name = "consumer-replication", 
        partitions =
        {
            @CreatePartition(
                name = "example",
                suffix = "dc=example,dc=com",
                indexes =
                {
                    @CreateIndex(attribute = "objectClass"),
                    @CreateIndex(attribute = "dc"),
                    @CreateIndex(attribute = "ou")
                },
                contextEntry=@ContextEntry( entryLdif = 
                    "dn: dc=example,dc=com\n" +
                    "objectClass: domain\n" +
                    "dc: example" ) )
             })
    @CreateLdapServer(transports =
        { @CreateTransport( port=17000, protocol = "LDAP") })
    @CreateConsumer
        (
            remoteHost = "localhost",
            remotePort = 16000,
            replUserDn = "uid=admin,ou=system",
            replUserPassword = "secret",
            useTls = false,
            baseDn = "dc=example,dc=com",
            refreshInterval = 1000,
            replicaId = 1
        )
    public static void startConsumer() throws Exception
    {
        DirectoryService provDirService = DSAnnotationProcessor.getDirectoryService();
        consumerServer = ServerAnnotationProcessor.getLdapServer( provDirService );
        
        final ReplicationConsumerImpl consumer = (ReplicationConsumerImpl)ServerAnnotationProcessor.createConsumer();
        
        List<ReplicationConsumer> replConsumers = new ArrayList<ReplicationConsumer>();
        replConsumers.add( consumer );
        
        consumerServer.setReplConsumers( replConsumers );
        consumerServer.startReplicationConsumers();
        
        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    DirectoryService ds = consumerServer.getDirectoryService();
                    
                    Dn configDn = new Dn( ds.getSchemaManager(), "ads-replConsumerId=localhost,ou=system" );
                    consumer.getConfig().setConfigEntryDn( configDn );
                    
                    Entry provConfigEntry = new DefaultEntry( ds.getSchemaManager(), configDn,
                        "objectClass: ads-replConsumer",
                        "ads-replConsumerId: localhost",
                        "ads-searchBaseDN", consumer.getConfig().getBaseDn(),
                        "ads-replProvHostName", consumer.getConfig().getRemoteHost(),
                        "ads-replProvPort", String.valueOf( consumer.getConfig().getRemotePort() ),
                        "ads-replRefreshInterval", String.valueOf( consumer.getConfig().getRefreshInterval() ),
                        "ads-replRefreshNPersist", String.valueOf( consumer.getConfig().isRefreshNPersist() ),
                        "ads-replSearchScope", consumer.getConfig().getSearchScope().getLdapUrlValue(),
                        "ads-replSearchFilter", consumer.getConfig().getFilter(),
                        "ads-replSearchSizeLimit", String.valueOf( consumer.getConfig().getSearchSizeLimit() ),
                        "ads-replSearchTimeOut", String.valueOf( consumer.getConfig().getSearchTimeout() ),
                        "ads-replUserDn", consumer.getConfig().getReplUserDn(),
                        "ads-replUserPassword", consumer.getConfig().getReplUserPassword() );
                    
                    provConfigEntry.put( "ads-replAliasDerefMode", consumer.getConfig().getAliasDerefMode().getJndiValue() );
                    provConfigEntry.put( "ads-replAttributes", consumer.getConfig().getAttributes() );

                    
                    consumerSession = consumerServer.getDirectoryService().getAdminSession();
                    consumerSession.add( provConfigEntry );
                }
                catch( Exception e )
                {
                    e.printStackTrace();
                }
            }
        };

        Thread t = new Thread( r );
        t.setDaemon( true );
        t.start();
        t.join();
    }
}
