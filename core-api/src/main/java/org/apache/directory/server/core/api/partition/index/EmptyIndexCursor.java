/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.api.partition.index;


import java.util.UUID;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.model.cursor.InvalidCursorPositionException;


/**
 * An empty Cursor implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class EmptyIndexCursor<K> extends AbstractIndexCursor<K>
{
    /**
     * {@inheritDoc}
     */
    public void before( IndexEntry<K> element ) throws Exception
    {
        checkNotClosed( "before()" );
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
    public void after( IndexEntry<K> element ) throws Exception
    {
        checkNotClosed( "after()" );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
    }


    public boolean first() throws Exception
    {
        checkNotClosed( "first()" );
        return false;
    }


    public boolean last() throws Exception
    {
        checkNotClosed( "last()" );
        return false;
    }


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        return false;
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        return false;
    }


    public IndexEntry<K> get() throws Exception
    {
        checkNotClosed( "get()" );
        throw new InvalidCursorPositionException( I18n.err( I18n.ERR_703 ) );
    }


    /**
     * {@inheritDoc}
     */
    public void afterValue( UUID id, K indexValue ) throws Exception
    {
        checkNotClosed( "after()" );
    }


    /**
     * {@inheritDoc}
     */
    public void beforeValue( UUID id, K indexValue ) throws Exception
    {
        checkNotClosed( "after()" );
    }
}
