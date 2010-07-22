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
package org.apache.directory.server.core.exception;


import java.util.List;

import org.apache.commons.collections.map.LRUMap;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.EmptyCursor;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapAliasException;
import org.apache.directory.shared.ldap.exception.LdapAttributeInUseException;
import org.apache.directory.shared.ldap.exception.LdapEntryAlreadyExistsException;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.exception.LdapOperationException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An {@link org.apache.directory.server.core.interceptor.Interceptor} that detects any operations that breaks integrity
 * of {@link Partition} and terminates the current invocation chain by
 * throwing a {@link Exception}. Those operations include when an entry
 * already exists at a DN and is added once again to the same DN.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ExceptionInterceptor extends BaseInterceptor
{
    private PartitionNexus nexus;
    private DN subschemSubentryDn;

    /**
     * A cache to store entries which are not aliases. 
     * It's a speedup, we will be able to avoid backend lookups.
     * 
     * Note that the backend also use a cache mechanism, but for performance gain, it's good 
     * to manage a cache here. The main problem is that when a user modify the parent, we will
     * have to update it at three different places :
     * - in the backend,
     * - in the partition cache,
     * - in this cache.
     * 
     * The update of the backend and partition cache is already correctly handled, so we will
     * just have to offer an access to refresh the local cache. This should be done in 
     * delete, modify and move operations.
     * 
     * We need to be sure that frequently used DNs are always in cache, and not discarded.
     * We will use a LRU cache for this purpose. 
     */
    private final LRUMap notAliasCache = new LRUMap( DEFAULT_CACHE_SIZE );

    /** Declare a default for this cache. 100 entries seems to be enough */
    private static final int DEFAULT_CACHE_SIZE = 100;

    /** SchemaManager instance */
    private SchemaManager schemaManager;

    /** The ObjectClass AttributeType */
    private static AttributeType OBJECT_CLASS_AT;


    /**
     * Creates an interceptor that is also the exception handling service.
     */
    public ExceptionInterceptor()
    {
    }


    public void init( DirectoryService directoryService ) throws LdapException
    {
        nexus = directoryService.getPartitionNexus();
        Value<?> attr = nexus.getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        subschemSubentryDn = new DN( attr.getString(), directoryService.getSchemaManager() );
        schemaManager = directoryService.getSchemaManager();

        // look up some constant information
        OBJECT_CLASS_AT = schemaManager.getAttributeType( SchemaConstants.OBJECT_CLASS_AT );
    }


    public void destroy()
    {
    }


    /**
     * In the pre-invocation state this interceptor method checks to see if the entry to be added already exists.  If it
     * does an exception is raised.
     */
    public void add( NextInterceptor nextInterceptor, AddOperationContext addContext ) throws LdapException
    {
        DN name = addContext.getDn();

        if ( subschemSubentryDn.getNormName().equals( name.getNormName() ) )
        {
            throw new LdapEntryAlreadyExistsException( I18n.err( I18n.ERR_249 ) );
        }

        // check if the entry already exists
        if ( nextInterceptor.hasEntry( new EntryOperationContext( addContext.getSession(), name ) ) )
        {
            LdapEntryAlreadyExistsException ne = new LdapEntryAlreadyExistsException( 
                I18n.err( I18n.ERR_250_ENTRY_ALREADY_EXISTS, name.getName() ) );
            throw ne;
        }

        DN suffix = nexus.findSuffix( name );

        // we're adding the suffix entry so just ignore stuff to mess with the parent
        if ( suffix.equals( name ) )
        {
            nextInterceptor.add( addContext );
            return;
        }

        DN parentDn = ( DN ) name.clone();
        parentDn.remove( name.size() - 1 );

        // check if we're trying to add to a parent that is an alias
        boolean notAnAlias;

        synchronized ( notAliasCache )
        {
            notAnAlias = notAliasCache.containsKey( parentDn.getNormName() );
        }

        if ( !notAnAlias )
        {
            // We don't know if the parent is an alias or not, so we will launch a 
            // lookup, and update the cache if it's not an alias
            Entry attrs;

            try
            {
                attrs = addContext.lookup( parentDn, ByPassConstants.LOOKUP_BYPASS );
            }
            catch ( Exception e )
            {
                LdapNoSuchObjectException e2 = new LdapNoSuchObjectException( 
                    I18n.err( I18n.ERR_251_PARENT_NOT_FOUND, parentDn.getName() ) );
                throw e2;
            }

            EntryAttribute objectClass = ( ( ClonedServerEntry ) attrs ).getOriginalEntry().get(
                OBJECT_CLASS_AT );

            if ( objectClass.contains( SchemaConstants.ALIAS_OC ) )
            {
                String msg = I18n.err( I18n.ERR_252, name.getName() );
                LdapAliasException e = new LdapAliasException( msg );
                //e.setResolvedName( new DN( parentDn.getName() ) );
                throw e;
            }
            else
            {
                synchronized ( notAliasCache )
                {
                    notAliasCache.put( parentDn.getNormName(), parentDn );
                }
            }
        }

        nextInterceptor.add( addContext );
    }


    /**
     * Checks to make sure the entry being deleted exists, and has no children, otherwise throws the appropriate
     * LdapException.
     */
    public void delete( NextInterceptor nextInterceptor, DeleteOperationContext deleteContext ) throws LdapException
    {
        DN dn = deleteContext.getDn();

        if ( dn.equals( subschemSubentryDn ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_253,
                subschemSubentryDn ) );
        }

        nextInterceptor.delete( deleteContext );

        // Update the alias cache
        synchronized ( notAliasCache )
        {
            if ( notAliasCache.containsKey( dn.getNormName() ) )
            {
                notAliasCache.remove( dn.getNormName() );
            }
        }
    }


    /**
     * Checks to see the base being searched exists, otherwise throws the appropriate LdapException.
     */
    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext listContext )
        throws LdapException
    {
        if ( listContext.getDn().getNormName().equals( subschemSubentryDn.getNormName() ) )
        {
            // there is nothing under the schema subentry
            return new BaseEntryFilteringCursor( new EmptyCursor<Entry>(), listContext );
        }

        // check if entry to search exists
        String msg = "Attempt to search under non-existant entry: ";
        assertHasEntry( listContext, msg, listContext.getDn() );

        return nextInterceptor.list( listContext );
    }


    /**
     * Checks to see the base being searched exists, otherwise throws the appropriate LdapException.
     */
    public Entry lookup( NextInterceptor nextInterceptor, LookupOperationContext lookupContext ) throws LdapException
    {
        DN dn = lookupContext.getDn();

        if ( dn.equals( subschemSubentryDn ) )
        {
            return nexus.getRootDSE( null );
        }

        Entry result = nextInterceptor.lookup( lookupContext );

        if ( result == null )
        {
            LdapNoSuchObjectException e = new LdapNoSuchObjectException( "Attempt to lookup non-existant entry: "
                + dn.getName() );

            throw e;
        }

        return result;
    }


    /**
     * Checks to see the entry being modified exists, otherwise throws the appropriate LdapException.
     */
    public void modify( NextInterceptor nextInterceptor, ModifyOperationContext modifyContext ) throws LdapException
    {
        // check if entry to modify exists
        String msg = "Attempt to modify non-existant entry: ";

        // handle operations against the schema subentry in the schema service
        // and never try to look it up in the nexus below
        if ( modifyContext.getDn().equals( subschemSubentryDn ) )
        {
            nextInterceptor.modify( modifyContext );
            return;
        }

        // Check that the entry we read at the beginning exists. If
        // not, we will throw an exception here
        assertHasEntry( modifyContext, msg );

        Entry entry = modifyContext.getEntry();

        List<Modification> items = modifyContext.getModItems();

        // Check that we aren't adding a value that already exists in the current entry
        for ( Modification item : items )
        {
            if ( item.getOperation() == ModificationOperation.ADD_ATTRIBUTE )
            {
                EntryAttribute modAttr = item.getAttribute();
                EntryAttribute entryAttr = entry.get( modAttr.getId() );

                if ( entryAttr != null )
                {
                    for ( Value<?> value : modAttr )
                    {
                        if ( entryAttr.contains( value ) )
                        {
                            throw new LdapAttributeInUseException( I18n.err( I18n.ERR_254_ADD_EXISTING_VALUE, value,
                                modAttr.getId() ) );
                        }
                    }
                }
            }
        }

        // Let's assume that the new modified entry may be an alias,
        // but we don't want to check that now...
        // We will simply remove the DN from the NotAlias cache.
        // It would be smarter to check the modified attributes, but
        // it would also be more complex.
        synchronized ( notAliasCache )
        {
            if ( notAliasCache.containsKey( modifyContext.getDn().getNormName() ) )
            {
                notAliasCache.remove( modifyContext.getDn().getNormName() );
            }
        }

        nextInterceptor.modify( modifyContext );
    }


    /**
     * Checks to see the entry being renamed exists, otherwise throws the appropriate LdapException.
     */
    public void rename( NextInterceptor nextInterceptor, RenameOperationContext renameContext ) throws LdapException
    {
        DN dn = renameContext.getDn();

        if ( dn.equals( subschemSubentryDn ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_255,
                subschemSubentryDn, subschemSubentryDn ) );
        }

        // check to see if target entry exists
        DN newDn = renameContext.getNewDn();

        if ( nextInterceptor.hasEntry( new EntryOperationContext( renameContext.getSession(), newDn ) ) )
        {
            LdapEntryAlreadyExistsException e;
            e = new LdapEntryAlreadyExistsException( I18n.err( I18n.ERR_250_ENTRY_ALREADY_EXISTS, newDn.getName() ) );
            //e.setResolvedName( new DN( newDn.getName() ) );
            throw e;
        }

        // Remove the previous entry from the notAnAlias cache
        synchronized ( notAliasCache )
        {
            if ( notAliasCache.containsKey( dn.getNormName() ) )
            {
                notAliasCache.remove( dn.getNormName() );
            }
        }

        nextInterceptor.rename( renameContext );
    }


    /**
     * {@inheritDoc}
     */
    public void move( NextInterceptor nextInterceptor, MoveOperationContext moveContext ) throws LdapException
    {
        DN oriChildName = moveContext.getDn();

        if ( oriChildName.equals( subschemSubentryDn ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_258,
                subschemSubentryDn, subschemSubentryDn ) );
        }

        nextInterceptor.move( moveContext );

        // Remove the original entry from the NotAlias cache, if needed
        synchronized ( notAliasCache )
        {
            if ( notAliasCache.containsKey( oriChildName.getNormName() ) )
            {
                notAliasCache.remove( oriChildName.getNormName() );
            }
        }
    }


    /**
     * Checks to see the entry being moved exists, and so does its parent, otherwise throws the appropriate
     * LdapException.
     */
    public void moveAndRename( NextInterceptor nextInterceptor, MoveAndRenameOperationContext moveAndRenameContext )
        throws LdapException
    {
        DN oldDn = moveAndRenameContext.getDn();

        // Don't allow M&R in the SSSE
        if ( oldDn.equals( subschemSubentryDn ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_258,
                subschemSubentryDn, subschemSubentryDn ) );
        }

        // Remove the original entry from the NotAlias cache, if needed
        synchronized ( notAliasCache )
        {
            if ( notAliasCache.containsKey( oldDn.getNormName() ) )
            {
                notAliasCache.remove( oldDn.getNormName() );
            }
        }

        nextInterceptor.moveAndRename( moveAndRenameContext );
    }


    /**
     * Checks to see the entry being searched exists, otherwise throws the appropriate LdapException.
     */
    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext searchContext )
        throws LdapException
    {
        DN base = searchContext.getDn();

        try
        {
            EntryFilteringCursor cursor = nextInterceptor.search( searchContext );

            // Check that if the cursor is empty, it's not because the DN is invalid.
            if ( !cursor.available() && !base.isEmpty() && !subschemSubentryDn.equals( base ) )
            {
                // We just check that the entry exists only if we didn't found any entry
                assertHasEntry( searchContext, "Attempt to search under non-existant entry:", searchContext.getDn() );
            }

            return cursor;
        }
        catch ( Exception ne )
        {
            String msg = I18n.err( I18n.ERR_259 );
            assertHasEntry( searchContext, msg, searchContext.getDn() );
            throw new LdapOperationException( msg );
        }
    }


    /**
     * Asserts that an entry is present and as a side effect if it is not, creates a LdapNoSuchObjectException, which is
     * used to set the before exception on the invocation - eventually the exception is thrown.
     *
     * @param msg        the message to prefix to the distinguished name for explanation
     * @throws Exception if the entry does not exist
     * @param nextInterceptor the next interceptor in the chain
     */
    private void assertHasEntry( OperationContext opContext, String msg ) throws LdapException
    {
        DN dn = opContext.getDn();

        if ( subschemSubentryDn.equals( dn ) )
        {
            return;
        }

        if ( opContext.getEntry() == null )
        {
            LdapNoSuchObjectException e;

            if ( msg != null )
            {
                e = new LdapNoSuchObjectException( msg + dn.getName() );
            }
            else
            {
                e = new LdapNoSuchObjectException( dn.getName() );
            }

            throw e;
        }
    }


    /**
     * Asserts that an entry is present and as a side effect if it is not, creates a LdapNoSuchObjectException, which is
     * used to set the before exception on the invocation - eventually the exception is thrown.
     *
     * @param msg        the message to prefix to the distinguished name for explanation
     * @param dn         the distinguished name of the entry that is asserted
     * @throws Exception if the entry does not exist
     * @param nextInterceptor the next interceptor in the chain
     */
    private void assertHasEntry( OperationContext opContext, String msg, DN dn ) throws LdapException
    {
        if ( subschemSubentryDn.equals( dn ) )
        {
            return;
        }

        if ( !opContext.hasEntry( dn, ByPassConstants.HAS_ENTRY_BYPASS ) )
        {
            LdapNoSuchObjectException e;

            if ( msg != null )
            {
                e = new LdapNoSuchObjectException( msg + dn.getName() );
            }
            else
            {
                e = new LdapNoSuchObjectException( dn.getName() );
            }

            throw e;
        }
    }
}
