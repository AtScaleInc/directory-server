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
package org.apache.directory.server.core.partition;


import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetMatchedNameOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.GetSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RemoveContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.tree.BranchNode;
import org.apache.directory.server.core.partition.tree.LeafNode;
import org.apache.directory.server.core.partition.tree.Node;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.MultiException;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeIdentifierException;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.CascadeControl;
import org.apache.directory.shared.ldap.message.EntryChangeControl;
import org.apache.directory.shared.ldap.message.ManageDsaITControl;
import org.apache.directory.shared.ldap.message.PersistentSearchControl;
import org.apache.directory.shared.ldap.message.SubentriesControl;
import org.apache.directory.shared.ldap.message.extended.NoticeOfDisconnect;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.NamespaceTools;
import org.apache.directory.shared.ldap.util.SingletonEnumeration;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


/**
 * A nexus for partitions dedicated for storing entries specific to a naming
 * context.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultPartitionNexus extends PartitionNexus
{
    private static final Logger LOG = LoggerFactory.getLogger( DefaultPartitionNexus.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** the vendorName string proudly set to: Apache Software Foundation*/
    private static final String ASF = "Apache Software Foundation";

    /** the closed state of this partition */
    private boolean initialized;

    private DirectoryService directoryService;

    /** the system partition */
    private Partition system;

    /** the partitions keyed by normalized suffix strings */
    private Map<String, Partition> partitions = new HashMap<String, Partition>();
    
    /** A structure to hold all the partitions */
    private BranchNode partitionLookupTree = new BranchNode();
    
    /** the read only rootDSE attributes */
    private final ServerEntry rootDSE;

    /** The global registries */
    private Registries registries;
    
    /** The attributeType registry */
    private AttributeTypeRegistry atRegistry;
    
    /** The OID registry */
    private OidRegistry oidRegistry;


    /**
     * Creates the root nexus singleton of the entire system.  The root DSE has
     * several attributes that are injected into it besides those that may
     * already exist.  As partitions are added to the system more namingContexts
     * attributes are added to the rootDSE.
     *
     * @see <a href="http://www.faqs.org/rfcs/rfc3045.html">Vendor Information</a>
     * @param rootDSE the root entry for the DSA
     * @throws javax.naming.NamingException on failure to initialize
     */
    public DefaultPartitionNexus( ServerEntry rootDSE ) throws NamingException
    {
        // setup that root DSE
        this.rootDSE = rootDSE;
        
        // Add the basic informations
        rootDSE.put( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, ServerDNConstants.CN_SCHEMA_DN );
        rootDSE.put( SchemaConstants.SUPPORTED_LDAP_VERSION_AT, "3" );
        rootDSE.put( SchemaConstants.SUPPORTED_FEATURES_AT, SchemaConstants.FEATURE_ALL_OPERATIONAL_ATTRIBUTES );
        rootDSE.put( SchemaConstants.SUPPORTED_EXTENSION_AT, NoticeOfDisconnect.EXTENSION_OID );

        // Add the supported controls
        rootDSE.put( SchemaConstants.SUPPORTED_CONTROL_AT, 
            PersistentSearchControl.CONTROL_OID,
            EntryChangeControl.CONTROL_OID,
            SubentriesControl.CONTROL_OID,
            ManageDsaITControl.CONTROL_OID,
            CascadeControl.CONTROL_OID );

        // Add the objectClasses
        rootDSE.put( SchemaConstants.OBJECT_CLASS_AT,
            SchemaConstants.TOP_OC,
            SchemaConstants.EXTENSIBLE_OBJECT_OC );

        // Add the 'vendor' name and version infos
        rootDSE.put( SchemaConstants.VENDOR_NAME_AT, ASF );

        Properties props = new Properties();
        
        try
        {
            props.load( getClass().getResourceAsStream( "version.properties" ) );
        }
        catch ( IOException e )
        {
            LOG.error( "failed to LOG version properties" );
        }

        rootDSE.put( SchemaConstants.VENDOR_VERSION_AT, props.getProperty( "apacheds.version", "UNKNOWN" ) );
    }

    
    /**
     * Always returns the string "NEXUS".
     *
     * @return the string "NEXUS"
     */
    public String getId()
    {
        return "NEXUS";
    }


    // -----------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------


    /**
     * Not supported!
     *
     * @throws UnsupportedOperationException everytime
     */
    public void setId( String id )
    {
        throw new UnsupportedOperationException( "The id cannot be set for the partition nexus." );
    }


    /**
     * Returns root the rootDSE.
     *
     * @return the root entry for the DSA
     */
    public ServerEntry getContextEntry()
    {
        return rootDSE;
    }


    /**
     * Sets root entry for this BTreePartition.
     *
     * @throws UnsupportedOperationException everytime
     */
    public void setContextEntry( ServerEntry rootEntry )
    {
        throw new UnsupportedOperationException( "Setting the RootDSE is not allowed." );
    }


    /**
     * Always returns the empty String "".
     * @return the empty String ""
     */
    public String getSuffix()
    {
        return "";
    }


    /**
     * Unsupported operation on the Nexus.
     * @throws UnsupportedOperationException everytime
     */
    public void setSuffix( String suffix )
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Not support!
     */
    public void setCacheSize( int cacheSize )
    {
        throw new UnsupportedOperationException( "You cannot set the cache size of the nexus" );
    }


    /**
     * Not supported!
     *
     * @throws UnsupportedOperationException always
     */
    public int getCacheSize()
    {
        throw new UnsupportedOperationException( "There is no cache size associated with the nexus" );
    }



    public void init( DirectoryService directoryService )
        throws NamingException
    {
        // NOTE: We ignore ContextPartitionConfiguration parameter here.
        if ( initialized )
        {
            return;
        }

        this.directoryService = directoryService;
        registries = directoryService.getRegistries();
        atRegistry = registries.getAttributeTypeRegistry();
        oidRegistry = registries.getOidRegistry();
        
        initializeSystemPartition();
        List<Partition> initializedPartitions = new ArrayList<Partition>();
        initializedPartitions.add( 0, this.system );

        //noinspection unchecked
        Iterator<? extends Partition> partitions = ( Iterator<? extends Partition> ) directoryService.getPartitions().iterator();
        try
        {
            while ( partitions.hasNext() )
            {
                Partition partition = partitions.next();
                AddContextPartitionOperationContext opCtx = 
                    new AddContextPartitionOperationContext( registries, partition );
                addContextPartition( opCtx );
                initializedPartitions.add( opCtx.getPartition() );
            }
            initialized = true;
        }
        finally
        {
            if ( !initialized )
            {
                Iterator<Partition> i = initializedPartitions.iterator();
                while ( i.hasNext() )
                {
                    Partition partition = i.next();
                    i.remove();
                    try
                    {
                        partition.destroy();
                    }
                    catch ( Exception e )
                    {
                        LOG.warn( "Failed to destroy a partition: " + partition.getSuffixDn(), e );
                    }
                    finally
                    {
                        unregister( partition );
                    }
                }
            }
        }
    }


    private Partition initializeSystemPartition() throws NamingException
    {
        // initialize system partition first
        Partition override = directoryService.getSystemPartition();
        
        if ( override != null )
        {
            ServerEntry systemEntry = override.getContextEntry();
            EntryAttribute objectClassAttr = systemEntry.get( SchemaConstants.OBJECT_CLASS_AT );
            
            if ( objectClassAttr == null )
            {
                systemEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                    SchemaConstants.TOP_OC,
                    SchemaConstants.ORGANIZATIONAL_UNIT_OC,
                    SchemaConstants.EXTENSIBLE_OBJECT_OC );
            }
            else
            {
                // Feed the contextEntry with the mandatory ObjectClass values, if they are missing.
                if ( !objectClassAttr.contains( SchemaConstants.TOP_OC ) )
                {
                    objectClassAttr.add( SchemaConstants.TOP_OC );
                }
                
                if ( !objectClassAttr.contains( SchemaConstants.ORGANIZATIONAL_UNIT_OC ) )
                {
                    objectClassAttr.add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );
                }

                if ( !objectClassAttr.contains( SchemaConstants.EXTENSIBLE_OBJECT_OC ) )
                {
                    objectClassAttr.add( SchemaConstants.EXTENSIBLE_OBJECT_OC );
                }
            }
            
            systemEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN );
            systemEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            systemEntry.put( NamespaceTools.getRdnAttribute( ServerDNConstants.SYSTEM_DN ),
                NamespaceTools.getRdnValue( ServerDNConstants.SYSTEM_DN ) );
            
            override.setContextEntry( systemEntry );
            
            // ---------------------------------------------------------------
            // check a few things to make sure users configured it properly
            // ---------------------------------------------------------------

            if ( ! override.getId().equals( "system" ) )
            {
                throw new ConfigurationException( "System partition has wrong name: should be 'system' not '"
                        + override.getId() + "'." );
            }
            
            // add all attribute oids of index configs to a hashset
            if ( override instanceof JdbmPartition )
            {
                Set<Index> indices = ( ( JdbmPartition ) override ).getIndexedAttributes();
                Set<String> indexOids = new HashSet<String>();
                OidRegistry registry = registries.getOidRegistry();

                for ( Index index : indices )
                {
                    indexOids.add( registry.getOid( index.getAttributeId() ) );
                }

                if ( ! indexOids.contains( registry.getOid( SchemaConstants.OBJECT_CLASS_AT ) ) )
                {
                    LOG.warn( "CAUTION: You have not included objectClass as an indexed attribute" +
                            "in the system partition configuration.  This will lead to poor " +
                            "performance.  The server is automatically adding this index for you." );
                    JdbmIndex index = new JdbmIndex();
                    index.setAttributeId( SchemaConstants.OBJECT_CLASS_AT );
                    indices.add( index );
                }

                ( ( JdbmPartition ) override ).setIndexedAttributes( indices );

                system = override;
            }
        }
        else
        {
            system = new JdbmPartition();
            system.setId( "system" );
            system.setCacheSize( 500 );
            system.setSuffix( ServerDNConstants.SYSTEM_DN );
    
            // Add objectClass attribute for the system partition
            Set<Index> indexedAttrs = new HashSet<Index>();
            indexedAttrs.add( new JdbmIndex( SchemaConstants.OBJECT_CLASS_AT ) );
            ( ( JdbmPartition ) system ).setIndexedAttributes( indexedAttrs );
    
            // Add context entry for system partition
            ServerEntry systemEntry = new DefaultServerEntry( registries, new LdapDN( ServerDNConstants.SYSTEM_DN ) );

            // Add the ObjectClasses
            systemEntry.put( SchemaConstants.OBJECT_CLASS_AT,
                SchemaConstants.TOP_OC,
                SchemaConstants.ORGANIZATIONAL_UNIT_OC,
                SchemaConstants.EXTENSIBLE_OBJECT_OC
                );
            
            // Add some operational attributes
            systemEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN );
            systemEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            systemEntry.put( NamespaceTools.getRdnAttribute( ServerDNConstants.SYSTEM_DN ),
                NamespaceTools.getRdnValue( ServerDNConstants.SYSTEM_DN ) );

            system.setContextEntry( systemEntry );
        }

        system.init( directoryService );
        String key = system.getSuffixDn().toString();
        
        if ( partitions.containsKey( key ) )
        {
            throw new ConfigurationException( "Duplicate partition suffix: " + key );
        }
        
        synchronized ( partitionLookupTree )
        {
            partitions.put( key, system );
            partitionLookupTree.recursivelyAddPartition( partitionLookupTree, system.getSuffixDn(), 0, system );
            EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
            
            if ( namingContexts == null )
            {
                namingContexts = new DefaultServerAttribute( 
                    registries.getAttributeTypeRegistry().lookup( SchemaConstants.NAMING_CONTEXTS_AT ), 
                    system.getUpSuffixDn().getUpName() );
                rootDSE.put( namingContexts );
            }
            else
            {
                namingContexts.add( system.getUpSuffixDn().getUpName() );
            }
        }

        return system;
    }


    public boolean isInitialized()
    {
        return initialized;
    }


    public synchronized void destroy()
    {
        if ( !initialized )
        {
            return;
        }

        // make sure this loop is not fail fast so all backing stores can
        // have an attempt at closing down and synching their cached entries
        for ( String suffix : new HashSet<String>( this.partitions.keySet() ) )
        {
            try
            {
                removeContextPartition( new RemoveContextPartitionOperationContext( registries, new LdapDN( suffix ) ) );
            }
            catch ( NamingException e )
            {
                LOG.warn( "Failed to destroy a partition: " + suffix, e );
            }
        }

        initialized = false;
    }


    /**
     * @see Partition#sync()
     */
    public void sync() throws NamingException
    {
        MultiException error = null;

        for ( Partition partition : this.partitions.values() )
        {
            try
            {
                partition.sync();
            }
            catch ( NamingException e )
            {
                LOG.warn( "Failed to flush partition data out.", e );
                if ( error == null )
                {
                    //noinspection ThrowableInstanceNeverThrown
                    error = new MultiException( "Grouping many exceptions on root nexus sync()" );
                }

                // @todo really need to send this info to a monitor
                error.addThrowable( e );
            }
        }

        if ( error != null )
        {
            String msg = "Encountered failures while performing a sync() operation on backing stores";
            //noinspection ThrowableInstanceNeverThrown
            NamingException total = new NamingException( msg );
            total.setRootCause( error );
        }
    }


    // ------------------------------------------------------------------------
    // ContextPartitionNexus Method Implementations
    // ------------------------------------------------------------------------

    public boolean compare( CompareOperationContext compareContext ) throws NamingException
    {
        Partition partition = getPartition( compareContext.getDn() );
        AttributeTypeRegistry registry = registries.getAttributeTypeRegistry();
        
        // complain if we do not recognize the attribute being compared
        if ( !registry.hasAttributeType( compareContext.getOid() ) )
        {
            throw new LdapInvalidAttributeIdentifierException( compareContext.getOid() + " not found within the attributeType registry" );
        }

        AttributeType attrType = registry.lookup( compareContext.getOid() );
        
        EntryAttribute attr = partition.lookup( new LookupOperationContext( registries, compareContext.getDn() ) ).get( attrType.getName() );

        // complain if the attribute being compared does not exist in the entry
        if ( attr == null )
        {
            throw new LdapNoSuchAttributeException();
        }

        // see first if simple match without normalization succeeds
        if ( attr.contains( (Value<?>)compareContext.getValue()  ) )
        {
            return true;
        }

        // now must apply normalization to all values (attr and in request) to compare

        /*
         * Get ahold of the normalizer for the attribute and normalize the request
         * assertion value for comparisons with normalized attribute values.  Loop
         * through all values looking for a match.
         */
        Normalizer normalizer = attrType.getEquality().getNormalizer();
        Object reqVal = normalizer.normalize( ((Value<?>)compareContext.getValue()).get() );

        for ( Value<?> value:attr )
        {
            Object attrValObj = normalizer.normalize( value.get() );
            
            if ( attrValObj instanceof String )
            {
                String attrVal = ( String ) attrValObj;
                if ( ( reqVal instanceof String ) && attrVal.equals( reqVal ) )
                {
                    return true;
                }
            }
            else
            {
                byte[] attrVal = ( byte[] ) attrValObj;
                if ( reqVal instanceof byte[] )
                {
                    return Arrays.equals( attrVal, ( byte[] ) reqVal );
                }
                else if ( reqVal instanceof String )
                {
                    return Arrays.equals( attrVal, StringTools.getBytesUtf8( ( String ) reqVal ) );
                }
            }
        }

        return false;
    }


    public synchronized void addContextPartition( AddContextPartitionOperationContext opContext ) throws NamingException
    {
        Partition partition = opContext.getPartition();

        // Turn on default indices
        String key = partition.getSuffix();
        
        if ( partitions.containsKey( key ) )
        {
            throw new ConfigurationException( "Duplicate partition suffix: " + key );
        }

        if ( ! partition.isInitialized() )
        {
            partition.setContextEntry( partition.getContextEntry() );
            
            partition.init( directoryService );
        }
        
        synchronized ( partitionLookupTree )
        {
            LdapDN partitionSuffix = partition.getSuffixDn();
            
            if ( partitionSuffix == null )
            {
                throw new ConfigurationException( "The current partition does not have any suffix: " + partition.getId() );
            }
            
            partitions.put( partitionSuffix.toString(), partition );
            partitionLookupTree.recursivelyAddPartition( partitionLookupTree, partition.getSuffixDn(), 0, partition );

            EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );

            LdapDN partitionUpSuffix = partition.getUpSuffixDn();
            
            if ( partitionUpSuffix == null )
            {
                throw new ConfigurationException( "The current partition does not have any user provided suffix: " + partition.getId() );
            }
            
            if ( namingContexts == null )
            {
                namingContexts = new DefaultServerAttribute( 
                    registries.getAttributeTypeRegistry().lookup( SchemaConstants.NAMING_CONTEXTS_AT ), partitionUpSuffix.getUpName() );
                rootDSE.put( namingContexts );
            }
            else
            {
                namingContexts.add( partitionUpSuffix.getUpName() );
            }
        }
    }


    public synchronized void removeContextPartition( RemoveContextPartitionOperationContext removeContextPartition ) throws NamingException
    {
        String key = removeContextPartition.getDn().getNormName();
        Partition partition = partitions.get( key );
        
        if ( partition == null )
        {
            throw new NameNotFoundException( "No partition with suffix: " + key );
        }

        EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
        
        if ( namingContexts != null )
        {
            namingContexts.remove( partition.getUpSuffixDn().getUpName() );
        }

        // Create a new partition list. 
        // This is easier to create a new structure from scratch than to reorganize
        // the current structure. As this structure is not modified often
        // this is an acceptable solution.
        synchronized ( partitionLookupTree )
        {
            partitions.remove( key );
            partitionLookupTree = new BranchNode();
            
            for ( Partition part : partitions.values() )
            {
                partitionLookupTree.recursivelyAddPartition( partitionLookupTree, part.getSuffixDn(), 0, partition );
            }
    
            partition.sync();
            partition.destroy();
        }
    }


    public Partition getSystemPartition()
    {
        return system;
    }


    /**
     * @see PartitionNexus#getLdapContext()
     */
    public LdapContext getLdapContext()
    {
        throw new NotImplementedException();
    }


    /**
     * @see PartitionNexus#getMatchedName( GetMatchedNameOperationContext )
     */
    public LdapDN getMatchedName ( GetMatchedNameOperationContext getMatchedNameContext ) throws NamingException
    {
        LdapDN dn = ( LdapDN ) getMatchedNameContext.getDn().clone();
        
        while ( dn.size() > 0 )
        {
            if ( hasEntry( new EntryOperationContext( registries, dn ) ) )
            {
                return dn;
            }

            dn.remove( dn.size() - 1 );
        }

        return dn;
    }


    public LdapDN getSuffixDn()
    {
        return LdapDN.EMPTY_LDAPDN;
    }

    public LdapDN getUpSuffixDn()
    {
        return LdapDN.EMPTY_LDAPDN;
    }


    /**
     * @see PartitionNexus#getSuffix( GetSuffixOperationContext )
     */
    public LdapDN getSuffix ( GetSuffixOperationContext getSuffixContext ) throws NamingException
    {
        Partition backend = getPartition( getSuffixContext.getDn() );
        return backend.getSuffixDn();
    }


    /**
     * @see PartitionNexus#listSuffixes( ListSuffixOperationContext )
     */
    public Iterator<String> listSuffixes ( ListSuffixOperationContext emptyContext ) throws NamingException
    {
        return Collections.unmodifiableSet( partitions.keySet() ).iterator();
    }


    public ServerEntry getRootDSE( GetRootDSEOperationContext getRootDSEContext )
    {
        return rootDSE;
    }


    /**
     * Unregisters an ContextPartition with this BackendManager.  Called for each
     * registered Backend right befor it is to be stopped.  This prevents
     * protocol server requests from reaching the Backend and effectively puts
     * the ContextPartition's naming context offline.
     *
     * Operations against the naming context should result in an LDAP BUSY
     * result code in the returnValue if the naming context is not online.
     *
     * @param partition ContextPartition component to unregister with this
     * BackendNexus.
     * @throws NamingException if there are problems unregistering the partition
     */
    private void unregister( Partition partition ) throws NamingException
    {
        EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
        
        if ( namingContexts != null )
        {
            namingContexts.remove( partition.getSuffixDn().getUpName() );
        }
        
        partitions.remove( partition.getSuffixDn().toString() );
    }


    // ------------------------------------------------------------------------
    // DirectoryPartition Interface Method Implementations
    // ------------------------------------------------------------------------
    public void bind( BindOperationContext bindContext ) throws NamingException
    {
        Partition partition = getPartition( bindContext.getDn() );
        partition.bind( bindContext );
    }

    public void unbind( UnbindOperationContext unbindContext ) throws NamingException
    {
        Partition partition = getPartition( unbindContext.getDn() );
        partition.unbind( unbindContext );
    }


    /**
     * @see Partition#delete(DeleteOperationContext)
     */
    public void delete( DeleteOperationContext deleteContext ) throws NamingException
    {
        Partition backend = getPartition( deleteContext.getDn() );
        backend.delete( deleteContext );
    }


    /**
     * Looks up the backend corresponding to the entry first, then checks to
     * see if the entry already exists.  If so an exception is thrown.  If not
     * the add operation against the backend proceeds.  This check is performed
     * here so backend implementors do not have to worry about performing these
     * kinds of checks.
     *
     * @see Partition#add( AddOperationContext )
     */
    public void add( AddOperationContext addContext ) throws NamingException
    {
        Partition backend = getPartition( addContext.getDn() );
        backend.add( addContext );
    }


    public void modify( ModifyOperationContext modifyContext ) throws NamingException
    {
        Partition backend = getPartition( modifyContext.getDn() );
        backend.modify( modifyContext );
    }


    /**
     * @see Partition#list(ListOperationContext)
     */
    public NamingEnumeration<ServerSearchResult> list( ListOperationContext opContext ) throws NamingException
    {
        Partition backend = getPartition( opContext.getDn() );
        return backend.list( opContext );
    }


    public NamingEnumeration<ServerSearchResult> search( SearchOperationContext opContext )
        throws NamingException
    {
        LdapDN base = opContext.getDn();
        SearchControls searchCtls = opContext.getSearchControls();
        ExprNode filter = opContext.getFilter();
        
        if ( base.size() == 0 )
        {
            boolean isObjectScope = searchCtls.getSearchScope() == SearchControls.OBJECT_SCOPE;
            
            // test for (objectClass=*)
            boolean isSearchAll = ( ( PresenceNode ) filter ).getAttribute().equals( SchemaConstants.OBJECT_CLASS_AT_OID );

            /*
             * if basedn is "", filter is "(objectclass=*)" and scope is object
             * then we have a request for the rootDSE
             */
            if ( filter instanceof PresenceNode && isObjectScope && isSearchAll )
            {
                String[] ids = searchCtls.getReturningAttributes();

                // -----------------------------------------------------------
                // If nothing is asked for then we just return the entry asis.
                // We let other mechanisms filter out operational attributes.
                // -----------------------------------------------------------
                if ( ( ids == null ) || ( ids.length == 0 ) )
                {
                    ServerEntry rootDSE = (ServerEntry)getRootDSE( null ).clone();
                    ServerSearchResult result = new ServerSearchResult( LdapDN.EMPTY_LDAPDN, null, rootDSE, false );
                    return new SingletonEnumeration<ServerSearchResult>( result );
                }
                
                // -----------------------------------------------------------
                // Collect all the real attributes besides 1.1, +, and * and
                // note if we've seen these special attributes as well.
                // -----------------------------------------------------------

                Set<String> realIds = new HashSet<String>();
                boolean containsAsterisk = false;
                boolean containsPlus = false;
                boolean containsOneDotOne = false;
                
                for ( String id:ids )
                {
                    String idTrimmed = id.trim();
                    
                    if ( idTrimmed.equals( SchemaConstants.ALL_USER_ATTRIBUTES ) )
                    {
                        containsAsterisk = true;
                    }
                    else if ( idTrimmed.equals( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
                    {
                        containsPlus = true;
                    }
                    else if ( idTrimmed.equals( SchemaConstants.NO_ATTRIBUTE ) )
                    {
                        containsOneDotOne = true;
                    }
                    else
                    {
                        try
                        {
                            realIds.add( oidRegistry.getOid( idTrimmed ) );
                        }
                        catch ( NamingException e )
                        {
                            realIds.add( idTrimmed );
                        }
                    }
                }

                // return nothing
                if ( containsOneDotOne )
                {
                    ServerEntry serverEntry = new DefaultServerEntry( registries, base );
                    ServerSearchResult result = new ServerSearchResult( LdapDN.EMPTY_LDAPDN, null, serverEntry, false );
                    return new SingletonEnumeration<ServerSearchResult>( result );
                }
                
                // return everything
                if ( containsAsterisk && containsPlus )
                {
                    ServerEntry rootDSE = (ServerEntry)getRootDSE( null ).clone();
                    ServerSearchResult result = new ServerSearchResult( LdapDN.EMPTY_LDAPDN, null, rootDSE, false );
                    return new SingletonEnumeration<ServerSearchResult>( result );
                }
                
                ServerEntry serverEntry = new DefaultServerEntry( registries, opContext.getDn() );
                
                ServerEntry rootDSE = getRootDSE( new GetRootDSEOperationContext( registries ) );
                
                for ( EntryAttribute attribute:rootDSE )
                {
                    AttributeType type = atRegistry.lookup( attribute.getUpId() );
                    
                    if ( realIds.contains( type.getOid() ) )
                    {
                        serverEntry.put( attribute );
                    }
                    else if ( containsAsterisk && ( type.getUsage() == UsageEnum.USER_APPLICATIONS ) )
                    {
                        serverEntry.put( attribute );
                    }
                    else if ( containsPlus && ( type.getUsage() != UsageEnum.USER_APPLICATIONS ) )
                    {
                        serverEntry.put( attribute );
                    }
                }

                ServerSearchResult result = new ServerSearchResult( LdapDN.EMPTY_LDAPDN, null, serverEntry, false );
                return new SingletonEnumeration<ServerSearchResult>( result );
            }

            throw new LdapNameNotFoundException();
        }

        Partition backend = getPartition( base );
        return backend.search( opContext );
    }


    public ServerEntry lookup( LookupOperationContext opContext ) throws NamingException
    {
        LdapDN dn = opContext.getDn();
        
        if ( dn.size() == 0 )
        {
            ServerEntry retval = new DefaultServerEntry( registries, opContext.getDn() );
            Set<AttributeType> attributeTypes = rootDSE.getAttributeTypes();
     
            if ( opContext.getAttrsId() != null )
            {
                for ( AttributeType attributeType:attributeTypes )
                {
                    String oid = attributeType.getOid();
                    
                    if ( opContext.getAttrsId().contains( oid ) )
                    {
                        EntryAttribute attr = rootDSE.get( oid );
                        retval.put( (ServerAttribute)attr.clone() );
                    }
                    
                }
            }
            else
            {
                for ( AttributeType attributeType:attributeTypes )
                {
                    String id = attributeType.getName();
                    
                    EntryAttribute attr = rootDSE.get( id );
                    retval.put( (ServerAttribute)attr.clone() );
                }
            }
            
            return retval;
        }

        Partition backend = getPartition( dn );
        return backend.lookup( opContext );
    }


    /**
     * @see Partition#hasEntry(EntryOperationContext)
     */
    public boolean hasEntry( EntryOperationContext opContext ) throws NamingException
    {
        LdapDN dn = opContext.getDn();
        
        if ( IS_DEBUG )
        {
            LOG.debug( "Check if DN '" + dn + "' exists." );
        }

        if ( dn.size() == 0 )
        {
            return true;
        }

        Partition backend = getPartition( dn );
        return backend.hasEntry( opContext );
    }


    /**
     * @see Partition#rename(RenameOperationContext)
     */
    public void rename( RenameOperationContext opContext ) throws NamingException
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.rename( opContext );
    }


    /**
     * @see Partition#move(MoveOperationContext)
     */
    public void move( MoveOperationContext opContext ) throws NamingException
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.move( opContext );
    }


    public void moveAndRename( MoveAndRenameOperationContext opContext ) throws NamingException
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.moveAndRename( opContext );
    }


    /**
     * Gets the partition associated with a normalized dn.
     *
     * @param dn the normalized distinguished name to resolve to a partition
     * @return the backend partition associated with the normalized dn
     * @throws NamingException if the name cannot be resolved to a partition
     */
    public Partition getPartition( LdapDN dn ) throws NamingException
    {
        Enumeration<String> rdns = dn.getAll();
        
        // This is synchronized so that we can't read the
        // partitionList when it is modified.
        synchronized ( partitionLookupTree )
        {
            Node currentNode = partitionLookupTree;

            // Iterate through all the RDN until we find the associated partition
            while ( rdns.hasMoreElements() )
            {
                String rdn = rdns.nextElement();

                if ( currentNode == null )
                {
                    break;
                }

                if ( currentNode instanceof LeafNode )
                {
                    return ( ( LeafNode ) currentNode ).getPartition();
                }

                BranchNode currentBranch = ( BranchNode ) currentNode;
                
                if ( currentBranch.contains( rdn ) )
                {
                    currentNode = currentBranch.getChild( rdn );
                    
                    if ( currentNode instanceof LeafNode )
                    {
                        return ( ( LeafNode ) currentNode ).getPartition();
                    }
                }
            }
        }
        
        throw new LdapNameNotFoundException( dn.getUpName() );
    }


    public void registerSupportedExtensions( Set<String> extensionOids ) throws NamingException
    {
        EntryAttribute supportedExtension = rootDSE.get( SchemaConstants.SUPPORTED_EXTENSION_AT );

        if ( supportedExtension == null )
        {
            rootDSE.set( SchemaConstants.SUPPORTED_EXTENSION_AT );
            supportedExtension = rootDSE.get( SchemaConstants.SUPPORTED_EXTENSION_AT );
        }

        for ( String extensionOid : extensionOids )
        {
            supportedExtension.add( extensionOid );
        }
    }


    public void registerSupportedSaslMechanisms( Set<String> supportedSaslMechanisms ) throws NamingException
    {
        EntryAttribute supportedSaslMechanismsAttribute = rootDSE.get( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );

        if ( supportedSaslMechanismsAttribute == null )
        {
            rootDSE.set( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );
            supportedSaslMechanismsAttribute = rootDSE.get( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );
        }

        for ( String saslMechanism : supportedSaslMechanisms )
        {
            supportedSaslMechanismsAttribute.add( saslMechanism );
        }
    }
}
