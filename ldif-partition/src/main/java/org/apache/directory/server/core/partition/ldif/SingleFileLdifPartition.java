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

package org.apache.directory.server.core.partition.ldif;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.naming.InvalidNameException;

import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.impl.avl.AvlStore;
import org.apache.directory.shared.ldap.model.constants.SchemaConstants;
import org.apache.directory.shared.ldap.model.entry.DefaultEntry;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.exception.LdapException;
import org.apache.directory.shared.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.model.ldif.LdifEntry;
import org.apache.directory.shared.ldap.model.ldif.LdifReader;
import org.apache.directory.shared.ldap.model.ldif.LdifUtils;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.apache.directory.shared.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Partition implementation backed by a single LDIF file.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SingleFileLdifPartition extends AbstractLdifPartition
{

    /** the LDIF file holding the partition's data */
    private RandomAccessFile ldifFile;

    /** flag to enable/disable re-writing in-memory partition data back to file, default is set to true */
    private volatile boolean enableRewriting = true;

    /** flag used internally to detect if partition data was updated in memory but not on disk */
    private boolean dirty = false;

    /** lock for serializing the operations on the backing LDIF file */
    private Object lock = new Object();

    private static Logger LOG = LoggerFactory.getLogger( SingleFileLdifPartition.class );


    /**
     * Creates a new instance of SingleFileLdifPartition.
     */
    public SingleFileLdifPartition()
    {
        wrappedPartition = new AvlPartition();
    }


    @Override
    protected void doInit() throws InvalidNameException, Exception
    {
        if ( getPartitionPath() == null )
        {
            throw new IllegalArgumentException( "Partition path cannot be null" );
        }

        File partitionFile = new File( getPartitionPath() );
        if ( partitionFile.exists() && !partitionFile.isFile() )
        {
            throw new IllegalArgumentException( "Partition path must be a LDIF file" );
        }

        ldifFile = new RandomAccessFile( partitionFile, "rws" );

        // Initialize the AvlPartition
        wrappedPartition.setId( id );
        wrappedPartition.setSuffix( suffix );
        wrappedPartition.setSchemaManager( schemaManager );
        wrappedPartition.initialize();

        this.searchEngine = wrappedPartition.getSearchEngine();

        LOG.debug( "id is : {}", wrappedPartition.getId() );

        // Initialize the suffixDirectory : it's a composition
        // of the workingDirectory followed by the suffix
        if ( ( suffix == null ) || ( suffix.isEmpty() ) )
        {
            String msg = I18n.err( I18n.ERR_150 );
            LOG.error( msg );
            throw new LdapInvalidDnException( msg );
        }

        if ( !suffix.isSchemaAware() )
        {
            suffix.apply( schemaManager );
        }

        loadEntries();
    }


    /**
     * load the entries from the LDIF file if present
     * @throws Exception
     */
    private void loadEntries() throws Exception
    {
        RandomAccessLdifReader parser = new RandomAccessLdifReader();

        Iterator<LdifEntry> itr = parser.iterator();

        if ( !itr.hasNext() )
        {
            return;
        }

        LdifEntry ldifEntry = itr.next();

        contextEntry = new DefaultEntry( schemaManager, ldifEntry.getEntry() );

        AvlStore store = wrappedPartition.getStore();

        if ( suffix.equals( contextEntry.getDn() ) )
        {
            addMandatoryOpAt( contextEntry );
            store.add( contextEntry );
        }
        else
        {
            throw new LdapException( "The given LDIF file doesn't contain the context entry" );
        }

        while ( itr.hasNext() )
        {
            ldifEntry = itr.next();

            Entry entry = new DefaultEntry( schemaManager, ldifEntry.getEntry() );

            addMandatoryOpAt( entry );
            store.add( entry );
        }

        parser.close();
    }


    public void bind( BindOperationContext bindContext ) throws LdapException
    {
        wrappedPartition.bind( bindContext );
    }


    @Override
    public void add( Entry entry ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.add( entry );

            if ( contextEntry == null )
            {
                if ( entry.getDn().equals( suffix ) )
                {
                    contextEntry = entry;
                }
            }

            dirty = true;
            rewritePartitionData();
        }
    }


    @Override
    public void modify( ModifyOperationContext modifyContext ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.modify( modifyContext );
            dirty = true;
            rewritePartitionData();
        }
    }


    @Override
    public void rename( RenameOperationContext renameContext ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.rename( renameContext );
            dirty = true;
            rewritePartitionData();
        }
    }


    @Override
    public void move( MoveOperationContext moveContext ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.move( moveContext );
            dirty = true;
            rewritePartitionData();
        }
    }


    @Override
    public void moveAndRename( MoveAndRenameOperationContext opContext ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.moveAndRename( opContext );
            dirty = true;
            rewritePartitionData();
        }
    }


    @Override
    public void delete( Dn entryDn ) throws LdapException
    {
        synchronized ( lock )
        {
            wrappedPartition.delete( entryDn );
            dirty = true;
            rewritePartitionData();
        }
    }


    /**
     * writes the partition's data to the file if {@link #enableRewriting} is set to true
     * and partition was modified since the last write or {@link #dirty} data. 
     * 
     * @throws LdapException
     */
    private void rewritePartitionData() throws LdapException
    {
        synchronized ( lock )
        {
            if ( !enableRewriting || !dirty )
            {
                return;
            }

            try
            {
                ldifFile.setLength( 0 ); // wipe the file clean

                Long suffixId = wrappedPartition.getEntryId( suffix );

                if( suffixId == null )
                {
                    contextEntry = null;
                    return;
                }
                
                IndexCursor<Long, Entry, Long> cursor = wrappedPartition.getOneLevelIndex().forwardCursor( suffixId );

                appendLdif( wrappedPartition.lookup( suffix ) );

                while ( cursor.next() )
                {
                    Long childId = cursor.get().getId();

                    Entry entry = wrappedPartition.lookup( cursor.get().getObject().getDn() );

                    appendLdif( entry );

                    appendRecursive( childId, null );
                }

                cursor.close();
                dirty = false;
            }
            catch ( LdapException e )
            {
                throw e;
            }
            catch ( Exception e )
            {
                throw new LdapException( e );
            }
        }
    }


    /**
     * appends all the entries present under a given entry, recursively
     *
     * @param entryId the base entry's id
     * @param cursorMap the open cursor map
     * @throws Exception
     */
    private void appendRecursive( Long entryId, Map<Long, IndexCursor<Long, Entry, Long>> cursorMap ) throws Exception
    {
        synchronized ( lock )
        {

            IndexCursor<Long, Entry, Long> cursor = null;
            
            if ( cursorMap == null )
            {
                cursorMap = new HashMap<Long, IndexCursor<Long, Entry, Long>>();
            }

            cursor = cursorMap.get( entryId );

            if ( cursor == null )
            {
                cursor = wrappedPartition.getOneLevelIndex().forwardCursor( entryId );
                cursor.beforeFirst();
                cursorMap.put( entryId, cursor );
            }

            if ( !cursor.next() ) // if this is a leaf entry's Dn
            {
                cursorMap.remove( entryId );
                cursor.close();
            }
            else
            {
                do
                {
                    IndexEntry<Long, Entry, Long> idxEntry = cursor.get();
                    Entry entry = wrappedPartition.lookup( idxEntry.getObject().getDn() );

                    Long childId = wrappedPartition.getEntryId( entry.getDn() );

                    appendLdif( entry );

                    appendRecursive( childId, cursorMap );
                }
                while ( cursor.next() );
                cursorMap.remove( entryId );
                cursor.close();
            }
        }
    }


    /**
     * append data to the LDIF file
     *
     * @param entry the entry to be written
     * @throws LdapException
     */
    private void appendLdif( Entry entry ) throws IOException, LdapException
    {
        synchronized ( lock )
        {
            String ldif = LdifUtils.convertToLdif( entry );
            ldifFile.write( Strings.getBytesUtf8(ldif + "\n") );
        }
    }

    /**
     * an LdifReader backed by a RandomAccessFile
     */
    private class RandomAccessLdifReader extends LdifReader
    {
        private long len;


        public RandomAccessLdifReader() throws LdapException
        {
            try
            {
                len = ldifFile.length();
                super.init();
            }
            catch ( IOException e )
            {
                LdapException le = new LdapException( e.getMessage(), e );
                le.initCause( e );

                throw le;
            }
        }


        @Override
        protected String getLine() throws IOException
        {
            if ( len == 0 )
            {
                return null;
            }

            return ldifFile.readLine();
        }

    }


    /**
     * add the CSN and UUID attributes to the entry if they are not present
     */
    private void addMandatoryOpAt( Entry entry ) throws LdapException
    {
        if ( entry.get( SchemaConstants.ENTRY_CSN_AT ) == null )
        {
            entry.add( SchemaConstants.ENTRY_CSN_AT, defaultCSNFactory.newInstance().toString() );
        }

        if ( entry.get( SchemaConstants.ENTRY_UUID_AT ) == null )
        {
            String uuid = UUID.randomUUID().toString();
            entry.add( SchemaConstants.ENTRY_UUID_AT, uuid );
        }
    }


    @Override
    protected void doDestroy() throws Exception
    {
        ldifFile.close();
    }

    
    /**
     * enable/disable the re-writing of partition data.
     * This method internally calls the @see {@link #rewritePartitionData()} to save any dirty data if present
     * 
     * @param enableRewriting flag to enable/disable re-writing
     * @throws LdapException
     */
    public void setEnableRewriting( boolean enableRewriting ) throws LdapException
    {
        this.enableRewriting = enableRewriting;

        // save data if found dirty 
        rewritePartitionData();
    }
}
