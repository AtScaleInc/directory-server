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
package org.apache.directory.mitosis.operation;

import org.apache.directory.mitosis.common.CSN;
import org.apache.directory.mitosis.common.CSNFactory;
import org.apache.directory.mitosis.common.Constants;
import org.apache.directory.mitosis.common.ReplicaId;
import org.apache.directory.mitosis.common.UUIDFactory;
import org.apache.directory.mitosis.configuration.ReplicationConfiguration;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;

import java.util.List;


/**
 * Creates an {@link Operation} instance for a JNDI operation.  The
 * {@link Operation} instance returned by the provided factory methods are
 * mostly a {@link CompositeOperation}, which consists smaller JNDI
 * operations. The elements of the {@link CompositeOperation} differs from
 * the original JNDI operation to make the operation more robust to
 * replication conflict.  All {@link Operation}s created by
 * {@link OperationFactory} whould be robust to the replication conflict and
 * should be able to recover from the conflict.
 * <p>
 * "Add" (or "bind") is the only operation that doesn't return a
 * {@link CompositeOperation} but returns an {@link AddEntryOperation}.
 * It is because all other operations needs to update its related entry's
 * {@link Constants#ENTRY_CSN} or {@link Constants#ENTRY_DELETED} attribute
 * with additional sub-operations.  In contrast, "add" operation doesn't need
 * to create a {@link CompositeOperation} because those attributes can be
 * added just modifying an {@link AddEntryOperation} rather than creating
 * a parent operation and add sub-operations there.
 * <p>
 * Please note that all operations update {@link Constants#ENTRY_CSN} and
 * documentation for each method won't explain this behavior.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class OperationFactory
{
    private final ReplicaId replicaId;
    private final PartitionNexus nexus;
    private final UUIDFactory uuidFactory;
    private final CSNFactory csnFactory;
    
    /** The attributeType registry */
    private final AttributeTypeRegistry attributeRegistry;

    /** The global registries */
    private Registries registries;

    public OperationFactory( DirectoryService directoryService, ReplicationConfiguration cfg )
    {
        replicaId = cfg.getReplicaId();
        nexus = directoryService.getPartitionNexus();
        uuidFactory = cfg.getUuidFactory();
        csnFactory = cfg.getCsnFactory();
        registries = directoryService.getRegistries();
        attributeRegistry = registries.getAttributeTypeRegistry();
    }


    /**
     * Creates a new {@link Operation} that performs LDAP "add" operation
     * with a newly generated {@link CSN}.
     */
    public Operation newAdd( LdapDN normalizedName, ServerEntry entry ) throws NamingException
    {
        return newAdd( newCSN(), normalizedName, entry );
    }


    /**
     * Creates a new {@link Operation} that performs LDAP "add" operation
     * with the specified {@link CSN}.  The new entry will have three
     * additional attributes; {@link Constants#ENTRY_CSN} ({@link CSN}),
     * {@link Constants#ENTRY_UUID}, and {@link Constants#ENTRY_DELETED}.
     */
    private Operation newAdd( CSN csn, LdapDN normalizedName, ServerEntry entry ) throws NamingException
    {
        // Check an entry already exists.
        checkBeforeAdd( normalizedName );

        // Insert 'entryUUID' and 'entryDeleted'.
        ServerEntry cloneEntry = ( ServerEntry ) entry.clone();
        cloneEntry.removeAttributes( Constants.ENTRY_UUID );
        cloneEntry.removeAttributes( Constants.ENTRY_DELETED );
        cloneEntry.put( Constants.ENTRY_UUID, uuidFactory.newInstance().toOctetString() );
        cloneEntry.put( Constants.ENTRY_DELETED, "FALSE" );

        // NOTE: We inlined addDefaultOperations() because ApacheDS currently
        // creates an index entry only for ADD operation (and not for
        // MODIFY operation)
        cloneEntry.put( Constants.ENTRY_CSN, csn.toOctetString() );

        return new AddEntryOperation( csn, cloneEntry );
    }


    /**
     * Creates a new {@link Operation} that performs "delete" operation.
     * The created {@link Operation} doesn't actually delete the entry.
     * Instead, it sets {@link Constants#ENTRY_DELETED} to "TRUE". 
     */
    public Operation newDelete( LdapDN normalizedName ) throws NamingException
    {
        CSN csn = newCSN();
        CompositeOperation result = new CompositeOperation( csn );

        // Transform into replace operation.
        result.add( new ReplaceAttributeOperation( csn, normalizedName, 
            new DefaultServerAttribute( 
                Constants.ENTRY_DELETED, 
                attributeRegistry.lookup( Constants.ENTRY_DELETED ),
                "TRUE" ) ) );

        return addDefaultOperations( result, csn, normalizedName );
    }


    /**
     * Returns a new {@link Operation} that performs "modify" operation.
     * 
     * @return a {@link CompositeOperation} that consists of one or more
     * {@link AttributeOperation}s and one additional operation that
     * sets {@link Constants#ENTRY_DELETED} to "FALSE" to resurrect the
     * entry the modified attributes belong to.
     */
    public Operation newModify( ModifyOperationContext opContext ) throws NamingException
    {
        List<Modification> items = opContext.getModItems();
        LdapDN normalizedName = opContext.getDn();

        CSN csn = newCSN();
        CompositeOperation result = new CompositeOperation( csn );
        
        // Transform into multiple {@link AttributeOperation}s.
        for ( Modification item:items )
        {
            result.add( 
                newModify( 
                    csn, 
                    normalizedName, 
                    item.getOperation(), 
                    (ServerAttribute)item.getAttribute() ) );
        }

        // Resurrect the entry in case it is deleted.
        result.add( 
            new ReplaceAttributeOperation( 
                csn, 
                normalizedName, 
                new DefaultServerAttribute( 
                    Constants.ENTRY_DELETED,
                    attributeRegistry.lookup( Constants.ENTRY_DELETED ),
                    "FALSE" ) ) );

        return addDefaultOperations( result, csn, normalizedName );
    }


    /**
     * Returns a new {@link AttributeOperation} that performs one 
     * attribute modification operation.  This method is called by other
     * methods internally to create an appropriate {@link AttributeOperation}
     * instance from the specified <tt>modOp</tt> value.
     */
    private Operation newModify( CSN csn, LdapDN normalizedName, ModificationOperation modOp, ServerAttribute attribute )
    {
        switch ( modOp )
        {
            case ADD_ATTRIBUTE:
                return new AddAttributeOperation( csn, normalizedName, attribute );
            
            case REPLACE_ATTRIBUTE:
                return new ReplaceAttributeOperation( csn, normalizedName, attribute );
            
            case REMOVE_ATTRIBUTE:
                return new DeleteAttributeOperation( csn, normalizedName, attribute );
            
            default:
                throw new IllegalArgumentException( "Unknown modOp: " + modOp );
        }
    }


    /**
     * Returns a new {@link Operation} that performs "modifyRN" operation.
     * This operation is a subset of "move" operation.
     * Calling this method actually forwards the call to
     * {@link #newMove(LdapDN, LdapDN, Rdn, boolean)} with unchanged
     * <tt>newParentName</tt>. 
     */
    public Operation newModifyRn( LdapDN oldName, Rdn newRdn, boolean deleteOldRn ) throws NamingException
    {
        LdapDN newParentName = ( LdapDN ) oldName.clone();
        newParentName.remove( oldName.size() - 1 );
        
        return newMove( oldName, newParentName, newRdn, deleteOldRn );
    }


    /**
     * Returns a new {@link Operation} that performs "move" operation.
     * Calling this method actually forwards the call to
     * {@link #newMove(LdapDN, LdapDN, Rdn, boolean)} with unchanged
     * <tt>newRdn</tt> and '<tt>true</tt>' <tt>deleteOldRn</tt>. 
     */
    public Operation newMove( LdapDN oldName, LdapDN newParentName ) throws NamingException
    {
        return newMove( oldName, newParentName, oldName.getRdn(), true );
    }


    /**
     * Returns a new {@link Operation} that performs "move" operation.
     * Please note this operation is the most fragile operation I've written
     * so it should be reviewed completely again.
     */
    public Operation newMove( LdapDN oldName, LdapDN newParentName, Rdn newRdn, boolean deleteOldRn )
        throws NamingException
    {
        // Prepare to create composite operations
        CSN csn = newCSN();
        CompositeOperation result = new CompositeOperation( csn );

        // Retrieve all subtree including the base entry
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope( SearchControls.SUBTREE_SCOPE );
        
        NamingEnumeration<ServerSearchResult> e = nexus.search( 
            new SearchOperationContext( registries, oldName, AliasDerefMode.DEREF_ALWAYS,
                    new PresenceNode( SchemaConstants.OBJECT_CLASS_AT_OID ), ctrl ) );

        while ( e.hasMore() )
        {
            ServerSearchResult sr = e.next();

            // Get the name of the old entry
            LdapDN oldEntryName = sr.getDn();
            oldEntryName.normalize( attributeRegistry.getNormalizerMapping() );

            // Delete the old entry
            result.add( 
                new ReplaceAttributeOperation( 
                    csn, 
                    oldEntryName, 
                    new DefaultServerAttribute( 
                        Constants.ENTRY_DELETED,
                        attributeRegistry.lookup( Constants.ENTRY_DELETED ),
                        "TRUE" ) ) );

            // Get the old entry attributes and replace RDN if required
            ServerEntry entry = sr.getServerEntry();
            
            if ( oldEntryName.size() == oldName.size() )
            {
                if ( deleteOldRn )
                {
                    // Delete the old RDN attribute value
                    String oldRDNAttributeID = oldName.getRdn().getUpType();
                    EntryAttribute oldRDNAttribute = entry.get( oldRDNAttributeID );
                    
                    if ( oldRDNAttribute != null )
                    {
                        boolean removed = oldRDNAttribute.remove( (String)oldName.getRdn().getUpValue() );
                        
                        if ( removed && oldRDNAttribute.size() == 0 )
                        {
                            // Now an empty attribute, remove it.
                            entry.removeAttributes( oldRDNAttributeID );
                        }
                    }
                }
                
                // Add the new RDN attribute value.
                String newRDNAttributeID = newRdn.getUpType();
                String newRDNAttributeValue = ( String ) newRdn.getUpValue();
                EntryAttribute newRDNAttribute = entry.get( newRDNAttributeID );
                
                if ( newRDNAttribute != null )
                {
                    newRDNAttribute.add( newRDNAttributeValue );
                }
                else
                {
                    entry.put( newRDNAttributeID, newRDNAttributeValue );
                }
            }

            // Calculate new name from newParentName, oldEntryName, and newRdn.
            LdapDN newEntryName = ( LdapDN ) newParentName.clone();
            newEntryName.add( newRdn );
            
            for ( int i = oldEntryName.size() - newEntryName.size(); i > 0; i-- )
            {
                newEntryName.add( oldEntryName.get( oldEntryName.size() - i ) );
            }
            
            newEntryName.normalize( attributeRegistry.getNormalizerMapping() );

            // Add the new entry
            result.add( newAdd( csn, newEntryName, entry ) );

            // Add default operations to the old entry.
            // Please note that newAdd() already added default operations
            // to the new entry. 
            addDefaultOperations( result, csn, oldEntryName );
        }

        return result;
    }


    /**
     * Make sure the specified <tt>newEntryName</tt> already exists.  It
     * checked {@link Constants#ENTRY_DELETED} additionally to see if the
     * entry actually exists in a {@link Partition} but maked as deleted.
     *
     * @param newEntryName makes sure an entry already exists.
     */
    private void checkBeforeAdd( LdapDN newEntryName ) throws NamingException
    {
        if ( nexus.hasEntry( new EntryOperationContext( registries, newEntryName ) ) )
        {
            ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, newEntryName ) );
            EntryAttribute deleted = entry.get( Constants.ENTRY_DELETED );
            Object value = deleted == null ? null : deleted.get();

            /*
             * Check first if the entry has been marked as deleted before
             * throwing an exception and delete the entry if so and return
             * without throwing an exception.
             */
            if ( value != null && "TRUE".equalsIgnoreCase( value.toString() ) )
            {
                return;
            }

            throw new NameAlreadyBoundException( newEntryName.toString() + " already exists." );
        }
    }


    /**
     * Adds default {@link Operation}s that should be followed by all
     * JNDI/LDAP operations except "add/bind" operation.  This method
     * currently adds only one attribute, {@link Constants#ENTRY_CSN}.
     * @return what you specified as a parameter to enable invocation chaining
     */
    private CompositeOperation addDefaultOperations( CompositeOperation result, CSN csn, LdapDN normalizedName ) throws NamingException
    {
        result.add( 
            new ReplaceAttributeOperation( 
                csn, 
                normalizedName, 
                new DefaultServerAttribute( 
                    Constants.ENTRY_DELETED,
                    attributeRegistry.lookup( Constants.ENTRY_CSN ),
                    csn.toOctetString() ) ) );

        return result;
    }

    /**
     * Creates new {@link CSN} from the {@link CSNFactory} which was specified
     * in the constructor.
     */
    private CSN newCSN()
    {
        return csnFactory.newInstance( replicaId );
    }
}
