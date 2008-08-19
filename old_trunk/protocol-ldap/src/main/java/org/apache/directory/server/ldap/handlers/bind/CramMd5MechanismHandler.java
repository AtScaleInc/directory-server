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
package org.apache.directory.server.ldap.handlers.bind;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.message.BindRequest;
import org.apache.mina.common.IoSession;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.Map;


/**
 * The CRAM-MD Sasl mechanism handler.
 *
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class CramMd5MechanismHandler implements MechanismHandler
{
    private DirectoryService directoryService;


    public void setDirectoryService( DirectoryService directoryService )
    {
        this.directoryService = directoryService;
    }

    
    public SaslServer handleMechanism( IoSession session, BindRequest bindRequest ) throws Exception
    {
        SaslServer ss;

        if ( session.containsAttribute( SASL_CONTEXT ) )
        {
            ss = ( SaslServer ) session.getAttribute( SASL_CONTEXT );
        }
        else
        {
            String saslHost = ( String ) session.getAttribute( "saslHost" );

            /*
             * Sasl will throw an exception is Sasl.QOP properties are set.
             * CRAM-MD5 doesn't support QoP.
             */
            Map<String, String> saslProps = new HashMap<String, String>();

            CallbackHandler callbackHandler = new CramMd5CallbackHandler( directoryService, session, bindRequest );

            ss = Sasl.createSaslServer( SupportedSaslMechanisms.CRAM_MD5, "ldap", saslHost, saslProps, callbackHandler );
            session.setAttribute( SASL_CONTEXT, ss );
        }

        return ss;
    }
}
