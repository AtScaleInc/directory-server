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
package org.apache.directory.server.core.api.filtering;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.directory.server.core.api.entry.ClonedServerEntry;
import org.apache.directory.server.core.api.entry.ClonedServerEntrySearch;
import org.apache.directory.server.core.api.interceptor.context.SearchingOperationContext;
import org.apache.directory.shared.ldap.model.cursor.AbstractCursor;
import org.apache.directory.shared.ldap.model.cursor.ClosureMonitor;
import org.apache.directory.shared.ldap.model.cursor.Cursor;
import org.apache.directory.shared.ldap.model.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.model.entry.Attribute;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.exception.OperationAbandonedException;
import org.apache.directory.shared.ldap.model.schema.AttributeType;
import org.apache.directory.shared.ldap.model.schema.AttributeTypeOptions;
import org.apache.directory.shared.ldap.model.schema.UsageEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Cursor which uses a list of filters to selectively return entries and/or
 * modify the contents of entries.  Uses lazy pre-fetching on positioning
 * operations which means adding filters after creation will not miss candidate
 * entries.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class BaseEntryFilteringCursor extends AbstractCursor<Entry> implements EntryFilteringCursor
{
    /** the logger used by this class */
    private static final Logger log = LoggerFactory.getLogger( BaseEntryFilteringCursor.class );

    /** A dedicated log for cursors */
    private static final Logger LOG_CURSOR = LoggerFactory.getLogger( "CURSOR" );

    /** the underlying wrapped search results Cursor */
    private final Cursor<Entry> wrapped;

    /** the parameters associated with the search operation */
    private final SearchingOperationContext operationContext;

    /** the list of filters to be applied */
    private final List<EntryFilter> filters;

    /** the first accepted search result that is pre fetched */
    private Entry prefetched;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a new entry filtering Cursor over an existing Cursor using a
     * single filter initially: more can be added later after creation.
     * 
     * @param wrapped the underlying wrapped Cursor whose entries are filtered
     * @param searchControls the controls of search that created this Cursor
     * @param invocation the search operation invocation creating this Cursor
     * @param filter a single filter to be used
     */
    public BaseEntryFilteringCursor( Cursor<Entry> wrapped,
        SearchingOperationContext operationContext, EntryFilter filter )
    {
        this( wrapped, operationContext, Collections.singletonList( filter ) );
    }


    /**
     * Creates a new entry filtering Cursor over an existing Cursor using a
     * no filter initially: more can be added later after creation.
     * 
     * @param wrapped the underlying wrapped Cursor whose entries are filtered
     * @param searchControls the controls of search that created this Cursor
     * @param invocation the search operation invocation creating this Cursor
     * @param filter a single filter to be used
     */
    public BaseEntryFilteringCursor( Cursor<Entry> wrapped, SearchingOperationContext operationContext )
    {
        LOG_CURSOR.debug( "Creating BaseEntryFilteringCursor {}", this );
        this.wrapped = wrapped;
        this.operationContext = operationContext;
        this.filters = new ArrayList<EntryFilter>();
    }


    /**
     * Creates a new entry filtering Cursor over an existing Cursor using a
     * list of filters initially: more can be added later after creation.
     * 
     * @param wrapped the underlying wrapped Cursor whose entries are filtered
     * @param operationContext the operation context that created this Cursor
     * @param invocation the search operation invocation creating this Cursor
     * @param filters a list of filters to be used
     */
    public BaseEntryFilteringCursor( Cursor<Entry> wrapped,
        SearchingOperationContext operationContext, List<EntryFilter> filters )
    {
        LOG_CURSOR.debug( "Creating BaseEntryFilteringCursor {}", this );
        this.wrapped = wrapped;
        this.operationContext = operationContext;
        this.filters = new ArrayList<EntryFilter>();
        this.filters.addAll( filters );
    }


    // ------------------------------------------------------------------------
    // Class Specific Methods
    // ------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#isAbandoned()
     */
    public boolean isAbandoned()
    {
        return getOperationContext().isAbandoned();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#setAbandoned(boolean)
     */
    public void setAbandoned( boolean abandoned )
    {
        getOperationContext().setAbandoned( abandoned );

        if ( abandoned )
        {
            log.info( "Cursor has been abandoned." );
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#addEntryFilter(org.apache.directory.server.core.filtering.EntryFilter)
     */
    public boolean addEntryFilter( EntryFilter filter )
    {
        return filters.add( filter );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#removeEntryFilter(org.apache.directory.server.core.filtering.EntryFilter)
     */
    public boolean removeEntryFilter( EntryFilter filter )
    {
        return filters.remove( filter );
    }


    /**
     * {@inheritDoc}
     */
    public List<EntryFilter> getEntryFilters()
    {
        return Collections.unmodifiableList( filters );
    }


    /**
     * {@inheritDoc}
     */
    public SearchingOperationContext getOperationContext()
    {
        return operationContext;
    }


    // ------------------------------------------------------------------------
    // Cursor Interface Methods
    // ------------------------------------------------------------------------
    /*
     * @see Cursor#after(Object)
     */
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#after(org.apache.directory.server.core.entry.ClonedServerEntry)
     */
    public void after( Entry element ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    /**
     * {@inheritDoc}
     */
    public void afterLast() throws Exception
    {
        wrapped.afterLast();
        prefetched = null;
    }


    /**
     * {@inheritDoc}
     */
    public boolean available()
    {
        return prefetched != null;
    }


    /*
     * @see Cursor#before(java.lang.Object)
     */
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.filtering.EntryFilteringCursor#before(org.apache.directory.server.core.entry.ClonedServerEntry)
     */
    public void before( Entry element ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    /**
     * {@inheritDoc}
     */
    public void beforeFirst() throws Exception
    {
        wrapped.beforeFirst();
        prefetched = null;
    }


    /**
     * {@inheritDoc}
     */
    public void close() throws Exception
    {
        LOG_CURSOR.debug( "Closing BaseEntryFilteringCursor {}", this );
        wrapped.close();
        prefetched = null;
    }


    /**
     * {@inheritDoc}
     */
    public void close( Exception reason ) throws Exception
    {
        LOG_CURSOR.debug( "Closing BaseEntryFilteringCursor {}", this );
        wrapped.close( reason );
        prefetched = null;
    }


    public final void setClosureMonitor( ClosureMonitor monitor )
    {
        wrapped.setClosureMonitor( monitor );
    }


    /**
     * {@inheritDoc}
     */
    public boolean first() throws Exception
    {
        if ( getOperationContext().isAbandoned() )
        {
            log.info( "Cursor has been abandoned." );
            close();
            throw new OperationAbandonedException();
        }

        beforeFirst();

        return next();
    }


    /**
     * {@inheritDoc}
     */
    public Entry get() throws Exception
    {
        if ( available() )
        {
            return prefetched;
        }

        throw new InvalidCursorPositionException();
    }


    /**
     * {@inheritDoc}
     */
    public boolean isClosed() throws Exception
    {
        return wrapped.isClosed();
    }


    /**
     * {@inheritDoc}
     */
    public boolean last() throws Exception
    {
        if ( getOperationContext().isAbandoned() )
        {
            log.info( "Cursor has been abandoned." );
            close();
            throw new OperationAbandonedException();
        }

        afterLast();

        return previous();
    }


    private void filterContents( Entry entry ) throws Exception
    {
        boolean typesOnly = getOperationContext().isTypesOnly();

        boolean returnAll = ( getOperationContext().getReturningAttributes() == null ||
            ( getOperationContext().isAllOperationalAttributes() && getOperationContext().isAllUserAttributes() ) )
            && ( !typesOnly );

        if ( returnAll )
        {
            return;
        }

        Entry originalEntry = ( ( ClonedServerEntry ) entry ).getOriginalEntry();

        if ( getOperationContext().isNoAttributes() )
        {
            for ( Attribute attribute : originalEntry.getAttributes() )
            {
                AttributeType attributeType = attribute.getAttributeType();
                entry.remove( entry.get( attributeType ) );
            }

            return;
        }

        if ( getOperationContext().isAllUserAttributes() )
        {
            for ( Attribute attribute : originalEntry.getAttributes() )
            {
                AttributeType attributeType = attribute.getAttributeType();
                boolean isNotRequested = true;

                for ( AttributeTypeOptions attrOptions : getOperationContext().getReturningAttributes() )
                {
                    if ( attrOptions.getAttributeType().equals( attributeType ) ||
                        attrOptions.getAttributeType().isAncestorOf( attributeType ) )
                    {
                        isNotRequested = false;
                        break;
                    }
                }

                boolean isNotUserAttribute = attributeType.getUsage() != UsageEnum.USER_APPLICATIONS;

                if ( isNotRequested && isNotUserAttribute )
                {
                    entry.removeAttributes( attributeType );
                }
                else if ( typesOnly )
                {
                    entry.get( attributeType ).clear();
                }
            }

            return;
        }

        if ( getOperationContext().isAllOperationalAttributes() )
        {
            for ( Attribute attribute : originalEntry.getAttributes() )
            {
                AttributeType attributeType = attribute.getAttributeType();
                boolean isNotRequested = true;

                for ( AttributeTypeOptions attrOptions : getOperationContext().getReturningAttributes() )
                {
                    if ( attrOptions.getAttributeType().equals( attributeType ) ||
                        attrOptions.getAttributeType().isAncestorOf( attributeType ) )
                    {
                        isNotRequested = false;
                        break;
                    }
                }

                boolean isUserAttribute = attributeType.getUsage() == UsageEnum.USER_APPLICATIONS;

                if ( isNotRequested && isUserAttribute )
                {
                    entry.removeAttributes( attributeType );
                }
                else if ( typesOnly )
                {
                    entry.get( attributeType ).clear();
                }
            }

            return;
        }

        if ( getOperationContext().getReturningAttributes() != null )
        {
            for ( Attribute attribute : originalEntry.getAttributes() )
            {
                AttributeType attributeType = attribute.getAttributeType();
                boolean isNotRequested = true;

                for ( AttributeTypeOptions attrOptions : getOperationContext().getReturningAttributes() )
                {
                    if ( attrOptions.getAttributeType().equals( attributeType ) ||
                        attrOptions.getAttributeType().isAncestorOf( attributeType ) )
                    {
                        isNotRequested = false;
                        break;
                    }
                }

                if ( isNotRequested )
                {
                    entry.removeAttributes( attributeType );
                }
                else if ( typesOnly )
                {
                    entry.get( attributeType ).clear();
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    public boolean next() throws Exception
    {
        if ( getOperationContext().isAbandoned() )
        {
            log.info( "Cursor has been abandoned." );
            close();
            throw new OperationAbandonedException();
        }

        Entry tempResult = null;

        outer: while ( wrapped.next() )
        {
            boolean accepted = true;

            Entry tempEntry = wrapped.get();

            if ( tempEntry instanceof ClonedServerEntry )
            {
                tempResult = tempEntry;
            }
            else
            {
                tempResult = new ClonedServerEntrySearch( tempEntry );
            }

            /*
             * O P T I M I Z A T I O N
             * -----------------------
             * 
             * Don't want to waste cycles on enabling a loop for processing
             * filters if we have zero or one filter.
             */

            if ( filters.isEmpty() )
            {
                prefetched = tempResult;
                filterContents( prefetched );
                return true;
            }

            if ( ( filters.size() == 1 ) && filters.get( 0 ).accept( getOperationContext(), tempResult ) )
            {
                prefetched = tempResult;
                filterContents( prefetched );
                return true;
            }

            /* E N D   O P T I M I Z A T I O N */
            for ( EntryFilter filter : filters )
            {
                // if a filter rejects then short and continue with outer loop
                if ( !filter.accept( getOperationContext(), tempResult ) )
                {
                    continue outer;
                }
            }

            /*
             * Here the entry has been accepted by all filters.
             */
            prefetched = tempResult;
            filterContents( prefetched );

            return true;
        }

        prefetched = null;
        return false;
    }


    /**
     * {@inheritDoc}
     */
    public boolean previous() throws Exception
    {
        if ( getOperationContext().isAbandoned() )
        {
            log.info( "Cursor has been abandoned." );
            close();
            throw new OperationAbandonedException();
        }

        Entry tempResult = null;

        outer: while ( wrapped.previous() )
        {
            boolean accepted = true;
            tempResult = new ClonedServerEntrySearch( wrapped.get() );

            /*
             * O P T I M I Z A T I O N
             * -----------------------
             * 
             * Don't want to waste cycles on enabling a loop for processing
             * filters if we have zero or one filter.
             */

            if ( filters.isEmpty() )
            {
                prefetched = tempResult;
                filterContents( prefetched );
                return true;
            }

            if ( ( filters.size() == 1 ) && filters.get( 0 ).accept( getOperationContext(), tempResult ) )
            {
                prefetched = tempResult;
                filterContents( prefetched );
                return true;
            }

            /* E N D   O P T I M I Z A T I O N */

            for ( EntryFilter filter : filters )
            {
                // if a filter rejects then short and continue with outer loop
                if ( !filter.accept( getOperationContext(), tempResult ) )
                {
                    continue outer;
                }
            }

            /*
             * Here the entry has been accepted by all filters.
             */
            prefetched = tempResult;
            filterContents( prefetched );

            return true;
        }

        prefetched = null;

        return false;
    }
}
