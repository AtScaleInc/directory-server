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
package org.apache.directory.server.core.partition.impl.btree.jdbm;


import jdbm.RecordManager;
import jdbm.helper.MRU;
import jdbm.recman.BaseRecordManager;
import jdbm.recman.CacheRecordManager;

import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.entry.ServerStringValue;
import org.apache.directory.server.core.partition.Oid;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.IndexAssertion;
import org.apache.directory.server.core.partition.impl.btree.IndexAssertionEnumeration;
import org.apache.directory.server.core.partition.impl.btree.IndexNotFoundException;
import org.apache.directory.server.core.partition.impl.btree.IndexRecord;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.AttributeTypeAndValue;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.NamespaceTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class JdbmStore
{
    /** static logger */
    private static final Logger LOG = LoggerFactory.getLogger( JdbmStore.class );
    /** The default cache size is set to 10 000 objects */
    private static final int DEFAULT_CACHE_SIZE = 10000;

    /** the JDBM record manager used by this database */
    private RecordManager recMan;
    /** the normalized suffix DN of this backend database */
    private LdapDN normSuffix;
    /** the user provided suffix DN of this backend database */
    private LdapDN upSuffix;
    /** the working directory to use for files */
    private File workingDirectory;
    /** the master table storing entries by primary key */
    private JdbmMasterTable master;
    /** a map of attributeType numeric ids to user userIndices */
    private Map<String, JdbmIndex> userIndices = new HashMap<String, JdbmIndex>();
    /** a map of attributeType numeric ids to system userIndices */
    private Map<String, JdbmIndex> systemIndices = new HashMap<String, JdbmIndex>();
    /** true if initialized */
    private boolean initialized;
    /** true if we sync disks on every write operation */
    private boolean isSyncOnWrite = true;

    /** the normalized distinguished name index */
    private JdbmIndex ndnIdx;
    /** the user provided distinguished name index */
    private JdbmIndex updnIdx;
    /** the attribute existance index */
    private JdbmIndex existanceIdx;
    /** the parent child relationship index */
    private JdbmIndex hierarchyIdx;
    /** the one level scope alias index */
    private JdbmIndex oneAliasIdx;
    /** the subtree scope alias index */
    private JdbmIndex subAliasIdx;
    /** a system index on aliasedObjectName attribute */
    private JdbmIndex aliasIdx;

    /** Two static declaration to avoid lookup all over the code */
    private static AttributeType OBJECT_CLASS_AT;
    private static AttributeType ALIASED_OBJECT_NAME_AT;

    /** A pointer on the global registries */
    private Registries registries;

    /** A pointer on the AT registry */
    private AttributeTypeRegistry attributeTypeRegistry;

    /** A pointer on the OID registry */
    private OidRegistry oidRegistry;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a store based on JDBM B+Trees.
     */
    public JdbmStore()
    {
    }

    // -----------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    private ServerEntry contextEntry;
    private String suffixDn;
    private boolean enableOptimizer;
    private int cacheSize = DEFAULT_CACHE_SIZE;
    private String name;


    private void protect( String property )
    {
        if ( initialized )
        {
            throw new IllegalStateException( "Cannot set jdbm store property " + property + " after initialization." );
        }
    }


    public void setWorkingDirectory( File workingDirectory )
    {
        protect( "workingDirectory" );
        this.workingDirectory = workingDirectory;
    }


    public File getWorkingDirectory()
    {
        return workingDirectory;
    }


    public void setUserIndices( Set<JdbmIndex> userIndices )
    {
        protect( "userIndices" );
        for ( JdbmIndex index : userIndices )
        {
            this.userIndices.put( index.getAttributeId(), index );
        }
    }


    public Set<JdbmIndex> getUserIndices()
    {
        return new HashSet<JdbmIndex>( userIndices.values() );
    }


    public void setContextEntry( ServerEntry contextEntry )
    {
        protect( "contextEntry" );
        this.contextEntry = contextEntry;
    }


    public ServerEntry getContextEntry()
    {
        return contextEntry;
    }


    public void setSuffixDn( String suffixDn )
    {
        protect( "suffixDn" );
        this.suffixDn = suffixDn;
    }


    public String getSuffixDn()
    {
        return suffixDn;
    }


    public void setSyncOnWrite( boolean isSyncOnWrite )
    {
        protect( "syncOnWrite" );
        this.isSyncOnWrite = isSyncOnWrite;
    }


    public boolean isSyncOnWrite()
    {
        return isSyncOnWrite;
    }


    public void setEnableOptimizer( boolean enableOptimizer )
    {
        protect( "enableOptimizer" );
        this.enableOptimizer = enableOptimizer;
    }


    public boolean isEnableOptimizer()
    {
        return enableOptimizer;
    }


    public void setCacheSize( int cacheSize )
    {
        protect( "cacheSize" );
        this.cacheSize = cacheSize;
    }


    public int getCacheSize()
    {
        return cacheSize;
    }


    public void setName( String name )
    {
        protect( "name" );
        this.name = name;
    }


    public String getName()
    {
        return name;
    }


    // -----------------------------------------------------------------------
    // E N D   C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    /**
     * Initialize the JDBM storage system.
     *
     * @param oidRegistry an OID registry to resolve numeric identifiers from names
     * @param attributeTypeRegistry an attributeType specification registry to lookup type specs
     * @throws NamingException on failure to create proper database files
     */
    public synchronized void init( Registries registries ) throws NamingException
    {
        this.registries = registries;
        this.oidRegistry = registries.getOidRegistry();
        this.attributeTypeRegistry = registries.getAttributeTypeRegistry();

        OBJECT_CLASS_AT = attributeTypeRegistry.lookup( SchemaConstants.OBJECT_CLASS_AT );
        ALIASED_OBJECT_NAME_AT = attributeTypeRegistry.lookup( SchemaConstants.ALIASED_OBJECT_NAME_AT );

        this.upSuffix = new LdapDN( suffixDn );
        this.normSuffix = LdapDN.normalize( upSuffix, attributeTypeRegistry.getNormalizerMapping() );
        workingDirectory.mkdirs();

        try
        {
            // First, check if the file storing the data exists
            String path = workingDirectory.getPath() + File.separator + "master";
            BaseRecordManager base = new BaseRecordManager( path );
            base.disableTransactions();

            if ( cacheSize < 0 )
            {
                cacheSize = DEFAULT_CACHE_SIZE;

                if ( LOG.isDebugEnabled() )
                {
                    LOG.debug( "Using the default entry cache size of {} for {} partition", cacheSize, name );
                }
            }
            else
            {
                if ( LOG.isDebugEnabled() )
                {
                    LOG.debug( "Using the custom configured cache size of {} for {} partition", cacheSize, name );
                }
            }

            // Now, create the entry cache for this partition
            recMan = new CacheRecordManager( base, new MRU( cacheSize ) );
        }
        catch ( IOException e )
        {
            NamingException ne = new NamingException( "Could not initialize RecordManager" );
            ne.setRootCause( e );
            throw ne;
        }

        // Create the master table (the table wcontaining all the entries)
        master = new JdbmMasterTable( recMan, registries );

        // -------------------------------------------------------------------
        // Initializes the user and system indices
        // -------------------------------------------------------------------

        setupSystemIndices();
        setupUserIndices();

        contextEntry.getDn().normalize( attributeTypeRegistry.getNormalizerMapping() );

        initSuffixEntry3( suffixDn, contextEntry );

        // We are done !
        initialized = true;
    }


    private void setupSystemIndices() throws NamingException
    {
        if ( systemIndices.size() > 0 )
        {
            HashMap<String, JdbmIndex> tmp = new HashMap<String, JdbmIndex>();
            for ( JdbmIndex index : systemIndices.values() )
            {
                String oid = oidRegistry.getOid( index.getAttributeId() );
                tmp.put( oid, index );
                index.init( attributeTypeRegistry.lookup( oid ), workingDirectory );
            }
            systemIndices = tmp;
        }

        if ( ndnIdx == null )
        {
            ndnIdx = new JdbmIndex();
            ndnIdx.setAttributeId( Oid.NDN );
            systemIndices.put( Oid.NDN, ndnIdx );
            ndnIdx.init( attributeTypeRegistry.lookup( Oid.NDN ), workingDirectory );
        }

        if ( updnIdx == null )
        {
            updnIdx = new JdbmIndex();
            updnIdx.setAttributeId( Oid.UPDN );
            systemIndices.put( Oid.UPDN, updnIdx );
            updnIdx.init( attributeTypeRegistry.lookup( Oid.UPDN ), workingDirectory );
        }

        if ( existanceIdx == null )
        {
            existanceIdx = new JdbmIndex();
            existanceIdx.setAttributeId( Oid.EXISTANCE );
            systemIndices.put( Oid.EXISTANCE, existanceIdx );
            existanceIdx.init( attributeTypeRegistry.lookup( Oid.EXISTANCE ), workingDirectory );
        }

        if ( hierarchyIdx == null )
        {
            hierarchyIdx = new JdbmIndex();
            hierarchyIdx.setAttributeId( Oid.HIERARCHY );
            systemIndices.put( Oid.HIERARCHY, hierarchyIdx );
            hierarchyIdx.init( attributeTypeRegistry.lookup( Oid.HIERARCHY ), workingDirectory );
        }

        if ( oneAliasIdx == null )
        {
            oneAliasIdx = new JdbmIndex();
            oneAliasIdx.setAttributeId( Oid.ONEALIAS );
            systemIndices.put( Oid.ONEALIAS, oneAliasIdx );
            oneAliasIdx.init( attributeTypeRegistry.lookup( Oid.ONEALIAS ), workingDirectory );
        }

        if ( subAliasIdx == null )
        {
            subAliasIdx = new JdbmIndex();
            subAliasIdx.setAttributeId( Oid.SUBALIAS );
            systemIndices.put( Oid.SUBALIAS, subAliasIdx );
            subAliasIdx.init( attributeTypeRegistry.lookup( Oid.SUBALIAS ), workingDirectory );
        }

        if ( aliasIdx == null )
        {
            aliasIdx = new JdbmIndex();
            aliasIdx.setAttributeId( Oid.ALIAS );
            systemIndices.put( Oid.ALIAS, aliasIdx );
            aliasIdx.init( attributeTypeRegistry.lookup( Oid.ALIAS ), workingDirectory );
        }
    }


    private void setupUserIndices() throws NamingException
    {
        if ( userIndices != null && userIndices.size() > 0 )
        {
            HashMap<String, JdbmIndex> tmp = new HashMap<String, JdbmIndex>();
            for ( JdbmIndex index : userIndices.values() )
            {
                String oid = oidRegistry.getOid( index.getAttributeId() );
                tmp.put( oid, index );
                index.init( attributeTypeRegistry.lookup( oid ), workingDirectory );
            }
            userIndices = tmp;
        }
        else
        {
            userIndices = new HashMap<String, JdbmIndex>();
        }
    }


    /**
     * Called last (4th) to check if the suffix entry has been created on disk,
     * and if not it is created.
     *  
     * @param suffix the suffix for the store
     * @param entry the root entry of the store
     * @throws NamingException on failure to add the root entry
     */
    protected void initSuffixEntry3( String suffix, ServerEntry entry ) throws NamingException
    {
        // add entry for context, if it does not exist
        ServerEntry suffixOnDisk = getSuffixEntry();

        if ( suffixOnDisk == null )
        {
            LdapDN dn = new LdapDN( suffix );
            LdapDN normalizedSuffix = LdapDN.normalize( dn, attributeTypeRegistry.getNormalizerMapping() );

            add( normalizedSuffix, entry );
        }
    }


    /**
     * Close the parttion : we have to close all the userIndices and the master table.
     */
    public synchronized void destroy()
    {
        LOG.debug( "destroy() called on store for {}", this.suffixDn );

        if ( !initialized )
        {
            return;
        }

        List<JdbmIndex> array = new ArrayList<JdbmIndex>();
        array.addAll( userIndices.values() );

        if ( null != ndnIdx )
        {
            array.add( ndnIdx );
        }

        if ( null != updnIdx )
        {
            array.add( updnIdx );
        }

        if ( null != aliasIdx )
        {
            array.add( aliasIdx );
        }

        if ( null != oneAliasIdx )
        {
            array.add( oneAliasIdx );
        }

        if ( null != subAliasIdx )
        {
            array.add( subAliasIdx );
        }

        if ( null != hierarchyIdx )
        {
            array.add( hierarchyIdx );
        }

        if ( null != existanceIdx )
        {
            array.add( existanceIdx );
        }

        for ( JdbmIndex index : array )
        {
            try
            {
                index.close();
                LOG.debug( "Closed {} index for {} partition.", index.getAttributeId(), suffixDn );
            }
            catch ( Throwable t )
            {
                LOG.error( "Failed to close an index.", t );
            }
        }

        try
        {
            master.close();
            LOG.debug( "Closed master table for {} partition.", suffixDn );
        }
        catch ( Throwable t )
        {
            LOG.error( "Failed to close the master.", t );
        }

        try
        {
            recMan.close();
            LOG.debug( "Closed record manager for {} partition.", suffixDn );
        }
        catch ( Throwable t )
        {
            LOG.error( "Failed to close the record manager", t );
        }

        initialized = false;
    }


    /**
     * Gets whether the store is initialized.
     *
     * @return true if the partition store is initialized
     */
    public boolean isInitialized()
    {
        return initialized;
    }


    /**
     * This method is called when the synch thread is waking up, to write
     * the modified data.
     * 
     * @throws NamingException on failures to sync to disk
     */
    public synchronized void sync() throws NamingException
    {
        if ( !initialized )
        {
            return;
        }

        List<Index> array = new ArrayList<Index>();
        array.addAll( userIndices.values() );
        array.add( ndnIdx );
        array.add( updnIdx );
        array.add( aliasIdx );
        array.add( oneAliasIdx );
        array.add( subAliasIdx );
        array.add( hierarchyIdx );
        array.add( existanceIdx );

        // Sync all user defined userIndices
        for ( Index idx : array )
        {
            idx.sync();
        }

        master.sync();

        try
        {
            recMan.commit();
        }
        catch ( Throwable t )
        {
            throw ( NamingException ) new NamingException( "Failed to commit changes to the record manager." )
                .initCause( t );
        }
    }


    // ------------------------------------------------------------------------
    // I N D E X   M E T H O D S
    // ------------------------------------------------------------------------

    public void addIndex( JdbmIndex index ) throws NamingException
    {
        userIndices.put( index.getAttributeId(), index );
    }


    public JdbmIndex getExistanceIndex()
    {
        return existanceIdx;
    }


    public void setExistanceIndex( JdbmIndex index ) throws NamingException
    {
        protect( "existanceIndex" );
        existanceIdx = index;
        systemIndices.put( index.getAttributeId(), existanceIdx );
    }


    public JdbmIndex getHierarchyIndex()
    {
        return hierarchyIdx;
    }


    public void setHierarchyIndex( JdbmIndex index ) throws NamingException
    {
        protect( "hierarchyIndex" );
        hierarchyIdx = index;
        systemIndices.put( index.getAttributeId(), hierarchyIdx );
    }


    public JdbmIndex getAliasIndex()
    {
        return aliasIdx;
    }


    public void setAliasIndex( JdbmIndex index ) throws NamingException
    {
        protect( "aliasIndex" );
        aliasIdx = index;
        systemIndices.put( index.getAttributeId(), aliasIdx );
    }


    public JdbmIndex getOneAliasIndex()
    {
        return oneAliasIdx;
    }


    public void setOneAliasIndex( JdbmIndex index ) throws NamingException
    {
        protect( "oneAliasIndex" );
        oneAliasIdx = index;
        systemIndices.put( index.getAttributeId(), oneAliasIdx );
    }


    public JdbmIndex getSubAliasIndex()
    {
        return subAliasIdx;
    }


    public void setSubAliasIndex( JdbmIndex index ) throws NamingException
    {
        protect( "subAliasIndex" );
        subAliasIdx = index;
        systemIndices.put( index.getAttributeId(), subAliasIdx );
    }


    public JdbmIndex getUpdnIndex()
    {
        return updnIdx;
    }


    public void setUpdnIndex( JdbmIndex index ) throws NamingException
    {
        protect( "updnIndex" );
        updnIdx = index;
        systemIndices.put( index.getAttributeId(), updnIdx );
    }


    public JdbmIndex getNdnIndex()
    {
        return ndnIdx;
    }


    public void setNdnIndex( JdbmIndex index ) throws NamingException
    {
        protect( "ndnIndex" );
        ndnIdx = index;
        systemIndices.put( index.getAttributeId(), ndnIdx );
    }


    public Iterator<String> userIndices()
    {
        return userIndices.keySet().iterator();
    }


    public Iterator<String> systemIndices()
    {
        return systemIndices.keySet().iterator();
    }


    public boolean hasUserIndexOn( String id ) throws NamingException
    {
        return userIndices.containsKey( oidRegistry.getOid( id ) );
    }


    public boolean hasSystemIndexOn( String id ) throws NamingException
    {
        return systemIndices.containsKey( oidRegistry.getOid( id ) );
    }


    public JdbmIndex getUserIndex( String id ) throws IndexNotFoundException
    {
        try
        {
            id = oidRegistry.getOid( id );
        }
        catch ( NamingException e )
        {
            LOG.error( "Failed to identify OID for: " + id, e );
            throw new IndexNotFoundException( "Failed to identify OID for: " + id, id, e );
        }

        if ( userIndices.containsKey( id ) )
        {
            return userIndices.get( id );
        }
        else
        {
            String name;

            try
            {
                name = oidRegistry.getPrimaryName( id );
            }
            catch ( NamingException e )
            {
                String msg = "Failed to resolve primary name for " + id + " in user index lookup";
                LOG.error( msg, e );
                throw new IndexNotFoundException( msg, id, e );
            }

            throw new IndexNotFoundException( "A user index on attribute " + id + " (" + name + ") does not exist!" );
        }
    }


    public JdbmIndex getSystemIndex( String id ) throws IndexNotFoundException
    {
        try
        {
            id = oidRegistry.getOid( id );
        }
        catch ( NamingException e )
        {
            LOG.error( "Failed to identify OID for: " + id, e );
            throw new IndexNotFoundException( "Failed to identify OID for: " + id, id, e );
        }

        if ( systemIndices.containsKey( id ) )
        {
            return systemIndices.get( id );
        }
        else
        {
            String name;

            try
            {
                name = oidRegistry.getPrimaryName( id );
            }
            catch ( NamingException e )
            {
                String msg = "Failed to resolve primary name for " + id + " in user index lookup";
                LOG.error( msg, e );
                throw new IndexNotFoundException( msg, id, e );
            }

            throw new IndexNotFoundException( "A system index on attribute " + id + " (" + name + ") does not exist!" );
        }
    }


    public Long getEntryId( String dn ) throws NamingException
    {
        return ndnIdx.forwardLookup( dn );
    }


    public String getEntryDn( Long id ) throws NamingException
    {
        return ( String ) ndnIdx.reverseLookup( id );
    }


    public Long getParentId( String dn ) throws NamingException
    {
        Long childId = ndnIdx.forwardLookup( dn );
        return ( Long ) hierarchyIdx.reverseLookup( childId );
    }


    public Long getParentId( Long childId ) throws NamingException
    {
        return ( Long ) hierarchyIdx.reverseLookup( childId );
    }


    public String getEntryUpdn( Long id ) throws NamingException
    {
        return ( String ) updnIdx.reverseLookup( id );
    }


    public String getEntryUpdn( String dn ) throws NamingException
    {
        Long id = ndnIdx.forwardLookup( dn );
        return ( String ) updnIdx.reverseLookup( id );
    }


    public int count() throws NamingException
    {
        return master.count();
    }


    /**
     * Removes the index entries for an alias before the entry is deleted from
     * the master table.
     * 
     * @todo Optimize this by walking the hierarchy index instead of the name 
     * @param aliasId the id of the alias entry in the master table
     * @throws NamingException if we cannot delete the userIndices
     */
    private void dropAliasIndices( Long aliasId ) throws NamingException
    {
        String targetDn = ( String ) aliasIdx.reverseLookup( aliasId );
        Long targetId = getEntryId( targetDn );
        String aliasDn = getEntryDn( aliasId );
        LdapDN ancestorDn = ( LdapDN ) new LdapDN( aliasDn ).getPrefix( 1 );
        Long ancestorId = getEntryId( ancestorDn.toString() );

        /*
         * We cannot just drop all tuples in the one level and subtree userIndices
         * linking baseIds to the targetId.  If more than one alias refers to
         * the target then droping all tuples with a value of targetId would
         * make all other aliases to the target inconsistent.
         * 
         * We need to walk up the path of alias ancestors until we reach the 
         * upSuffix, deleting each ( ancestorId, targetId ) tuple in the
         * subtree scope alias.  We only need to do this for the direct parent
         * of the alias on the one level subtree.
         */
        oneAliasIdx.drop( ancestorId, targetId );
        subAliasIdx.drop( ancestorId, targetId );

        while ( !ancestorDn.equals( normSuffix ) )
        {
            ancestorDn = ( LdapDN ) ancestorDn.getPrefix( 1 );
            ancestorId = getEntryId( ancestorDn.toString() );

            subAliasIdx.drop( ancestorId, targetId );
        }

        // Drops all alias tuples pointing to the id of the alias to be deleted
        aliasIdx.drop( aliasId );
    }


    /**
     * Adds userIndices for an aliasEntry to be added to the database while checking
     * for constrained alias constructs like alias cycles and chaining.
     * 
     * @param aliasDn normalized distinguished name for the alias entry
     * @param aliasTarget the user provided aliased entry dn as a string
     * @param aliasId the id of alias entry to add
     * @throws NamingException if index addition fails, of the alias is not 
     * allowed due to chaining or cycle formation.
     */
    private void addAliasIndices( Long aliasId, LdapDN aliasDn, String aliasTarget ) throws NamingException
    {
        LdapDN normalizedAliasTargetDn; // Name value of aliasedObjectName
        Long targetId; // Id of the aliasedObjectName
        LdapDN ancestorDn; // Name of an alias entry relative
        Long ancestorId; // Id of an alias entry relative

        // Access aliasedObjectName, normalize it and generate the Name 
        normalizedAliasTargetDn = new LdapDN( aliasTarget );
        normalizedAliasTargetDn.normalize( attributeTypeRegistry.getNormalizerMapping() );

        /*
         * Check For Cycles
         * 
         * Before wasting time to lookup more values we check using the target
         * dn to see if we have the possible formation of an alias cycle.  This
         * happens when the alias refers back to a target that is also a 
         * relative of the alias entry.  For detection we test if the aliased
         * entry Dn starts with the target Dn.  If it does then we know the 
         * aliased target is a relative and we have a perspecitive cycle.
         */
        if ( aliasDn.startsWith( normalizedAliasTargetDn ) )
        {
            if ( aliasDn.equals( normalizedAliasTargetDn ) )
            {
                throw new NamingException( "[36] aliasDereferencingProblem - " + "attempt to create alias to itself." );
            }

            throw new NamingException( "[36] aliasDereferencingProblem - "
                + "attempt to create alias with cycle to relative " + aliasTarget
                + " not allowed from descendent alias " + aliasDn );
        }

        /*
         * Check For Aliases External To Naming Context
         * 
         * id may be null but the alias may be to a valid entry in 
         * another namingContext.  Such aliases are not allowed and we
         * need to point it out to the user instead of saying the target
         * does not exist when it potentially could outside of this upSuffix.
         */
        if ( !normalizedAliasTargetDn.startsWith( normSuffix ) )
        {
            // Complain specifically about aliases to outside naming contexts
            throw new NamingException( "[36] aliasDereferencingProblem -"
                + " the alias points to an entry outside of the " + upSuffix.getUpName()
                + " namingContext to an object whose existance cannot be" + " determined." );
        }

        // L O O K U P   T A R G E T   I D
        targetId = ndnIdx.forwardLookup( normalizedAliasTargetDn.toNormName() );

        /*
         * Check For Target Existance
         * 
         * We do not allow the creation of inconsistant aliases.  Aliases should
         * not be broken links.  If the target does not exist we start screaming
         */
        if ( null == targetId )
        {
            // Complain about target not existing
            throw new NamingException( "[33] aliasProblem - "
                + "the alias when dereferenced would not name a known object "
                + "the aliasedObjectName must be set to a valid existing " + "entry." );
        }

        /*
         * Detect Direct Alias Chain Creation
         * 
         * Rather than resusitate the target to test if it is an alias and fail
         * due to chaing creation we use the alias index to determine if the
         * target is an alias.  Hence if the alias we are about to create points
         * to another alias as its target in the aliasedObjectName attribute, 
         * then we have a situation where an alias chain is being created.  
         * Alias chaining is not allowed so we throw and exception. 
         */
        if ( null != aliasIdx.reverseLookup( targetId ) )
        {
            // Complain about illegal alias chain
            throw new NamingException( "[36] aliasDereferencingProblem -"
                + " the alias points to another alias.  Alias chaining is" + " not supported by this backend." );
        }

        // Add the alias to the simple alias index
        aliasIdx.add( normalizedAliasTargetDn.getNormName(), aliasId );

        /*
         * Handle One Level Scope Alias Index
         * 
         * The first relative is special with respect to the one level alias
         * index.  If the target is not a sibling of the alias then we add the
         * index entry maping the parent's id to the aliased target id.
         */
        ancestorDn = ( LdapDN ) aliasDn.clone();
        ancestorDn.remove( aliasDn.size() - 1 );
        ancestorId = getEntryId( ancestorDn.toNormName() );

        if ( !NamespaceTools.isSibling( normalizedAliasTargetDn, aliasDn ) )
        {
            oneAliasIdx.add( ancestorId, targetId );
        }

        /*
         * Handle Sub Level Scope Alias Index
         * 
         * Walk the list of relatives from the parents up to the upSuffix, testing
         * to see if the alias' target is a descendant of the relative.  If the
         * alias target is not a descentant of the relative it extends the scope
         * and is added to the sub tree scope alias index.  The upSuffix node is
         * ignored since everything is under its scope.  The first loop 
         * iteration shall handle the parents.
         */
        while ( !ancestorDn.equals( normSuffix ) && null != ancestorId )
        {
            if ( !NamespaceTools.isDescendant( ancestorDn, normalizedAliasTargetDn ) )
            {
                subAliasIdx.add( ancestorId, targetId );
            }

            ancestorDn.remove( ancestorDn.size() - 1 );
            ancestorId = getEntryId( ancestorDn.toNormName() );
        }
    }


    public void add( LdapDN normName, ServerEntry entry ) throws NamingException
    {
        Long id;
        Long parentId;

        id = master.getNextId();

        //
        // Suffix entry cannot have a parent since it is the root so it is 
        // capped off using the zero value which no entry can have since 
        // entry sequences start at 1.
        //

        LdapDN parentDn = null;

        if ( normName.equals( normSuffix ) )
        {
            parentId = 0L;
        }
        else
        {
            parentDn = ( LdapDN ) normName.clone();
            parentDn.remove( parentDn.size() - 1 );
            parentId = getEntryId( parentDn.toString() );
        }

        // don't keep going if we cannot find the parent Id
        if ( parentId == null )
        {
            throw new LdapNameNotFoundException( "Id for parent '" + parentDn + "' not found!" );
        }

        EntryAttribute objectClass = entry.get( OBJECT_CLASS_AT );

        if ( objectClass == null )
        {
            String msg = "Entry " + normName.getUpName() + " contains no objectClass attribute: " + entry;
            throw new LdapSchemaViolationException( msg, ResultCodeEnum.OBJECT_CLASS_VIOLATION );
        }

        // Start adding the system userIndices
        // Why bother doing a lookup if this is not an alias.

        if ( objectClass.contains( SchemaConstants.ALIAS_OC ) )
        {
            EntryAttribute aliasAttr = entry.get( ALIASED_OBJECT_NAME_AT );
            addAliasIndices( id, normName, aliasAttr.getString() );
        }

        if ( !Character.isDigit( normName.toNormName().charAt( 0 ) ) )
        {
            throw new IllegalStateException( "Not a normalized name: " + normName.toNormName() );
        }

        ndnIdx.add( normName.toNormName(), id );
        updnIdx.add( normName.getUpName(), id );
        hierarchyIdx.add( parentId, id );

        // Now work on the user defined userIndices
        for ( EntryAttribute attribute : entry )
        {
            String attributeOid = ( ( ServerAttribute ) attribute ).getAttributeType().getOid();

            if ( hasUserIndexOn( attributeOid ) )
            {
                Index idx = getUserIndex( attributeOid );

                // here lookup by attributeId is ok since we got attributeId from 
                // the entry via the enumeration - it's in there as is for sure

                for ( Value<?> value : attribute )
                {
                    idx.add( value.get(), id );
                }

                // Adds only those attributes that are indexed
                existanceIdx.add( attributeOid, id );
            }
        }

        master.put( entry, id );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    public ServerEntry lookup( Long id ) throws NamingException
    {
        return master.get( id );
    }


    public void delete( Long id ) throws NamingException
    {
        ServerEntry entry = lookup( id );
        Long parentId = getParentId( id );

        EntryAttribute objectClass = entry.get( OBJECT_CLASS_AT );

        if ( objectClass.contains( SchemaConstants.ALIAS_OC ) )
        {
            dropAliasIndices( id );
        }

        ndnIdx.drop( id );
        updnIdx.drop( id );
        hierarchyIdx.drop( id );

        // Remove parent's reference to entry only if entry is not the upSuffix
        if ( !parentId.equals( 0L ) )
        {
            hierarchyIdx.drop( parentId, id );
        }

        for ( EntryAttribute attribute : entry )
        {
            String attributeOid = ( ( ServerAttribute ) attribute ).getAttributeType().getOid();

            if ( hasUserIndexOn( attributeOid ) )
            {
                Index index = getUserIndex( attributeOid );

                // here lookup by attributeId is ok since we got attributeId from 
                // the entry via the enumeration - it's in there as is for sure
                for ( Value<?> value : attribute )
                {
                    index.drop( value, id );
                }

                existanceIdx.drop( attributeOid, id );
            }
        }

        master.delete( id );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    public NamingEnumeration<?> list( Long id ) throws NamingException
    {
        return hierarchyIdx.listIndices( id );
    }


    public int getChildCount( Long id ) throws NamingException
    {
        return hierarchyIdx.count( id );
    }


    public LdapDN getSuffix()
    {
        return normSuffix;
    }


    public LdapDN getUpSuffix()
    {
        return upSuffix;
    }


    public ServerEntry getSuffixEntry() throws NamingException
    {
        Long id = getEntryId( normSuffix.toNormName() );

        if ( null == id )
        {
            return null;
        }

        return lookup( id );
    }


    public void setProperty( String propertyName, String propertyValue ) throws NamingException
    {
        master.setProperty( propertyName, propertyValue );
    }


    public String getProperty( String propertyName ) throws NamingException
    {
        return master.getProperty( propertyName );
    }


    public ServerEntry getIndices( Long id ) throws NamingException
    {
        ServerEntry attributes = new DefaultServerEntry( registries );

        // Get the distinguishedName to id mapping
        attributes.put( "_nDn", getEntryDn( id ) );
        attributes.put( "_upDn", getEntryUpdn( id ) );
        attributes.put( "_parent", getParentId( id ).toString() );

        // Get all standard index attribute to value mappings
        for ( Index index : this.userIndices.values() )
        {
            NamingEnumeration<IndexRecord> list = index.listReverseIndices( id );

            while ( list.hasMore() )
            {
                IndexRecord rec = list.next();
                Object val = rec.getIndexKey();
                String attrId = index.getAttribute().getName();
                EntryAttribute attr = attributes.get( attrId );

                if ( attr == null )
                {
                    attr = new DefaultServerAttribute( attributeTypeRegistry.lookup( attrId ) );
                }

                if ( val instanceof String )
                {
                    attr.add( ( String ) val );
                }
                else
                {
                    attr.add( ( byte[] ) val );
                }

                attributes.put( attr );
            }
        }

        // Get all existance mappings for this id creating a special key
        // that looks like so 'existance[attribute]' and the value is set to id
        NamingEnumeration<IndexRecord> list = existanceIdx.listReverseIndices( id );
        StringBuffer val = new StringBuffer();

        while ( list.hasMore() )
        {
            IndexRecord rec = list.next();
            val.append( "_existance[" );
            val.append( rec.getIndexKey() );
            val.append( "]" );

            String valStr = val.toString();
            EntryAttribute attr = attributes.get( valStr );

            if ( attr == null )
            {
                attr = new DefaultServerAttribute( attributeTypeRegistry.lookup( valStr ) );
            }

            Object idRec = rec.getEntryId();

            if ( idRec instanceof String )
            {
                attr.add( ( String ) idRec );
            }
            else
            {
                attr.add( ( byte[] ) idRec );
            }

            attributes.put( attr );
            val.setLength( 0 );
        }

        // Get all parent child mappings for this entry as the parent using the
        // key 'child' with many entries following it.
        list = hierarchyIdx.listIndices( id );
        EntryAttribute childAttr = new DefaultServerAttribute( attributeTypeRegistry.lookup( "_child" ) );

        attributes.put( childAttr );

        while ( list.hasMore() )
        {
            IndexRecord rec = ( IndexRecord ) list.next();

            Object idRec = rec.getEntryId();

            if ( idRec instanceof String )
            {
                childAttr.add( ( String ) idRec );
            }
            else
            {
                childAttr.add( ( byte[] ) idRec );
            }
        }

        return attributes;
    }


    /**
     * Adds a set of attribute values while affecting the appropriate userIndices.
     * The entry is not persisted: it is only changed in anticipation for a put 
     * into the master table.
     *
     * @param id the primary key of the entry
     * @param entry the entry to alter
     * @param mods the attribute and values to add 
     * @throws NamingException if index alteration or attribute addition
     * fails.
     */
    private void add( Long id, ServerEntry entry, EntryAttribute mods ) throws NamingException
    {
        String modsOid = oidRegistry.getOid( mods.getId() );

        if ( hasUserIndexOn( modsOid ) )
        {
            Index idx = getUserIndex( modsOid );
            idx.add( ServerEntryUtils.toAttributeImpl( mods ), id );

            // If the attr didn't exist for this id add it to existance index
            if ( !existanceIdx.hasValue( modsOid, id ) )
            {
                existanceIdx.add( modsOid, id );
            }
        }

        // add all the values in mods to the same attribute in the entry
        AttributeType type = attributeTypeRegistry.lookup( modsOid );

        for ( Value<?> value : mods )
        {
            entry.add( type, value );
        }

        if ( modsOid.equals( oidRegistry.getOid( SchemaConstants.ALIASED_OBJECT_NAME_AT ) ) )
        {
            String ndnStr = ( String ) ndnIdx.reverseLookup( id );
            addAliasIndices( id, new LdapDN( ndnStr ), mods.getString() );
        }
    }


    /**
     * Completely removes the set of values for an attribute having the values 
     * supplied while affecting the appropriate userIndices.  The entry is not
     * persisted: it is only changed in anticipation for a put into the master 
     * table.  Note that an empty attribute w/o values will remove all the 
     * values within the entry where as an attribute w/ values will remove those
     * attribute values it contains.
     *
     * @param id the primary key of the entry
     * @param entry the entry to alter
     * @param mods the attribute and its values to delete
     * @throws NamingException if index alteration or attribute modification 
     * fails.
     */
    private void remove( Long id, ServerEntry entry, EntryAttribute mods ) throws NamingException
    {
        String modsOid = oidRegistry.getOid( mods.getId() );

        if ( hasUserIndexOn( modsOid ) )
        {
            Index idx = getUserIndex( modsOid );
            idx.drop( ServerEntryUtils.toAttributeImpl( mods ), id );

            /* 
             * If no attribute values exist for this entryId in the index then
             * we remove the existance index entry for the removed attribute.
             */
            if ( null == idx.reverseLookup( id ) )
            {
                existanceIdx.drop( modsOid, id );
            }
        }

        AttributeType attrType = attributeTypeRegistry.lookup( modsOid );
        /*
         * If there are no attribute values in the modifications then this 
         * implies the compelete removal of the attribute from the entry. Else
         * we remove individual attribute values from the entry in mods one 
         * at a time.
         */
        if ( mods.size() == 0 )
        {
            entry.removeAttributes( attrType );
        }
        else
        {
            EntryAttribute entryAttr = entry.get( attrType );

            for ( Value<?> value : mods )
            {
                if ( value instanceof ServerStringValue )
                {
                    entryAttr.remove( ( String ) value.get() );
                }
                else
                {
                    entryAttr.remove( ( byte[] ) value.get() );
                }
            }

            // if nothing is left just remove empty attribute
            if ( entryAttr.size() == 0 )
            {
                entry.removeAttributes( entryAttr.getId() );
            }
        }

        // Aliases->single valued comp/partial attr removal is not relevant here
        if ( modsOid.equals( oidRegistry.getOid( SchemaConstants.ALIASED_OBJECT_NAME_AT ) ) )
        {
            dropAliasIndices( id );
        }
    }


    /**
     * Completely replaces the existing set of values for an attribute with the
     * modified values supplied affecting the appropriate userIndices.  The entry
     * is not persisted: it is only changed in anticipation for a put into the
     * master table.
     *
     * @param id the primary key of the entry
     * @param entry the entry to alter
     * @param mods the replacement attribute and values
     * @throws NamingException if index alteration or attribute modification 
     * fails.
     */
    private void replace( Long id, ServerEntry entry, EntryAttribute mods ) throws NamingException
    {
        String modsOid = oidRegistry.getOid( mods.getId() );

        if ( hasUserIndexOn( modsOid ) )
        {
            Index idx = getUserIndex( modsOid );

            // Drop all existing attribute value index entries and add new ones
            idx.drop( id );
            idx.add( ServerEntryUtils.toAttributeImpl( mods ), id );

            /* 
             * If no attribute values exist for this entryId in the index then
             * we remove the existance index entry for the removed attribute.
             */
            if ( null == idx.reverseLookup( id ) )
            {
                existanceIdx.drop( modsOid, id );
            }
        }

        String aliasAttributeOid = oidRegistry.getOid( SchemaConstants.ALIASED_OBJECT_NAME_AT );

        if ( modsOid.equals( aliasAttributeOid ) )
        {
            dropAliasIndices( id );
        }

        // replaces old attributes with new modified ones if they exist
        if ( mods.size() > 0 )
        {
            entry.put( mods );
        }
        else
        // removes old attributes if new replacements do not exist
        {
            entry.remove( mods );
        }

        if ( modsOid.equals( aliasAttributeOid ) && mods.size() > 0 )
        {
            String ndnStr = ( String ) ndnIdx.reverseLookup( id );
            addAliasIndices( id, new LdapDN( ndnStr ), mods.getString() );
        }
    }


    public void modify( LdapDN dn, ModificationOperation modOp, ServerEntry mods ) throws NamingException
    {
        NamingEnumeration<String> attrs;
        Long id = getEntryId( dn.toString() );
        ServerEntry entry = master.get( id );

        for ( AttributeType attributeType : mods.getAttributeTypes() )
        {
            EntryAttribute attr = mods.get( attributeType );

            switch ( modOp )
            {
                case ADD_ATTRIBUTE:
                    add( id, entry, attr );
                    break;

                case REMOVE_ATTRIBUTE:
                    remove( id, entry, attr );
                    break;

                case REPLACE_ATTRIBUTE:
                    replace( id, entry, attr );

                    break;

                default:
                    throw new NamingException( "Unidentified modification operation" );
            }
        }

        master.put( entry, id );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    public void modify( LdapDN dn, List<Modification> mods ) throws NamingException
    {
        Long id = getEntryId( dn.toString() );
        ServerEntry entry = master.get( id );

        for ( Modification mod : mods )
        {
            ServerAttribute attrMods = ( ServerAttribute ) mod.getAttribute();

            switch ( mod.getOperation() )
            {
                case ADD_ATTRIBUTE:
                    add( id, entry, attrMods );
                    break;

                case REMOVE_ATTRIBUTE:
                    remove( id, entry, attrMods );
                    break;

                case REPLACE_ATTRIBUTE:
                    replace( id, entry, attrMods );
                    break;

                default:
                    throw new NamingException( "Unidentified modification operation" );
            }
        }

        master.put( entry, id );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    /**
     * Changes the relative distinguished name of an entry specified by a 
     * distinguished name with the optional removal of the old Rdn attribute
     * value from the entry.  Name changes propagate down as dn changes to the 
     * descendants of the entry where the Rdn changed. 
     * 
     * An Rdn change operation does not change parent child relationships.  It 
     * merely propagates a name change at a point in the DIT where the Rdn is 
     * changed. The change propagates down the subtree rooted at the 
     * distinguished name specified.
     *
     * @param dn the normalized distinguished name of the entry to alter
     * @param newRdn the new Rdn to set
     * @param deleteOldRdn whether or not to remove the old Rdn attr/val
     * @throws NamingException if there are any errors propagating the name
     *        changes.
     */
    public void rename( LdapDN dn, Rdn newRdn, boolean deleteOldRdn ) throws NamingException
    {
        Long id = getEntryId( dn.getNormName() );
        ServerEntry entry = lookup( id );
        LdapDN updn = entry.getDn();

        /* 
         * H A N D L E   N E W   R D N
         * ====================================================================
         * Add the new Rdn attribute to the entry.  If an index exists on the 
         * new Rdn attribute we add the index for this attribute value pair.
         * Also we make sure that the existance index shows the existance of the
         * new Rdn attribute within this entry.
         */

        for ( AttributeTypeAndValue newAtav : newRdn )
        {
            String newNormType = newAtav.getNormType();
            String newNormValue = ( String ) newAtav.getNormValue();
            AttributeType newRdnAttrType = attributeTypeRegistry.lookup( newNormType );
            entry.add( newRdnAttrType, ( String ) newAtav.getUpValue() );

            if ( hasUserIndexOn( newNormType ) )
            {
                Index idx = getUserIndex( newNormType );
                idx.add( newNormValue, id );

                // Make sure the altered entry shows the existance of the new attrib
                if ( !existanceIdx.hasValue( newNormType, id ) )
                {
                    existanceIdx.add( newNormType, id );
                }
            }
        }

        /*
         * H A N D L E   O L D   R D N
         * ====================================================================
         * If the old Rdn is to be removed we need to get the attribute and 
         * value for it.  Keep in mind the old Rdn need not be based on the 
         * same attr as the new one.  We remove the Rdn value from the entry
         * and remove the value/id tuple from the index on the old Rdn attr
         * if any.  We also test if the delete of the old Rdn index tuple 
         * removed all the attribute values of the old Rdn using a reverse
         * lookup.  If so that means we blew away the last value of the old 
         * Rdn attribute.  In this case we need to remove the attrName/id 
         * tuple from the existance index.
         * 
         * We only remove an ATAV of the old Rdn if it is not included in the
         * new Rdn.
         */

        if ( deleteOldRdn )
        {
            Rdn oldRdn = updn.getRdn();
            for ( AttributeTypeAndValue oldAtav : oldRdn )
            {
                // check if the new ATAV is part of the old Rdn
                // if that is the case we do not remove the ATAV
                boolean mustRemove = true;
                for ( AttributeTypeAndValue newAtav : newRdn )
                {
                    if ( oldAtav.equals( newAtav ) )
                    {
                        mustRemove = false;
                        break;
                    }
                }

                if ( mustRemove )
                {
                    String oldNormType = oldAtav.getNormType();
                    String oldNormValue = ( String ) oldAtav.getNormValue();
                    AttributeType oldRdnAttrType = attributeTypeRegistry.lookup( oldNormType );
                    entry.remove( oldRdnAttrType, oldNormValue );

                    if ( hasUserIndexOn( oldNormType ) )
                    {
                        Index idx = getUserIndex( oldNormType );
                        idx.drop( oldNormValue, id );

                        /*
                         * If there is no value for id in this index due to our
                         * drop above we remove the oldRdnAttr from the existance idx
                         */
                        if ( null == idx.reverseLookup( id ) )
                        {
                            existanceIdx.drop( oldNormType, id );
                        }
                    }
                }
            }
        }

        /*
         * H A N D L E   D N   C H A N G E
         * ====================================================================
         * 1) Build the new user defined distinguished name
         *      - clone / copy old updn
         *      - remove old upRdn from copy
         *      - add the new upRdn to the copy
         * 2) Make call to recursive modifyDn method to change the names of the
         *    entry and its descendants
         */

        LdapDN newUpdn = ( LdapDN ) updn.clone(); // copy da old updn
        newUpdn.remove( newUpdn.size() - 1 ); // remove old upRdn
        newUpdn.add( newRdn.getUpName() ); // add da new upRdn

        // gotta normalize cuz this thang is cloned and not normalized by default
        newUpdn.normalize( attributeTypeRegistry.getNormalizerMapping() );

        modifyDn( id, newUpdn, false ); // propagate dn changes

        // Update the current entry
        entry.setDn( newUpdn );
        master.put( entry, id );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    /*
     * The move operation severs a child from a parent creating a new parent
     * child relationship.  As a consequence the relationships between the 
     * old ancestors of the child and its descendants change.  A descendant is
     *   
     */

    /**
     * Recursively modifies the distinguished name of an entry and the names of
     * its descendants calling itself in the recursion.
     *
     * @param id the primary key of the entry
     * @param updn User provided distinguished name to set as the new DN
     * @param isMove whether or not the name change is due to a move operation
     * which affects alias userIndices.
     * @throws NamingException if something goes wrong
     */
    private void modifyDn( Long id, LdapDN updn, boolean isMove ) throws NamingException
    {
        String aliasTarget;

        // Now we can handle the appropriate name userIndices for all cases
        ndnIdx.drop( id );

        if ( !updn.isNormalized() )
        {
            updn.normalize( attributeTypeRegistry.getNormalizerMapping() );
        }

        ndnIdx.add( ndnIdx.getNormalized( updn.toNormName() ), id );

        updnIdx.drop( id );
        updnIdx.add( updn.getUpName(), id );

        /* 
         * Read Alias Index Tuples
         * 
         * If this is a name change due to a move operation then the one and
         * subtree userIndices for aliases were purged before the aliases were
         * moved.  Now we must add them for each alias entry we have moved.  
         * 
         * aliasTarget is used as a marker to tell us if we're moving an 
         * alias.  If it is null then the moved entry is not an alias.
         */
        if ( isMove )
        {
            aliasTarget = ( String ) aliasIdx.reverseLookup( id );

            if ( null != aliasTarget )
            {
                addAliasIndices( id, new LdapDN( getEntryDn( id ) ), aliasTarget );
            }
        }

        NamingEnumeration children = list( id );
        while ( children.hasMore() )
        {
            // Get the child and its id
            IndexRecord rec = ( IndexRecord ) children.next();
            Long childId = ( Long ) rec.getEntryId();

            /* 
             * Calculate the Dn for the child's new name by copying the parents
             * new name and adding the child's old upRdn to new name as its Rdn
             */
            LdapDN childUpdn = ( LdapDN ) updn.clone();
            LdapDN oldUpdn = new LdapDN( getEntryUpdn( childId ) );

            String rdn = oldUpdn.get( oldUpdn.size() - 1 );
            LdapDN rdnDN = new LdapDN( rdn );
            rdnDN.normalize( attributeTypeRegistry.getNormalizerMapping() );
            childUpdn.add( rdnDN.getRdn() );

            // Modify the child
            ServerEntry entry = lookup( childId );
            entry.setDn( childUpdn );
            master.put( childId, entry );

            // Recursively change the names of the children below
            modifyDn( childId, childUpdn, isMove );
        }
    }


    public void move( LdapDN oldChildDn, LdapDN newParentDn, Rdn newRdn, boolean deleteOldRdn ) throws NamingException
    {
        Long childId = getEntryId( oldChildDn.toString() );
        rename( oldChildDn, newRdn, deleteOldRdn );
        move( oldChildDn, childId, newParentDn );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    public void move( LdapDN oldChildDn, LdapDN newParentDn ) throws NamingException
    {
        Long childId = getEntryId( oldChildDn.toString() );
        move( oldChildDn, childId, newParentDn );

        if ( isSyncOnWrite )
        {
            sync();
        }
    }


    /**
     * Moves an entry under a new parent.  The operation causes a shift in the
     * parent child relationships between the old parent, new parent and the 
     * child moved.  All other descendant entries under the child never change
     * their direct parent child relationships.  Hence after the parent child
     * relationship changes are broken at the old parent and set at the new
     * parent a modifyDn operation is conducted to handle name changes 
     * propagating down through the moved child and its descendants.
     * 
     * @param oldChildDn the normalized dn of the child to be moved
     * @param childId the id of the child being moved
     * @param newParentDn the normalized dn of the new parent for the child
     * @throws NamingException if something goes wrong
     */
    private void move( LdapDN oldChildDn, Long childId, LdapDN newParentDn ) throws NamingException
    {
        // Get the child and the new parent to be entries and Ids
        Long newParentId = getEntryId( newParentDn.toString() );
        Long oldParentId = getParentId( childId );

        /*
         * All aliases including and below oldChildDn, will be affected by
         * the move operation with respect to one and subtree userIndices since
         * their relationship to ancestors above oldChildDn will be 
         * destroyed.  For each alias below and including oldChildDn we will
         * drop the index tuples mapping ancestor ids above oldChildDn to the
         * respective target ids of the aliases.
         */
        dropMovedAliasIndices( oldChildDn );

        /*
         * Drop the old parent child relationship and add the new one
         * Set the new parent id for the child replacing the old parent id
         */
        hierarchyIdx.drop( oldParentId, childId );
        hierarchyIdx.add( newParentId, childId );

        /*
         * Build the new user provided DN (updn) for the child using the child's
         * user provided RDN & the new parent's UPDN.  Basically add the child's
         * UpRdn String to the tail of the new parent's Updn Name.
         */
        LdapDN childUpdn = new LdapDN( getEntryUpdn( childId ) );
        String childRdn = childUpdn.get( childUpdn.size() - 1 );
        LdapDN newUpdn = new LdapDN( getEntryUpdn( newParentId ) );
        newUpdn.add( newUpdn.size(), childRdn );

        // Call the modifyDn operation with the new updn
        modifyDn( childId, newUpdn, true );
    }


    /**
     * For all aliases including and under the moved base, this method removes
     * one and subtree alias index tuples for old ancestors above the moved base
     * that will no longer be ancestors after the move.
     * 
     * @param movedBase the base at which the move occured - the moved node
     * @throws NamingException if system userIndices fail
     */
    private void dropMovedAliasIndices( final LdapDN movedBase ) throws NamingException
    {
        // Find all the aliases from movedBase down
        IndexAssertion isBaseDescendant = new IndexAssertion()
        {
            public boolean assertCandidate( IndexRecord rec ) throws NamingException
            {
                String dn = getEntryDn( ( Long ) rec.getEntryId() );
                return dn.endsWith( movedBase.toString() );
            }
        };

        Long movedBaseId = getEntryId( movedBase.toString() );

        if ( aliasIdx.reverseLookup( movedBaseId ) != null )
        {
            dropAliasIndices( movedBaseId, movedBase );
        }

        NamingEnumeration<IndexRecord> aliases = new IndexAssertionEnumeration( aliasIdx.listIndices( movedBase
            .toString(), true ), isBaseDescendant );

        while ( aliases.hasMore() )
        {
            IndexRecord entry = aliases.next();
            dropAliasIndices( ( Long ) entry.getEntryId(), movedBase );
        }
    }


    /**
     * For the alias id all ancestor one and subtree alias tuples are moved 
     * above the moved base.
     * 
     * @param aliasId the id of the alias 
     * @param movedBase the base where the move occured
     * @throws NamingException if userIndices fail
     */
    private void dropAliasIndices( Long aliasId, LdapDN movedBase ) throws NamingException
    {
        String targetDn = ( String ) aliasIdx.reverseLookup( aliasId );
        Long targetId = getEntryId( targetDn );
        String aliasDn = getEntryDn( aliasId );

        /*
         * Start droping index tuples with the first ancestor right above the 
         * moved base.  This is the first ancestor effected by the move.
         */
        LdapDN ancestorDn = ( LdapDN ) movedBase.getPrefix( 1 );
        Long ancestorId = getEntryId( ancestorDn.toString() );

        /*
         * We cannot just drop all tuples in the one level and subtree userIndices
         * linking baseIds to the targetId.  If more than one alias refers to
         * the target then droping all tuples with a value of targetId would
         * make all other aliases to the target inconsistent.
         * 
         * We need to walk up the path of alias ancestors right above the moved 
         * base until we reach the upSuffix, deleting each ( ancestorId,
         * targetId ) tuple in the subtree scope alias.  We only need to do 
         * this for the direct parent of the alias on the one level subtree if
         * the moved base is the alias.
         */
        if ( aliasDn.equals( movedBase.toString() ) )
        {
            oneAliasIdx.drop( ancestorId, targetId );
        }

        subAliasIdx.drop( ancestorId, targetId );

        while ( !ancestorDn.equals( upSuffix ) )
        {
            ancestorDn = ( LdapDN ) ancestorDn.getPrefix( 1 );
            ancestorId = getEntryId( ancestorDn.toString() );

            subAliasIdx.drop( ancestorId, targetId );
        }
    }


    public void initRegistries( Registries registries )
    {
        this.attributeTypeRegistry = registries.getAttributeTypeRegistry();
        this.oidRegistry = registries.getOidRegistry();
    }
}
