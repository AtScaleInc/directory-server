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


import java.util.UUID;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.partition.index.AbstractIndexCursor;
import org.apache.directory.server.core.api.partition.index.ForwardIndexEntry;
import org.apache.directory.server.core.api.partition.index.Index;
import org.apache.directory.server.core.api.partition.index.IndexCursor;
import org.apache.directory.server.core.api.partition.index.IndexEntry;
import org.apache.directory.server.core.api.txn.TxnLogManager;
import org.apache.directory.server.core.shared.partition.OperationExecutionManagerFactory;
import org.apache.directory.server.core.shared.txn.TxnManagerFactory;
import org.apache.directory.shared.ldap.model.constants.SchemaConstants;
import org.apache.directory.shared.ldap.model.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.model.schema.AttributeType;


/**
 * A Cursor over entry candidates matching a GreaterEq assertion filter.  This
 * Cursor operates in two modes.  The first is when an index exists for the
 * attribute the assertion is built on.  The second is when the user index for
 * the assertion attribute does not exist.  Different Cursors are used in each
 * of these cases where the other remains null.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class GreaterEqCursor<V> extends AbstractIndexCursor<V>
{
    private static final String UNSUPPORTED_MSG = "GreaterEqCursors only support positioning by element when a user index exists on the asserted attribute.";

    /** An greater eq evaluator for candidates */
    private final GreaterEqEvaluator<V> greaterEqEvaluator;

    /** Cursor over attribute entry matching filter: set when index present */
    private final IndexCursor<V> userIdxCursor;

    /** NDN Cursor on all entries in  (set when no index on user attribute) */
    private final IndexCursor<String> ndnIdxCursor;

    /** Txn and Operation Execution Factories */
    private TxnManagerFactory txnManagerFactory;
    private OperationExecutionManagerFactory executionManagerFactory;

    /**
     * Used to store indexEntry from ndnCandidate so it can be saved after
     * call to evaluate() which changes the value so it's not referring to
     * the NDN but to the value of the attribute instead.
     */
    IndexEntry<String> ndnCandidate;


    /**
     * Creates a new instance of an GreaterEqCursor
     * @param db The store
     * @param equalityEvaluator The GreaterEqEvaluator
     * @throws Exception If the creation failed
     */
    @SuppressWarnings("unchecked")
    public GreaterEqCursor( Partition db, GreaterEqEvaluator<V> greaterEqEvaluator,
        TxnManagerFactory txnManagerFactory,
        OperationExecutionManagerFactory executionManagerFactory ) throws Exception
    {
        this.txnManagerFactory = txnManagerFactory;
        this.executionManagerFactory = executionManagerFactory;

        TxnLogManager txnLogManager = txnManagerFactory.txnLogManagerInstance();
        this.greaterEqEvaluator = greaterEqEvaluator;

        AttributeType attributeType = greaterEqEvaluator.getExpression().getAttributeType();

        if ( db.hasIndexOn( attributeType ) )
        {
            Index<?> index = db.getIndex( attributeType );
            index = txnLogManager.wrap( db.getSuffixDn(), index );
            userIdxCursor = ( ( Index<V> ) index ).forwardCursor();
            ndnIdxCursor = null;
        }
        else
        {
            Index<?> entryUuidIdx = db.getSystemIndex( SchemaConstants.ENTRY_UUID_AT_OID );
            entryUuidIdx = txnLogManager.wrap( db.getSuffixDn(), entryUuidIdx );
            ndnIdxCursor = ( ( Index<String> ) entryUuidIdx ).forwardCursor();
            userIdxCursor = null;
        }
    }


    /**
     * {@inheritDoc}
     */
    protected String getUnsupportedMessage()
    {
        return UNSUPPORTED_MSG;
    }


    /**
     * {@inheritDoc}
     */
    public void beforeValue( UUID id, V value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );

        if ( userIdxCursor != null )
        {
            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is smaller or
             * equal to this lower bound then we simply position the
             * userIdxCursor before the first element.  Otherwise we let the
             * underlying userIdx Cursor position the element.
             */
            if ( greaterEqEvaluator.getComparator()
                .compare( value, greaterEqEvaluator.getExpression().getValue().getValue() ) <= 0 )
            {
                beforeFirst();
                return;
            }

            userIdxCursor.beforeValue( id, value );
            setAvailable( false );
        }
        else
        {
            super.beforeValue( id, value );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void afterValue( UUID id, V value ) throws Exception
    {
        checkNotClosed( "afterValue()" );

        if ( userIdxCursor != null )
        {
            int comparedValue = greaterEqEvaluator.getComparator().compare( value,
                greaterEqEvaluator.getExpression().getValue().getValue() );

            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is equal to this
             * lower bound then we simply position the userIdxCursor after
             * this first node.  If it is less than this value then we
             * position the Cursor before the first entry.
             */
            if ( comparedValue == 0 )
            {
                userIdxCursor.afterValue( id, value );
                setAvailable( false );

                return;
            }
            else if ( comparedValue < 0 )
            {
                beforeFirst();

                return;
            }

            // Element is in the valid range as specified by assertion
            userIdxCursor.afterValue( id, value );
            setAvailable( false );
        }
        else
        {
            super.afterValue( id, value );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void before( IndexEntry<V> element ) throws Exception
    {
        checkNotClosed( "before()" );

        if ( userIdxCursor != null )
        {
            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is smaller or
             * equal to this lower bound then we simply position the
             * userIdxCursor before the first element.  Otherwise we let the
             * underlying userIdx Cursor position the element.
             */
            if ( greaterEqEvaluator.getComparator().compare( element.getValue(),
                greaterEqEvaluator.getExpression().getValue().getValue() ) <= 0 )
            {
                beforeFirst();
                return;
            }

            userIdxCursor.before( element );
            setAvailable( false );
        }
        else
        {
            super.before( element );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void after( IndexEntry<V> element ) throws Exception
    {
        checkNotClosed( "after()" );

        if ( userIdxCursor != null )
        {
            int comparedValue = greaterEqEvaluator.getComparator().compare( element.getValue(),
                greaterEqEvaluator.getExpression().getValue().getValue() );

            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is equal to this
             * lower bound then we simply position the userIdxCursor after
             * this first node.  If it is less than this value then we
             * position the Cursor before the first entry.
             */
            if ( comparedValue == 0 )
            {
                userIdxCursor.after( element );
                setAvailable( false );

                return;
            }

            if ( comparedValue < 0 )
            {
                beforeFirst();

                return;
            }

            // Element is in the valid range as specified by assertion
            userIdxCursor.after( element );
            setAvailable( false );
        }
        else
        {
            super.after( element );
        }
    }


    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );

        if ( userIdxCursor != null )
        {
            IndexEntry<V> advanceTo = new ForwardIndexEntry<V>();
            advanceTo.setValue( ( V ) greaterEqEvaluator.getExpression().getValue().getValue() );
            userIdxCursor.before( advanceTo );
        }
        else
        {
            ndnIdxCursor.beforeFirst();
            ndnCandidate = null;
        }

        setAvailable( false );
    }


    /**
     * {@inheritDoc}
     */
    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );

        if ( userIdxCursor != null )
        {
            userIdxCursor.afterLast();
        }
        else
        {
            ndnIdxCursor.afterLast();
            ndnCandidate = null;
        }

        setAvailable( false );
    }


    /**
     * {@inheritDoc}
     */
    public boolean first() throws Exception
    {
        beforeFirst();

        return next();
    }


    /**
     * {@inheritDoc}
     */
    public boolean last() throws Exception
    {
        afterLast();

        return previous();
    }


    /**
     * {@inheritDoc}
     */
    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );

        if ( userIdxCursor != null )
        {
            /*
             * We have to check and make sure the previous value complies by
             * being greater than or eq to the expression node's value
             */
            while ( userIdxCursor.previous() )
            {
                checkNotClosed( "previous()" );
                IndexEntry<?> candidate = userIdxCursor.get();

                if ( greaterEqEvaluator.getComparator().compare( candidate.getValue(),
                    greaterEqEvaluator.getExpression().getValue().getValue() ) >= 0 )
                {
                    return setAvailable( true );
                }
            }

            return setAvailable( false );
        }

        while ( ndnIdxCursor.previous() )
        {
            checkNotClosed( "previous()" );
            ndnCandidate = ndnIdxCursor.get();

            if ( greaterEqEvaluator.evaluate( ndnCandidate ) )
            {
                return setAvailable( true );
            }
        }

        return setAvailable( false );
    }


    /**
     * {@inheritDoc}
     */
    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );

        if ( userIdxCursor != null )
        {
            /*
             * No need to do the same check that is done in previous() since
             * values are increasing with calls to next().
             */
            return setAvailable( userIdxCursor.next() );
        }

        while ( ndnIdxCursor.next() )
        {
            checkNotClosed( "next()" );
            ndnCandidate = ndnIdxCursor.get();

            if ( greaterEqEvaluator.evaluate( ndnCandidate ) )
            {
                return setAvailable( true );
            }
        }

        return setAvailable( false );
    }


    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public IndexEntry<V> get() throws Exception
    {
        checkNotClosed( "get()" );

        if ( userIdxCursor != null )
        {
            if ( available() )
            {
                return userIdxCursor.get();
            }

            throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
        }

        if ( available() )
        {
            return ( IndexEntry<V> ) ndnCandidate;
        }

        throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
    }


    /**
     * {@inheritDoc}
     */
    public void close() throws Exception
    {
        super.close();

        if ( userIdxCursor != null )
        {
            userIdxCursor.close();
        }
        else
        {
            ndnIdxCursor.close();
        }
    }
}