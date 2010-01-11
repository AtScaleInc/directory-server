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
package org.apache.directory.server.xdbm;

import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;



/**
 * An empty Cursor implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class EmptyIndexCursor<K,E> extends AbstractIndexCursor<K,E>
{
    public boolean available()
    {
        return false;
    }

    public void before( IndexEntry<K,E> element ) throws Exception
    {
        checkNotClosed( "before()" );
    }


    public void after( IndexEntry<K,E> element ) throws Exception
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


    public IndexEntry<K,E> get() throws Exception
    {
        checkNotClosed( "get()" );
        throw new InvalidCursorPositionException( "This cursor is empty and cannot return elements!" );
    }


    public boolean isElementReused()
    {
        return false;
    }

    public void afterValue( Long id, K indexValue ) throws Exception
    {
        checkNotClosed( "after()" );
    }

    public void beforeValue( Long id, K indexValue ) throws Exception
    {
        checkNotClosed( "after()" );
    }
}
