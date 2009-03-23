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
package org.apache.directory.server.integ;


import java.util.HashMap;
import java.util.Map;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.impl.DefaultDirectoryService;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.server.ldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.SimpleMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.cramMD5.CramMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.digestMD5.DigestMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.gssapi.GssapiMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.ntlm.NtlmMechanismHandler;
import org.apache.directory.server.ldap.handlers.extended.StartTlsHandler;
import org.apache.directory.server.ldap.handlers.extended.StoredProcedureExtendedOperationHandler;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.mina.util.AvailablePortFinder;


/**
 * A factory used to generate differently configured DirectoryService objects.
 * Since the DirectoryService itself is what is configured then a factory for
 * these objects acts as a configurator.  Tests can provide different factory
 * methods to be used.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public interface LdapServerFactory
{
    /**
     * The default factory returns stock instances of a directory
     * service with smart defaults
     */
    LdapServerFactory DEFAULT = new LdapServerFactory()
    {
        public LdapService newInstance() throws Exception
        {
            DirectoryService service = new DefaultDirectoryService();
            IntegrationUtils.doDelete( service.getWorkingDirectory() );
            service.getChangeLog().setEnabled( true );
            service.setShutdownHookEnabled( false );

            // change the working directory to something that is unique
            // on the system and somewhere either under target directory
            // or somewhere in a temp area of the machine.

            LdapService ldapService = new LdapService();
            ldapService.setDirectoryService( service );
            int port = AvailablePortFinder.getNextAvailable( 1024 );
            ldapService.setTcpTransport( new TcpTransport( port, 3 ) );
            ldapService.addExtendedOperationHandler( new StartTlsHandler() );
            ldapService.addExtendedOperationHandler( new StoredProcedureExtendedOperationHandler() );

            // Setup SASL Mechanisms
            
            Map<String, MechanismHandler> mechanismHandlerMap = new HashMap<String,MechanismHandler>();
            mechanismHandlerMap.put( SupportedSaslMechanisms.PLAIN, new SimpleMechanismHandler() );

            CramMd5MechanismHandler cramMd5MechanismHandler = new CramMd5MechanismHandler();
            mechanismHandlerMap.put( SupportedSaslMechanisms.CRAM_MD5, cramMd5MechanismHandler );

            DigestMd5MechanismHandler digestMd5MechanismHandler = new DigestMd5MechanismHandler();
            mechanismHandlerMap.put( SupportedSaslMechanisms.DIGEST_MD5, digestMd5MechanismHandler );

            GssapiMechanismHandler gssapiMechanismHandler = new GssapiMechanismHandler();
            mechanismHandlerMap.put( SupportedSaslMechanisms.GSSAPI, gssapiMechanismHandler );

            NtlmMechanismHandler ntlmMechanismHandler = new NtlmMechanismHandler();
            mechanismHandlerMap.put( SupportedSaslMechanisms.NTLM, ntlmMechanismHandler );
            mechanismHandlerMap.put( SupportedSaslMechanisms.GSS_SPNEGO, ntlmMechanismHandler );

            ldapService.setSaslMechanismHandlers( mechanismHandlerMap );

            return ldapService;
        }
    };

    
    LdapService newInstance() throws Exception;
}
