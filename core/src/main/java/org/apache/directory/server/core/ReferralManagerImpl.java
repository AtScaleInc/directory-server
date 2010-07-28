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
package org.apache.directory.server.core;


import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapOperationException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.tree.DnBranchNode;


/**
 * Implement a referral Manager, handling the requests from the LDAP protocol.
 * <br>
 * Referrals are stored in a tree, where leaves are the referrals. We are using 
 * the very same structure than for the partition manager.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ReferralManagerImpl implements ReferralManager
{
    /** The referrals tree */
    private DnBranchNode<Entry> referrals;

    /** A lock to guarantee the manager consistency */
    private ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();

    /** A storage for the ObjectClass attributeType */
    private AttributeType OBJECT_CLASS_AT;

    
    /**
     * 
     * Creates a new instance of ReferralManagerImpl.
     *
     * @param directoryService The directory service
     * @throws Exception If we can't initialize the manager
     */
    public ReferralManagerImpl( DirectoryService directoryService ) throws LdapException
    {
        lockWrite();
        
        referrals = new DnBranchNode<Entry>();
        PartitionNexus nexus = directoryService.getPartitionNexus();

        Set<String> suffixes = nexus.listSuffixes();
        OBJECT_CLASS_AT = directoryService.getSchemaManager().getAttributeType( SchemaConstants.OBJECT_CLASS_AT );

        init( directoryService, suffixes.toArray( new String[]{} ) );

        unlock();
    }
    
    
    /**
     * Get a read-lock on the referralManager. 
     * No read operation can be done on the referralManager if this
     * method is not called before.
     */
    public void lockRead()
    {
        mutex.readLock().lock();
    }
    
    
    /**
     * Get a write-lock on the referralManager. 
     * No write operation can be done on the referralManager if this
     * method is not called before.
     */
    public void lockWrite()
    {
        mutex.writeLock().lock();
    }

    
    /**
     * Release the read-write lock on the referralManager. 
     * This method must be called after having read or modified the
     * ReferralManager
     */
    public void unlock()
    {
        if ( mutex.isWriteLockedByCurrentThread() )
        {
            mutex.writeLock().unlock();
        }
        else
        {
            mutex.readLock().unlock();
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    // This will suppress PMD.EmptyCatchBlock warnings in this method
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public void addReferral( Entry entry )
    {
        try
        {
            ((DnBranchNode<Entry>)referrals).add( entry.getDn(), entry );
        }
        catch ( LdapException ne )
        {
            // Do nothing
        }
    }


    /**
     * {@inheritDoc}
     */
    public void init( DirectoryService directoryService, String... suffixes ) throws LdapException
    {
        ExprNode referralFilter = new EqualityNode<String>( OBJECT_CLASS_AT, 
            new StringValue( SchemaConstants.REFERRAL_OC ) );

        // Lookup for each entry with the ObjectClass = Referral value
        SearchControls searchControl = new SearchControls();
        searchControl.setReturningObjFlag( false );
        searchControl.setSearchScope( SearchControls.SUBTREE_SCOPE );
        
        CoreSession adminSession = directoryService.getAdminSession();
        PartitionNexus nexus = directoryService.getPartitionNexus();

        for ( String suffix:suffixes )
        {
            // We will store each entry's DN into the Referral tree
            DN suffixDn = DNFactory.create( suffix, directoryService.getSchemaManager() );
            
            SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, suffixDn, referralFilter, searchControl );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.DEREF_ALWAYS );
            EntryFilteringCursor cursor = nexus.search( searchOperationContext );
            
            try
            {
                // Move to the first entry in the cursor
                cursor.beforeFirst();
                
                while ( cursor.next() ) 
                {
                    Entry entry = cursor.get();
    
                    // Lock the referralManager
                    lockWrite();
                    
                    // Add it at the right place
                    addReferral( entry );
                    
                    // Unlock the referralManager
                    unlock();
                }
                
                cursor.close();
            }
            catch ( Exception e )
            {
                throw new LdapOperationException( e.getMessage() );
            }
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public void remove( DirectoryService directoryService, DN suffix ) throws Exception
    {
        ExprNode referralFilter = new EqualityNode<String>( OBJECT_CLASS_AT, 
            new StringValue( SchemaConstants.REFERRAL_OC ) );

        // Lookup for each entry with the ObjectClass = Referral value
        SearchControls searchControl = new SearchControls();
        searchControl.setReturningObjFlag( false );
        searchControl.setSearchScope( SearchControls.SUBTREE_SCOPE );
        
        CoreSession adminSession = directoryService.getAdminSession();
        PartitionNexus nexus = directoryService.getPartitionNexus();

        // We will store each entry's DN into the Referral tree
        SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, suffix,
            referralFilter, searchControl );
        searchOperationContext.setAliasDerefMode( AliasDerefMode.DEREF_ALWAYS );
        EntryFilteringCursor cursor = nexus.search( searchOperationContext );
        
        // Move to the first entry in the cursor
        cursor.beforeFirst();
        
        while ( cursor.next() ) 
        {
            Entry entry = cursor.get();
            
            // Add it at the right place
            removeReferral( entry );
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public boolean hasParentReferral( DN dn )
    {
        return referrals.hasParentElement( dn );
    }


    /**
     * {@inheritDoc}
     */
    public Entry getParentReferral( DN dn )
    {
        if ( !hasParentReferral( dn ) )
        {
            return null;
        }
        
        return referrals.getParentElement( dn );
    }

    
    /**
     * {@inheritDoc}
     */
    public boolean isReferral( DN dn )
    {
        Entry parent = referrals.getParentElement( dn );
        
        if ( parent != null )
        {
            return dn.equals( parent.getDn() );
        }
        else
        {
            return false;
        }
    }


    /**
     * {@inheritDoc}
     */
    public void removeReferral( Entry entry )
    {
        referrals.remove( entry );
    }
}
