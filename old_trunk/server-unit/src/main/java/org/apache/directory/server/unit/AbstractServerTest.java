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
package org.apache.directory.server.unit;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.jndi.CoreContextFactory;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.handlers.bind.CramMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.DigestMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.GssapiMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.SimpleMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.ntlm.NtlmMechanismHandler;
import org.apache.directory.server.ldap.handlers.extended.StartTlsHandler;
import org.apache.directory.server.ldap.handlers.extended.StoredProcedureExtendedOperationHandler;
import org.apache.directory.server.protocol.shared.SocketAcceptor;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.exception.LdapConfigurationException;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifReader;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.mina.util.AvailablePortFinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple testcase for testing JNDI provider functionality.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class AbstractServerTest extends TestCase
{
    private static final Logger LOG = LoggerFactory.getLogger( AbstractServerTest.class );
    private static final List<LdifEntry> EMPTY_LIST = Collections.unmodifiableList( new ArrayList<LdifEntry>( 0 ) );
    private static final String CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    /** the context root for the system partition */
    protected LdapContext sysRoot;

    /** the context root for the rootDSE */
    protected LdapContext rootDSE;

    /** the context root for the schema */
    protected LdapContext schemaRoot;

    /** flag whether to delete database files for each test or not */
    protected boolean doDelete = true;

//    protected ApacheDS apacheDS = new ApacheDS();

    protected int port = -1;

    private static int start;
    private static long t0;
    protected static int nbTests = 10000;
    protected DirectoryService directoryService;
    protected SocketAcceptor socketAcceptor;
    protected LdapServer ldapServer;


    /**
     * If there is an LDIF file with the same name as the test class 
     * but with the .ldif extension then it is read and the entries 
     * it contains are added to the server.  It appears as though the
     * administor adds these entries to the server.
     *
     * @param verifyEntries whether or not all entry additions are checked
     * to see if they were in fact correctly added to the server
     * @return a list of entries added to the server in the order they were added
     * @throws NamingException of the load fails
     */
    protected List<LdifEntry> loadTestLdif( boolean verifyEntries ) throws NamingException
    {
        return loadLdif( getClass().getResourceAsStream( getClass().getSimpleName() + ".ldif" ), verifyEntries );
    }


    /**
     * Loads an LDIF from an input stream and adds the entries it contains to 
     * the server.  It appears as though the administrator added these entries
     * to the server.
     *
     * @param in the input stream containing the LDIF entries to load
     * @param verifyEntries whether or not all entry additions are checked
     * to see if they were in fact correctly added to the server
     * @return a list of entries added to the server in the order they were added
     * @throws NamingException of the load fails
     */
    protected List<LdifEntry> loadLdif( InputStream in, boolean verifyEntries ) throws NamingException
    {
        if ( in == null )
        {
            return EMPTY_LIST;
        }
        
        LdifReader ldifReader = new LdifReader( in );
        List<LdifEntry> entries = new ArrayList<LdifEntry>();

        for ( LdifEntry entry:ldifReader )
        {
            rootDSE.createSubcontext( entry.getDn(), entry.getAttributes() );
            
            if ( verifyEntries )
            {
                verify( entry );
                LOG.info( "Successfully verified addition of entry {}", entry.getDn() );
            }
            else
            {
                LOG.info( "Added entry {} without verification", entry.getDn() );
            }
            
            entries.add( entry );
        }
        
        return entries;
    }
    

    /**
     * Verifies that an entry exists in the directory with the 
     * specified attributes.
     *
     * @param entry the entry to verify
     * @throws NamingException if there are problems accessing the entry
     */
    protected void verify( LdifEntry entry ) throws NamingException
    {
        Attributes readAttributes = rootDSE.getAttributes( entry.getDn() );
        NamingEnumeration<String> readIds = entry.getAttributes().getIDs();
        while ( readIds.hasMore() )
        {
            String id = readIds.next();
            Attribute readAttribute = readAttributes.get( id );
            Attribute origAttribute = entry.getAttributes().get( id );
            
            for ( int ii = 0; ii < origAttribute.size(); ii++ )
            {
                if ( ! readAttribute.contains( origAttribute.get( ii ) ) )
                {
                    LOG.error( "Failed to verify entry addition of {}. {} attribute in original " +
                            "entry missing from read entry.", entry.getDn(), id );
                    throw new AssertionFailedError( "Failed to verify entry addition of " + entry.getDn()  );
                }
            }
        }
    }
    

    /**
     * Common code to get an initial context via a simple bind to the 
     * server over the wire using the SUN JNDI LDAP provider. Do not use 
     * this method until after the setUp() method is called to start the
     * server otherwise it will fail. 
     *
     * @return an LDAP context as the the administrator to the rootDSE
     * @throws NamingException if the server cannot be contacted
     */
    protected LdapContext getWiredContext() throws NamingException
    {
        return getWiredContext( ServerDNConstants.ADMIN_SYSTEM_DN, "secret" );
    }
    
    
    /**
     * Common code to get an initial context via a simple bind to the 
     * server over the wire using the SUN JNDI LDAP provider. Do not use 
     * this method until after the setUp() method is called to start the
     * server otherwise it will fail.
     *
     * @param bindPrincipalDn the DN of the principal to bind as
     * @param password the password of the bind principal
     * @return an LDAP context as the the administrator to the rootDSE
     * @throws NamingException if the server cannot be contacted
     */
    protected LdapContext getWiredContext( String bindPrincipalDn, String password ) throws NamingException
    {
//        if ( ! apacheDS.isStarted() )
//        {
//            throw new ConfigurationException( "The server is not online! Cannot connect to it." );
//        }
        
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port );
        env.put( Context.SECURITY_PRINCIPAL, bindPrincipalDn );
        env.put( Context.SECURITY_CREDENTIALS, password );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        return new InitialLdapContext( env, null );
    }
    
    
    /**
     * Get's the initial context factory for the provider's ou=system context
     * root.
     *
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
        
        if ( start == 0 )
        {
            t0 = System.currentTimeMillis();
        }

        start++;
        directoryService = new DefaultDirectoryService();
        directoryService.setShutdownHookEnabled( false );
        socketAcceptor = new SocketAcceptor( null );
        ldapServer = new LdapServer();
        ldapServer.setSocketAcceptor( socketAcceptor );
        ldapServer.setDirectoryService( directoryService );
        ldapServer.setIpPort( port = AvailablePortFinder.getNextAvailable( 1024 ) );

        setupSaslMechanisms( ldapServer );

        doDelete( directoryService.getWorkingDirectory() );
        configureDirectoryService();
        directoryService.startup();

        configureLdapServer();

        // TODO shouldn't this be before calling configureLdapServer() ???
        ldapServer.addExtendedOperationHandler( new StartTlsHandler() );
        ldapServer.addExtendedOperationHandler( new StoredProcedureExtendedOperationHandler() );

        ldapServer.start();
        setContexts( ServerDNConstants.ADMIN_SYSTEM_DN, "secret" );
    }


    private void setupSaslMechanisms( LdapServer server )
    {
        Map<String, MechanismHandler> mechanismHandlerMap = new HashMap<String,MechanismHandler>();

        mechanismHandlerMap.put( SupportedSaslMechanisms.PLAIN, new SimpleMechanismHandler() );

        CramMd5MechanismHandler cramMd5MechanismHandler = new CramMd5MechanismHandler();
        cramMd5MechanismHandler.setDirectoryService( directoryService );
        mechanismHandlerMap.put( SupportedSaslMechanisms.CRAM_MD5, cramMd5MechanismHandler );

        DigestMd5MechanismHandler digestMd5MechanismHandler = new DigestMd5MechanismHandler();
        digestMd5MechanismHandler.setDirectoryService( directoryService );
        mechanismHandlerMap.put( SupportedSaslMechanisms.DIGEST_MD5, digestMd5MechanismHandler );

        GssapiMechanismHandler gssapiMechanismHandler = new GssapiMechanismHandler();
        gssapiMechanismHandler.setDirectoryService( directoryService );
        mechanismHandlerMap.put( SupportedSaslMechanisms.GSSAPI, gssapiMechanismHandler );

        NtlmMechanismHandler ntlmMechanismHandler = new NtlmMechanismHandler();
        // TODO - set some sort of default NtlmProvider implementation here
        // ntlmMechanismHandler.setNtlmProvider( provider );
        // TODO - or set FQCN of some sort of default NtlmProvider implementation here
        // ntlmMechanismHandler.setNtlmProviderFqcn( "com.foo.BarNtlmProvider" );
        mechanismHandlerMap.put( SupportedSaslMechanisms.NTLM, ntlmMechanismHandler );
        mechanismHandlerMap.put( SupportedSaslMechanisms.GSS_SPNEGO, ntlmMechanismHandler );

        ldapServer.setSaslMechanismHandlers( mechanismHandlerMap );
    }


    protected void configureDirectoryService() throws NamingException
    {
    }


    protected void configureLdapServer()
    {
    }


    protected void setAllowAnonymousAccess( boolean anonymousAccess )
    {
        directoryService.setAllowAnonymousAccess( anonymousAccess );
        ldapServer.setAllowAnonymousAccess( anonymousAccess );
    }

    
    /**
     * Deletes the Eve working directory.
     * @param wkdir the directory to delete
     * @throws IOException if the directory cannot be deleted
     */
    protected void doDelete( File wkdir ) throws IOException
    {
        if ( doDelete )
        {
            if ( wkdir.exists() )
            {
                FileUtils.deleteDirectory( wkdir );
            }

            if ( wkdir.exists() )
            {
                throw new IOException( "Failed to delete: " + wkdir );
            }
        }
    }


    /**
     * Sets the contexts for this base class.  Values of user and password used to
     * set the respective JNDI properties.  These values can be overriden by the
     * overrides properties.
     *
     * @param user the username for authenticating as this user
     * @param passwd the password of the user
     * @throws NamingException if there is a failure of any kind
     */
    protected void setContexts( String user, String passwd ) throws NamingException
    {
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( DirectoryService.JNDI_KEY, directoryService );
        env.put( Context.SECURITY_PRINCIPAL, user );
        env.put( Context.SECURITY_CREDENTIALS, passwd );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, CoreContextFactory.class.getName() );
        setContexts( env );
    }


    /**
     * Sets the contexts of this class taking into account the extras and overrides
     * properties.  
     *
     * @param env an environment to use while setting up the system root.
     * @throws NamingException if there is a failure of any kind
     */
    protected void setContexts( Hashtable<String, Object> env ) throws NamingException
    {
        Hashtable<String, Object> envFinal = new Hashtable<String, Object>( env );
        envFinal.put( Context.PROVIDER_URL, ServerDNConstants.SYSTEM_DN );
        sysRoot = new InitialLdapContext( envFinal, null );

        envFinal.put( Context.PROVIDER_URL, "" );
        rootDSE = new InitialLdapContext( envFinal, null );

        envFinal.put( Context.PROVIDER_URL, ServerDNConstants.OU_SCHEMA_DN );
        schemaRoot = new InitialLdapContext( envFinal, null );
    }


    /**
     * Sets the system context root to null.
     *
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception
    {
        super.tearDown();
        ldapServer.stop();
        try
        {
            directoryService.shutdown();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sysRoot = null;
//        apacheDS = new ApacheDS();
        
        if ( start >= nbTests )
        {
            System.out.println( "Delta = " + ( System.currentTimeMillis() - t0 ) );
        }
    }


    /**
     * Imports the LDIF entries packaged with the Eve JNDI provider jar into
     * the newly created system partition to prime it up for operation.  Note
     * that only ou=system entries will be added - entries for other partitions
     * cannot be imported and will blow chunks.
     *
     * @throws NamingException if there are problems reading the ldif file and
     * adding those entries to the system partition
     * @param in the input stream with the ldif
     */
    protected void importLdif( InputStream in ) throws NamingException
    {
        try
        {
            for ( LdifEntry ldifEntry:new LdifReader( in ) )
            {
                LdapDN dn = new LdapDN( ldifEntry.getDn() );
                rootDSE.createSubcontext( dn, ldifEntry.getAttributes() );
            }
        }
        catch ( Exception e )
        {
            String msg = "failed while trying to parse system ldif file";
            NamingException ne = new LdapConfigurationException( msg );
            ne.setRootCause( e );
            throw ne;
        }
    }
    
    /**
     * Inject an ldif String into the server. DN must be relative to the
     * root.
     * @param ldif the entries to inject
     * @throws NamingException if the entries cannot be added
     */
    protected void injectEntries( String ldif ) throws NamingException
    {
        LdifReader reader = new LdifReader();
        List<LdifEntry> entries = reader.parseLdif( ldif );

        for ( LdifEntry entry : entries )
        {
            rootDSE.createSubcontext( new LdapDN( entry.getDn() ), entry.getAttributes() );
        }
    }
    
    
}
