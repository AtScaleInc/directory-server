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
package org.apache.directory.server.core.avltree;


import java.util.Comparator;

import org.apache.directory.shared.ldap.model.cursor.AbstractCursor;
import org.apache.directory.shared.ldap.model.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.model.cursor.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Cursor for AvlTreeMap without duplicates.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AvlSingletonOrOrderedSetCursor<K, V> extends AbstractCursor<Tuple<K, SingletonOrOrderedSet<V>>>
{
    /** A dedicated log for cursors */
    private static final Logger LOG_CURSOR = LoggerFactory.getLogger( "CURSOR" );

    /** The underlying AVL tree map */
    private AvlTreeMap<K, V> tree;

    /** The current node */
    private LinkedAvlMapNode<K, V> node;

    /** The current position of this cursor, relative to the node */
    private Position position = Position.BEFORE_FIRST;

    private Tuple<K, SingletonOrOrderedSet<V>> returnedTuple = new Tuple<K, SingletonOrOrderedSet<V>>();


    public AvlSingletonOrOrderedSetCursor( AvlTreeMap<K, V> tree )
    {
        LOG_CURSOR.debug( "Creating AvlSingletonOrOrderedSetCursor {}", this );
        this.tree = tree;
    }


    public Comparator<K> getKeyComparator()
    {
        return tree.getKeyComparator();
    }


    public Comparator<V> getValuComparator()
    {
        return tree.getValueComparator();
    }


    public void after( Tuple<K, SingletonOrOrderedSet<V>> element ) throws Exception
    {
        afterKey( element.getKey() );
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast" );
        node = null;
        position = Position.AFTER_LAST;
    }


    public boolean available()
    {
        return position == Position.ON_NODE;
    }


    public void before( Tuple<K, SingletonOrOrderedSet<V>> element ) throws Exception
    {
        beforeKey( element.getKey() );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst" );
        node = null;
        position = Position.BEFORE_FIRST;
    }


    public boolean first() throws Exception
    {
        checkNotClosed( "first" );

        node = tree.getFirst();

        if ( node == null )
        {
            position = Position.BEFORE_FIRST;
            return false;
        }
        else
        {
            position = Position.ON_NODE;
            return true;
        }
    }


    public Tuple<K, SingletonOrOrderedSet<V>> get() throws Exception
    {
        checkNotClosed( "get" );

        if ( position == Position.ON_NODE )
        {
            returnedTuple.setKey( node.key );
            returnedTuple.setValue( node.value );
            return returnedTuple;
        }

        throw new InvalidCursorPositionException();
    }


    public boolean last() throws Exception
    {
        checkNotClosed( "last" );

        node = tree.getLast();

        if ( node == null )
        {
            position = Position.AFTER_LAST;
            return false;
        }
        else
        {
            position = Position.ON_NODE;
            return true;
        }
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next" );

        switch ( position )
        {
            case BEFORE_FIRST:
                return first();

            case BEFORE_NODE:
                position = Position.ON_NODE;
                return true;

            case ON_NODE:
            case AFTER_NODE:
                node = node.next;
                
                if ( node == null )
                {
                    afterLast();
                    
                    return false;
                }
                else
                {
                    position = Position.ON_NODE;
                    return true;
                }

            case AFTER_LAST:
                return false;

            default:
                throw new IllegalStateException( "Unexpected position " + position );
        }
    }


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous" );

        switch ( position )
        {
            case BEFORE_FIRST:
                return false;

            case BEFORE_NODE:
            case ON_NODE:
                node = node.previous;
                if ( node == null )
                {
                    beforeFirst();
                    return false;
                }
                else
                {
                    position = Position.ON_NODE;
                    return true;
                }

            case AFTER_NODE:
                position = Position.ON_NODE;
                return true;

            case AFTER_LAST:
                return last();

            default:
                throw new IllegalStateException( "Unexpected position " + position );
        }
    }


    public void afterKey( K key ) throws Exception
    {
        checkNotClosed( "afterKey" );

        if ( key == null )
        {
            afterLast();
            return;
        }

        node = tree.findGreater( key );

        if ( node == null )
        {
            position = Position.AFTER_LAST;
        }
        else
        {
            // the cursor should be positioned after the given element
            // we just fetched the next greater element so the cursor
            // is positioned before the fetched element
            position = Position.BEFORE_NODE;
        }
    }


    public void afterValue( K key, SingletonOrOrderedSet<V> value ) throws Exception
    {
        throw new UnsupportedOperationException( "This Cursor does not support duplicate keys." );
    }


    public void beforeKey( K key ) throws Exception
    {
        checkNotClosed( "beforeKey" );

        if ( key == null )
        {
            beforeFirst();
            return;
        }

        node = tree.findLess( key );

        if ( node == null )
        {
            position = Position.BEFORE_FIRST;
        }
        else
        {
            // the cursor should be positioned before the given element
            // we just fetched the next less element so the cursor
            // is positioned after the fetched element
            position = Position.AFTER_NODE;
        }
    }


    public void beforeValue( K key, SingletonOrOrderedSet<V> value ) throws Exception
    {
        throw new UnsupportedOperationException( "This Cursor does not support duplicate keys." );
    }


    /**
     * {@inheritDoc}
     */
    public void close() throws Exception
    {
        LOG_CURSOR.debug( "Closing AvlSingletonOrOrderedSetCursor {}", this );
        super.close();
    }


    /**
     * {@inheritDoc}
     */
    public void close( Exception reason ) throws Exception
    {
        LOG_CURSOR.debug( "Closing AvlSingletonOrOrderedSetCursor {}", this );
        super.close( reason );
    }
}
