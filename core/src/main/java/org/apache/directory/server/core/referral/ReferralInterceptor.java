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
package org.apache.directory.server.core.referral;


import javax.naming.Context;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.ReferralManager;
import org.apache.directory.server.core.ReferralManagerImpl;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.codec.util.LdapURLEncodingException;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.util.LdapURL;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An service which is responsible referral handling behavoirs.  It manages 
 * referral handling behavoir when the {@link Context#REFERRAL} is implicitly
 * or explicitly set to "ignore", when set to "throw" and when set to "follow". 
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ReferralInterceptor extends BaseInterceptor
{
    private static final Logger LOG = LoggerFactory.getLogger( ReferralInterceptor.class );

    private PartitionNexus nexus;

    /** The global schemaManager */
    private SchemaManager schemaManager;

    /** The referralManager */
    private ReferralManager referralManager;

    /** A normalized form for the SubschemaSubentry DN */
    private DN subschemaSubentryDn;

    /** The ObjectClass AttributeType */
    private static AttributeType OBJECT_CLASS_AT;


    static private void checkRefAttributeValue( Value<?> value ) throws LdapException, LdapURLEncodingException
    {
        StringValue ref = ( StringValue ) value;

        String refVal = ref.getString();

        LdapURL ldapUrl = new LdapURL( refVal );

        // We have a LDAP URL, we have to check that :
        // - we don't have scope specifier
        // - we don't have filters
        // - we don't have attribute description list
        // - we don't have extensions
        // - the DN is not empty

        if ( ldapUrl.getScope() != SearchScope.OBJECT )
        {
            // This is the default value if we don't have any scope
            // Let's assume that it's incorrect if we get something
            // else in the LdapURL
            String message = I18n.err( I18n.ERR_36 );
            LOG.error( message );
            throw new LdapException( message );
        }

        if ( !StringTools.isEmpty( ldapUrl.getFilter() ) )
        {
            String message = I18n.err( I18n.ERR_37 );
            LOG.error( message );
            throw new LdapException( message );
        }

        if ( ( ldapUrl.getAttributes() != null ) && ( ldapUrl.getAttributes().size() != 0 ) )
        {
            String message = I18n.err( I18n.ERR_38 );
            LOG.error( message );
            throw new LdapException( message );
        }

        if ( ( ldapUrl.getExtensions() != null ) && ( ldapUrl.getExtensions().size() != 0 ) )
        {
            String message = I18n.err( I18n.ERR_39 );
            LOG.error( message );
            throw new LdapException( message );
        }

        if ( ( ldapUrl.getExtensions() != null ) && ( ldapUrl.getExtensions().size() != 0 ) )
        {
            String message = I18n.err( I18n.ERR_40 );
            LOG.error( message );
            throw new LdapException( message );
        }

        DN dn = ldapUrl.getDn();

        if ( ( dn == null ) || dn.isEmpty() )
        {
            String message = I18n.err( I18n.ERR_41 );
            LOG.error( message );
            throw new LdapException( message );
        }
    }


    // This will suppress PMD.EmptyCatchBlock warnings in this method
    @SuppressWarnings("PMD.EmptyCatchBlock")
    static private boolean isReferral( Entry entry ) throws LdapException
    {
        // Check that the entry is not null, otherwise return FALSE.
        // This is typically to cover the case where the entry has not 
        // been added into the context because it does not exists.
        if ( entry == null )
        {
            return false;
        }

        EntryAttribute oc = entry.get( OBJECT_CLASS_AT );

        if ( oc == null )
        {
            LOG.warn( "could not find objectClass attribute in entry: " + entry );
            return false;
        }

        if ( !oc.contains( SchemaConstants.REFERRAL_OC ) )
        {
            return false;
        }
        else
        {
            // We have a referral ObjectClass, let's check that the ref is
            // valid, accordingly to the RFC

            // Get the 'ref' attributeType
            EntryAttribute refAttr = entry.get( SchemaConstants.REF_AT );

            if ( refAttr == null )
            {
                // very unlikely, as we have already checked the entry in SchemaInterceptor
                String message = I18n.err( I18n.ERR_42 );
                LOG.error( message );
                throw new LdapException( message );
            }

            for ( Value<?> value : refAttr )
            {
                try
                {
                    checkRefAttributeValue( value );
                }
                catch ( LdapURLEncodingException luee )
                {
                    // Either the URL is invalid, or it's not a LDAP URL.
                    // we will just ignore this LdapURL.
                }
            }

            return true;
        }
    }


    public void init( DirectoryService directoryService ) throws LdapException
    {
        nexus = directoryService.getPartitionNexus();
        schemaManager = directoryService.getSchemaManager();

        // Initialize the referralManager
        referralManager = new ReferralManagerImpl( directoryService );
        directoryService.setReferralManager( referralManager );

        Value<?> subschemaSubentry = nexus.getRootDSE( null ).get( SchemaConstants.SUB_SCHEMA_SUBENTRY_AT ).get();
        subschemaSubentryDn = directoryService.getDNFactory().create( subschemaSubentry.getString() );

        // look up some constant information
        OBJECT_CLASS_AT = schemaManager.getAttributeType( SchemaConstants.OBJECT_CLASS_AT );
    }


    /**
     * Add an entry into the server. We have 3 cases :
     * (1) The entry does not have any parent referral and is not a referral itself
     * (2) The entry does not have any parent referral and is a referral itself
     * (3) The entry has a parent referral
     * 
     * Case (1) is easy : we inject the entry into the server and we are done.
     * Case (2) is the same as case (1), but we have to update the referral manager.
     * Case (3) is handled by the LdapProcotol handler, as we have to return a 
     * LdapResult containing a list of this entry's parent's referrals URL, if the 
     * ManageDSAIT control is not present, or the parent's entry if the control 
     * is present. 
     * 
     * Of course, if the entry already exists, nothing will be done, as we will get an
     * entryAlreadyExists error.
     *  
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        Entry entry = addContext.getEntry();

        // Check if the entry is a referral itself
        boolean isReferral = isReferral( entry );

        // We add the entry into the server
        next.add( addContext );

        // If the addition is successful, we update the referralManager 
        if ( isReferral )
        {
            // We have to add it to the referralManager
            referralManager.lockWrite();

            referralManager.addReferral( entry );

            referralManager.unlock();
        }
    }


    /**
     * Delete an entry in the server. We have 4 cases :
     * (1) the entry is not a referral and does not have a parent referral
     * (2) the entry is not a referral but has a parent referral
     * (3) the entry is a referral
     * 
     * Case (1) is handled by removing the entry from the server
     * In case (2), we return an exception build using the parent referral 
     * For case(3), we remove the entry from the server and remove the referral
     * from the referral manager.
     * 
     * If the entry does not exist in the server, we will get a NoSuchObject error
     */
    public void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException
    {
        // First delete the entry into the server
        next.delete( deleteContext );

        Entry entry = deleteContext.getEntry();

        // Check if the entry exists and is a referral itself
        // If so, we have to update the referralManager
        if ( ( entry != null ) && isReferral( entry ) )
        {
            // We have to remove it from the referralManager
            referralManager.lockWrite();

            referralManager.removeReferral( entry );

            referralManager.unlock();
        }
    }


    /**
     * {@inheritDoc}
     **/
    public void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException
    {
        DN newDn = moveContext.getNewDn();

        // Check if the entry is a referral itself
        boolean isReferral = isReferral( moveContext.getOriginalEntry() );

        next.move( moveContext );

        if ( isReferral )
        {
            // Update the referralManager
            referralManager.lockWrite();

            referralManager.addReferral( moveContext.getModifiedEntry() );
            referralManager.removeReferral( moveContext.getOriginalEntry() );

            referralManager.unlock();
        }
    }


    /**
     * {@inheritDoc}
     **/
    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext ) throws LdapException
    {
        // Check if the entry is a referral itself
        boolean isReferral = isReferral( moveAndRenameContext.getEntry() );

        next.moveAndRename( moveAndRenameContext );

        if ( isReferral )
        {
            // Update the referralManager
            Entry newEntry = moveAndRenameContext.getModifiedEntry();

            referralManager.lockWrite();

            referralManager.addReferral( newEntry );
            referralManager.removeReferral( moveAndRenameContext.getEntry() );

            referralManager.unlock();
        }
    }


    /**
     * {@inheritDoc}
     **/
    public void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        // Check if the entry is a referral itself
        boolean isReferral = isReferral( renameContext.getEntry() );

        next.rename( renameContext );

        if ( isReferral )
        {
            // Update the referralManager
            LookupOperationContext lookupContext = new LookupOperationContext( renameContext.getSession(), renameContext
                .getNewDn() );

            Entry newEntry = nexus.lookup( lookupContext );

            referralManager.lockWrite();

            referralManager.addReferral( newEntry );
            referralManager.removeReferral( renameContext.getEntry().getOriginalEntry() );

            referralManager.unlock();
        }
    }


    /**
     * Modify an entry in the server.
     */
    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        DN dn = modifyContext.getDn();

        // handle a normal modify without following referrals
        next.modify( modifyContext );

        // Check if we are trying to modify the schema or the rootDSE,
        // if so, we don't modify the referralManager
        if ( dn.isEmpty() || dn.equals( subschemaSubentryDn ) )
        {
            // Do nothing
            return;
        }

        // Update the referralManager. We have to read the entry again
        // as it has been modified, before updating the ReferralManager
        // TODO: this can be spare, as we already have the altered entry
        // into the opContext, but for an unknow reason, this will fail
        // on eferral tests...
        LookupOperationContext lookupContext = new LookupOperationContext( modifyContext.getSession(), dn );

        Entry newEntry = nexus.lookup( lookupContext );

        // Update the referralManager.
        // Check that we have the entry, just in case
        // TODO : entries should be locked until the operation is done on it.
        if ( newEntry != null )
        {
            referralManager.lockWrite();

            if ( referralManager.isReferral( newEntry.getDn() ) )
            {
                referralManager.removeReferral( modifyContext.getEntry() );
                referralManager.addReferral( newEntry );
            }

            referralManager.unlock();
        }
    }
}
