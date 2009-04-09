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
package org.apache.directory.server.xdbm.search.impl;


import org.apache.directory.server.core.cursor.InvalidCursorPositionException;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.AbstractIndexCursor;
import org.apache.directory.server.xdbm.IndexCursor;


/**
 * A Cursor over entries satisfying scope constraints with alias dereferencing
 * considerations.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SubtreeScopeCursor extends AbstractIndexCursor<Long, ServerEntry>
{
    private static final String UNSUPPORTED_MSG =
        "Scope Cursors are not ordered and do not support positioning by element.";

    /** The Entry database/store */
    private final Store<ServerEntry> db;

    /** A ScopeNode Evaluator */
    private final SubtreeScopeEvaluator<ServerEntry> evaluator;

    /** A Cursor over the entries in the scope of the search base */
    private final IndexCursor<Long,ServerEntry> scopeCursor;

    /** A Cursor over entries brought into scope by alias dereferencing */
    private final IndexCursor<Long,ServerEntry> dereferencedCursor;

    /** Currently active Cursor: we switch between two cursors */
    private IndexCursor<Long,ServerEntry> cursor;

    /** Whether or not this Cursor is positioned so an entry is available */
    private boolean available = false;

    private Long contextEntryId;

    
    /**
     * Creates a Cursor over entries satisfying subtree level scope criteria.
     *
     * @param db the entry store
     * @param evaluator an IndexEntry (candidate) evaluator
     * @throws Exception on db access failures
     */
    public SubtreeScopeCursor( Store<ServerEntry> db, SubtreeScopeEvaluator<ServerEntry> evaluator ) throws Exception
    {
        this.db = db;
        this.evaluator = evaluator;
        
        if ( evaluator.getBaseId() == getContextEntryId() )
        {
            scopeCursor = new AllEntriesCursor( db );
        }
        else
        {
            scopeCursor = db.getSubLevelIndex().forwardCursor( evaluator.getBaseId() );
        }

        if ( evaluator.isDereferencing() )
        {
            dereferencedCursor = db.getSubAliasIndex().forwardCursor( evaluator.getBaseId() );
        }
        else
        {
            dereferencedCursor = null;
        }
    }


    private Long getContextEntryId()
    {
        if ( contextEntryId == null )
        {
            try
            {
                this.contextEntryId = db.getEntryId( db.getNormSuffixDn().getNormName() );
            }
            catch ( Exception e )
            {
                // might not have been created
                // might not have been created
            }
        }
        
        if ( contextEntryId == null )
        {
            return 1L;
        }
        
        return contextEntryId;
    }

    
    public boolean available()
    {
        return available;
    }


    public void beforeValue( Long id, Long value ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void before( IndexEntry<Long, ServerEntry> element ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void afterValue( Long id, Long value ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void after( IndexEntry<Long, ServerEntry> element ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        cursor = scopeCursor;
        cursor.beforeFirst();
        available = false;
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        if ( evaluator.isDereferencing() )
        {
            cursor = dereferencedCursor;
        }
        else
        {
            cursor = scopeCursor;
        }

        cursor.afterLast();
        available = false;
    }


    public boolean first() throws Exception
    {
        beforeFirst();
        return next();
    }


    public boolean last() throws Exception
    {
        afterLast();
        return previous();
    }


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        // if the cursor has not been set - position it after last element
        if ( cursor == null )
        {
            afterLast();
        }

        // if we're using the scopeCursor (1st Cursor) then return result as is
        if ( cursor == scopeCursor )
        {
            /*
             * If dereferencing is enabled then we must ignore alias entries, not
             * returning them as part of the results.
             */
            if ( evaluator.isDereferencing() )
            {
                // advance until nothing is available or until we find a non-alias
                do
                {
                    checkNotClosed( "previous()" );
                    available = cursor.previous();
                    if ( available && db.getAliasIndex().reverseLookup( cursor.get().getId() ) == null )
                    {
                        break;
                    }
                }
                while ( available );
            }
            else
            {
                available = cursor.previous();
            }

            return available;
        }

        /*
         * Below here we are using the dereferencedCursor so if nothing is
         * available after an advance backwards we need to switch to the
         * scopeCursor and try a previous call after positioning past it's
         * last element.
         */
        available = cursor.previous();
        if ( ! available )
        {
            cursor = scopeCursor;
            cursor.afterLast();

            // advance until nothing is available or until we find a non-alias
            do
            {
                checkNotClosed( "previous()" );
                available = cursor.previous();

                if ( available && db.getAliasIndex().reverseLookup( cursor.get().getId() ) == null )
                {
                    break;
                }
            }
            while ( available );

            return available;
        }

        return true;
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        // if the cursor hasn't been set position it before the first element
        if ( cursor == null )
        {
            beforeFirst();
        }

        /*
         * If dereferencing is enabled then we must ignore alias entries, not
         * returning them as part of the results.
         */
        if ( evaluator.isDereferencing() )
        {
            // advance until nothing is available or until we find a non-alias
            do
            {
                checkNotClosed( "next()" );
                available = cursor.next();

                if ( available && db.getAliasIndex().reverseLookup( cursor.get().getId() ) == null )
                {
                    break;
                }
            }
            while ( available );
        }
        else
        {
            available = cursor.next();
        }

        // if we're using dereferencedCursor (2nd) then we return the result
        if ( cursor == dereferencedCursor )
        {
            return available;
        }

        /*
         * Below here we are using the scopeCursor so if nothing is
         * available after an advance forward we need to switch to the
         * dereferencedCursor and try a previous call after positioning past
         * it's last element.
         */
        if ( ! available )
        {
            if ( dereferencedCursor != null )
            {
                cursor = dereferencedCursor;
                cursor.beforeFirst();
                return available = cursor.next();
            }

            return false;
        }

        return true;
    }


    public IndexEntry<Long, ServerEntry> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( available )
        {
            return cursor.get();
        }

        throw new InvalidCursorPositionException( "Cursor has not been positioned yet." );
    }


    public boolean isElementReused()
    {
        return scopeCursor.isElementReused() ||
            ( dereferencedCursor != null && dereferencedCursor.isElementReused() );
    }
}
