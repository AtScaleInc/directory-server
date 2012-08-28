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
package org.apache.directory.server.core.partition.impl.btree.jdbm;


import java.io.IOException;
import java.util.UUID;

import jdbm.helper.Serializer;


public class UUIDSerializer implements Serializer
{
    private static final long serialVersionUID = -1L;

    private StringSerializer stringSerializer = new StringSerializer();

    public final static UUIDSerializer INSTANCE = new UUIDSerializer();


    /* (non-Javadoc)
     * @see jdbm.helper.Serializer#deserialize(byte[])
     */
    public Object deserialize( byte[] bites ) throws IOException
    {
        return UUID.fromString( ( String ) stringSerializer.deserialize( bites ) );
    }


    /* (non-Javadoc)
     * @see jdbm.helper.Serializer#serialize(java.lang.Object)
     */
    public byte[] serialize( Object uuid ) throws IOException
    {
        return stringSerializer.serialize( ( ( UUID ) uuid ).toString() );
    }
}
