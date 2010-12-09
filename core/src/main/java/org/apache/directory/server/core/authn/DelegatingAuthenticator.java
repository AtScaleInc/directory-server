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
package org.apache.directory.server.core.authn;


import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionFactory;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.message.BindResponse;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.StringTools;


/**
 * Authenticator delegating to another LDAP server.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DelegatingAuthenticator extends AbstractAuthenticator
{
    /** A speedup for logger in debug mode */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();
    
    /** The host in charge of delegated authentication */
    private String delegateHost;
    
    /** The associated port */
    private int delegatePort;
    
    /**
     * Creates a new instance.
     * @see AbstractAuthenticator
     */
    public DelegatingAuthenticator()
    {
        super( AuthenticationLevel.SIMPLE );
    }


    /**
     * Creates a new instance, for a specific authentication level.
     * @see AbstractAuthenticator
     * @param type The relevant AuthenticationLevel
     */
    protected DelegatingAuthenticator( AuthenticationLevel type )
    {
        super( type );
    }


    /**
     * @return the delegateHost
     */
    public String getDelegateHost()
    {
        return delegateHost;
    }


    /**
     * @param delegateHost the delegateHost to set
     */
    public void setDelegateHost( String delegateHost )
    {
        this.delegateHost = delegateHost;
    }


    /**
     * @return the delegatePort
     */
    public int getDelegatePort()
    {
        return delegatePort;
    }


    /**
     * @param delegatePort the delegatePort to set
     */
    public void setDelegatePort( int delegatePort )
    {
        this.delegatePort = delegatePort;
    }


    /**
     * {@inheritDoc}
     */
    public LdapPrincipal authenticate( BindOperationContext bindContext )
            throws Exception
    {
        LdapPrincipal principal = null;
        
        if ( IS_DEBUG )
        {
            LOG.debug( "Authenticating {}", bindContext.getDn() );
        }
        
        // Create a connection on the remote host 
        LdapConnection ldapConnection = LdapConnectionFactory.getNetworkConnection( delegateHost, delegatePort );
        
        try
        {
            // Try to bind
            BindResponse bindResponse = ldapConnection.bind( bindContext.getDn(),
                StringTools.utf8ToString( bindContext.getCredentials() ) );
            
            if ( bindResponse.getLdapResult().getResultCode() != ResultCodeEnum.SUCCESS )
            {
                String message = I18n.err( I18n.ERR_230, bindContext.getDn().getName() );
                LOG.info( message );
                throw new LdapAuthenticationException( message );
            }
            else
            {
                // no need to remain bound to delegate host
                ldapConnection.unBind();
            }
            
            // Create the new principal
            principal = new LdapPrincipal( bindContext.getDn(), AuthenticationLevel.SIMPLE,
                bindContext.getCredentials() );
            
            return principal;

        }
        catch ( LdapException e )
        {
            // Bad password ...
            String message = I18n.err( I18n.ERR_230, bindContext.getDn().getName() );
            LOG.info( message );
            throw new LdapAuthenticationException( message );
        }
    }


    /**
     * We don't handle any password policy when using a delegated authentication
     */
    public void checkPwdPolicy( Entry userEntry ) throws LdapException
    {
        // no check for delegating authentication
    }


    /**
     * We don't handle any cache when using a delegated authentication
     */
    public void invalidateCache( DN bindDn )
    {
        // cache is not implemented here
    }
}
