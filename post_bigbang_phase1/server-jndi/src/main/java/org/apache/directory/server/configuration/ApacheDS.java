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
package org.apache.directory.server.configuration;


import org.apache.commons.lang.StringUtils;
import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.store.LdifLoadFilter;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Apache Directory Server top level.
 *
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ApacheDS
{
    private static final String WINDOWSFILE_ATTR = "windowsFilePath";
    private static final String UNIXFILE_ATTR = "unixFilePath";
    private static final Logger LOG = LoggerFactory.getLogger( ApacheDS.class.getName() );
    private static final String LDIF_FILES_DN = "ou=loadedLdifFiles,ou=configuration,ou=system";
    private static final long DEFAULT_SYNC_PERIOD_MILLIS = 20000;

    private long synchPeriodMillis = DEFAULT_SYNC_PERIOD_MILLIS;

    private File ldifDirectory;
    private final List<LdifLoadFilter> ldifFilters = new ArrayList<LdifLoadFilter>();

    private final LdapServer ldapServer;
    private final LdapServer ldapsServer;
    private final DirectoryService directoryService;


    public ApacheDS( DirectoryService directoryService, LdapServer ldapServer, LdapServer ldapsServer )
    {
        this.directoryService = directoryService == null? new DefaultDirectoryService(): directoryService;
        this.ldapServer = ldapServer;
        this.ldapsServer = ldapsServer;
        ByteBuffer.setAllocator( new SimpleByteBufferAllocator() );
        ByteBuffer.setUseDirectBuffers( false );
    }


    public void startup() throws NamingException, IOException
    {

        if ( ! directoryService.isStarted() )
        {
            directoryService.startup();
        }

        loadLdifs();

        if ( ldapServer != null && ! ldapServer.isStarted() )
        {
            ldapServer.start();
        }

        if ( ldapsServer != null && ! ldapsServer.isStarted() )
        {
            ldapsServer.start();
        }
    }


    public boolean isStarted()
    {
        if ( ldapServer != null || ldapsServer != null )
        {
             return ( ldapServer != null && ldapServer.isStarted() )
                     || ( ldapsServer != null && ldapsServer.isStarted() );
        }
        
        return directoryService.isStarted();
    }
    

    public void shutdown() throws NamingException
    {
        if ( ldapServer != null && ldapServer.isStarted() )
        {
            ldapServer.stop();
        }

        if ( ldapsServer != null && ldapsServer.isStarted() )
        {
            ldapsServer.stop();
        }

        directoryService.shutdown();
    }


    public LdapServer getLdapServer()
    {
        return ldapServer;
    }


    public LdapServer getLdapsServer()
    {
        return ldapsServer;
    }


    public DirectoryService getDirectoryService()
    {
        return directoryService;
    }


    public long getSynchPeriodMillis()
    {
        return synchPeriodMillis;
    }


    public void setSynchPeriodMillis( long synchPeriodMillis )
    {
        this.synchPeriodMillis = synchPeriodMillis;
    }


    public File getLdifDirectory()
    {
        if ( ldifDirectory == null )
        {
            return null;
        }
        else if ( ldifDirectory.isAbsolute() )
        {
            return this.ldifDirectory;
        }
        else
        {
            return new File( directoryService.getWorkingDirectory().getParent() , ldifDirectory.toString() );
        }
    }


    public void setAllowAnonymousAccess( boolean allowAnonymousAccess )
    {
        this.directoryService.setAllowAnonymousAccess( allowAnonymousAccess );
        if ( ldapServer != null )
        {
            this.ldapServer.setAllowAnonymousAccess( allowAnonymousAccess );
        }
        if ( ldapsServer != null )
        {
            this.ldapsServer.setAllowAnonymousAccess( allowAnonymousAccess );
        }
    }


    public void setLdifDirectory( File ldifDirectory )
    {
        this.ldifDirectory = ldifDirectory;
    }


    public List<LdifLoadFilter> getLdifFilters()
    {
        return new ArrayList<LdifLoadFilter>( ldifFilters );
    }


    protected void setLdifFilters( List<LdifLoadFilter> filters )
    {
        this.ldifFilters.clear();
        this.ldifFilters.addAll( filters );
    }


    // ----------------------------------------------------------------------
    // From CoreContextFactory: presently in intermediate step but these
    // methods will be moved to the appropriate protocol service eventually.
    // This is here simply to start to remove the JNDI dependency then further
    // refactoring will be needed to place these where they belong.
    // ----------------------------------------------------------------------


    private void ensureLdifFileBase( DirContext root )
    {
        Attributes entry = new AttributesImpl( SchemaConstants.OU_AT, "loadedLdifFiles", true );
        entry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC );
        entry.get( SchemaConstants.OBJECT_CLASS_AT ).add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );

        try
        {
            root.createSubcontext( LDIF_FILES_DN, entry );
            LOG.info( "Creating " + LDIF_FILES_DN );
        }
        catch ( NamingException e )
        {
            LOG.info( LDIF_FILES_DN + " exists" );
        }
    }


    private String buildProtectedFileEntry( File ldif )
    {
        StringBuffer buf = new StringBuffer();

        buf.append( File.separatorChar == '\\' ? WINDOWSFILE_ATTR : UNIXFILE_ATTR );
        buf.append( "=" );

        buf.append( StringTools.dumpHexPairs( StringTools.getBytesUtf8( getCanonical( ldif ) ) ) );

        buf.append( "," );
        buf.append( LDIF_FILES_DN );

        return buf.toString();
    }

    
    private void addFileEntry( DirContext root, File ldif ) throws NamingException
    {
		String rdnAttr = File.separatorChar == '\\' ? WINDOWSFILE_ATTR : UNIXFILE_ATTR;
        String oc = File.separatorChar == '\\' ? ApacheSchemaConstants.WINDOWS_FILE_OC : ApacheSchemaConstants.UNIX_FILE_OC;

        Attributes entry = new AttributesImpl( rdnAttr, getCanonical( ldif ), true );
        entry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC );
        entry.get( SchemaConstants.OBJECT_CLASS_AT ).add( oc );
        root.createSubcontext( buildProtectedFileEntry( ldif ), entry );
    }


    private Attributes getLdifFileEntry( DirContext root, File ldif )
    {
        try
        {
            return root.getAttributes( buildProtectedFileEntry( ldif ), new String[]
                { SchemaConstants.CREATE_TIMESTAMP_AT } );
        }
        catch ( NamingException e )
        {
            return null;
        }
    }


    private String getCanonical( File file )
    {
        String canonical;

        try
        {
            canonical = file.getCanonicalPath();
        }
        catch ( IOException e )
        {
            LOG.error( "could not get canonical path", e );
            return null;
        }

        return StringUtils.replace( canonical, "\\", "\\\\" );
    }


    private void loadLdifs() throws NamingException
    {
        // LOG and bail if property not set
        if ( ldifDirectory == null )
        {
            LOG.info( "LDIF load directory not specified.  No LDIF files will be loaded." );
            return;
        }

        // LOG and bail if LDIF directory does not exists
        if ( ! ldifDirectory.exists() )
        {
            LOG.warn( "LDIF load directory '" + getCanonical( ldifDirectory )
                + "' does not exist.  No LDIF files will be loaded." );
            return;
        }


        LdapPrincipal admin = new LdapPrincipal( new LdapDN( ServerDNConstants.ADMIN_SYSTEM_DN ),
                AuthenticationLevel.STRONG );
        DirContext root = directoryService.getJndiContext( admin );
        ensureLdifFileBase( root );

        // if ldif directory is a file try to load it
        if ( ! ldifDirectory.isDirectory() )
        {
            if ( LOG.isInfoEnabled() )
            {
                LOG.info( "LDIF load directory '" + getCanonical( ldifDirectory )
                    + "' is a file.  Will attempt to load as LDIF." );
            }

            Attributes fileEntry = getLdifFileEntry( root, ldifDirectory );

            if ( fileEntry != null )
            {
                String time = ( String ) fileEntry.get( SchemaConstants.CREATE_TIMESTAMP_AT ).get();

                if ( LOG.isInfoEnabled() )
                {
                    LOG.info( "Load of LDIF file '" + getCanonical( ldifDirectory )
                        + "' skipped.  It has already been loaded on " + time + "." );
                }

                return;
            }

            LdifFileLoader loader = new LdifFileLoader( root, ldifDirectory, ldifFilters );
            loader.execute();

            addFileEntry( root, ldifDirectory );
            return;
        }

        // get all the ldif files within the directory (should be sorted alphabetically)
        File[] ldifFiles = ldifDirectory.listFiles( new FileFilter()
        {
            public boolean accept( File pathname )
            {
                boolean isLdif = pathname.getName().toLowerCase().endsWith( ".ldif" );
                return pathname.isFile() && pathname.canRead() && isLdif;
            }
        } );

        // LOG and bail if we could not find any LDIF files
        if ( ldifFiles == null || ldifFiles.length == 0 )
        {
            LOG.warn( "LDIF load directory '" + getCanonical( ldifDirectory )
                + "' does not contain any LDIF files.  No LDIF files will be loaded." );
            return;
        }

        // load all the ldif files and load each one that is loaded
        for ( File ldifFile : ldifFiles )
        {
            Attributes fileEntry = getLdifFileEntry( root, ldifFile );

            if ( fileEntry != null )
            {
                String time = ( String ) fileEntry.get( SchemaConstants.CREATE_TIMESTAMP_AT ).get();
                LOG.info( "Load of LDIF file '" + getCanonical( ldifFile )
                        + "' skipped.  It has already been loaded on " + time + "." );
                continue;
            }

            LdifFileLoader loader = new LdifFileLoader( root, ldifFile, ldifFilters );
            int count = loader.execute();
            LOG.info( "Loaded " + count + " entries from LDIF file '" + getCanonical( ldifFile ) + "'" );
            addFileEntry( root, ldifFile );
        }
    }
}
