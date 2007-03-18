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
package org.apache.directory.server.ldap.support;


import org.apache.directory.server.ldap.support.bind.BindHandlerChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.handler.chain.IoHandlerCommand;
import org.apache.mina.handler.demux.MessageHandler;


/**
 * A single reply handler for {@link org.apache.directory.shared.ldap.message.BindRequest}s.
 * 
 * Implements server-side of RFC 2222, sections 4.2 and 4.3.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class BindHandler implements MessageHandler
{
    private IoHandlerCommand bindHandler;


    /**
     * Creates a new instance of BindHandler.
     */
    public BindHandler()
    {
        bindHandler = new BindHandlerChain();
    }


    public void messageReceived( IoSession session, Object message ) throws Exception
    {
        bindHandler.execute( null, session, message );
    }
}
