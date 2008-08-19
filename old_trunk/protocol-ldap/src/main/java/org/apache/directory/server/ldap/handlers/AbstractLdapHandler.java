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
package org.apache.directory.server.ldap.handlers;


import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.SessionRegistry;
import org.apache.directory.shared.ldap.message.Message;
import org.apache.directory.shared.ldap.message.MutableControl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.handler.demux.MessageHandler;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;


/**
 * An abstract class to handle common methods used by all the handlers
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 541827 $
 */
public abstract class AbstractLdapHandler implements MessageHandler
{
    private LdapServer ldapServer;


    public final LdapServer getProtocolProvider()
    {
        return ldapServer;
    }


    public final void setProtocolProvider( LdapServer provider )
    {
        this.ldapServer = provider;
    }
    
    
    /**
     * Checks to see if confidentiality requirements are met.  If the 
     * LdapServer requires confidentiality and the SSLFilter is engaged
     * this will return true.  If confidentiality is not required this 
     * will return true.  If confidentially is required and the SSLFilter
     * is not engaged in the IoFilterChain this will return false.
     * 
     * This method is used by handlers to determine whether to send back
     * {@link ResultCodeEnum#CONFIDENTIALITY_REQUIRED} error responses back
     * to clients.
     * 
     * @param session the MINA IoSession to check for TLS security
     * @return true if confidentiality requirement is met, false otherwise
     */
    public final boolean isConfidentialityRequirementSatisfied( IoSession session )
    {
    	
    	if ( ! ldapServer.isConfidentialityRequired() )
    	{
    		return true;
    	}
    	
        IoFilterChain chain = session.getFilterChain();
        return chain.contains( "sslFilter" );
    }


    public final SessionRegistry getSessionRegistry()
    {
        return this.ldapServer.getRegistry();
    }


    /**
     * Return an array containing the controls for this message.
     *  
     * @param context The context in which we will store teh found controls
     * @param message The message for which we want to extract the controls
     */
    protected void setRequestControls( LdapContext context, Message message ) throws NamingException
    {
        MutableControl[] controls = null;
        
        if ( message.getControls() != null )
        {
            int nbControls = message.getControls().size();
            
            if ( nbControls != 0 )
            {
                controls = new MutableControl[ nbControls ];
                context.setRequestControls( message.getControls().values().toArray( controls ) );
            }
        }
    }
}
