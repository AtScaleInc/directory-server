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


import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.spi.InitialContextFactory;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.kerberos.shared.store.operations.GetPrincipal;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.SessionRegistry;
import org.apache.directory.server.ldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.SaslFilter;
import org.apache.directory.server.protocol.shared.ServiceConfigurationException;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.message.BindRequest;
import org.apache.directory.shared.ldap.message.BindResponse;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ManageDsaITControl;
import org.apache.directory.shared.ldap.message.MutableControl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A single reply handler for {@link BindRequest}s.
 *
 * Implements server-side of RFC 2222, sections 4.2 and 4.3.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DefaultBindHandler extends BindHandler
{
    private static final Logger LOG = LoggerFactory.getLogger( BindHandler.class );

    /** An empty Control array used to get back the controls if any */
    private static final MutableControl[] EMPTY_CONTROL = new MutableControl[0];

    private DirContext ctx;

    /**
     * A Hashed Adapter mapping SASL mechanisms to their handlers.
     */
    private Map<String, MechanismHandler> handlers;

    private SessionRegistry registry;

    
    /**
     * Creates a new instance of BindHandler.
     */
    public DefaultBindHandler()
    {
        registry = null;
        handlers = null;
        ctx = null;
    }


    /**
     * Set the mechanisms handler map.
     * 
     * @param handlers The associations btween a machanism and its handler
     */
    public void setSaslMechanismHandlers( Map<String, MechanismHandler> handlers )
    {
        this.handlers = handlers;
    }


    /**
     * Associated a Registry to the handler
     *
     * @param registry The registry to attach
     */
    public void setSessionRegistry( SessionRegistry registry )
    {
        this.registry = registry;
    }


    public void setDirectoryService( DirectoryService directoryService )
    {
    }


    /**
     * Create an environment object and inject the Bond informations collected
     * from the BindRequest message :
     *  - the principal : the user's who issued the Bind request
     *  - the credentials : principal's password, if auth level is 'simple'
     *  - the authentication level : either 'simple' or 'strong'
     *  - how to handle referral : either 'ignore' or 'throw'
     *  
     * @param bindRequest the bind request object
     * @param authenticationLevel the level of the authentication
     * @return the environment for the session
     */
    private Hashtable<String, Object> getEnvironment( BindRequest bindRequest, String authenticationLevel )
    {
        LdapDN principal = bindRequest.getName();

        /**
         * For simple, this is a password.  For strong, this is unused.
         */
        Object credentials = bindRequest.getCredentials();

        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "{} {}", Context.SECURITY_PRINCIPAL, principal );
            LOG.debug( "{} {}", Context.SECURITY_CREDENTIALS, credentials );
            LOG.debug( "{} {}", Context.SECURITY_AUTHENTICATION, authenticationLevel );
        }

        // clone the environment first then add the required security settings
        Hashtable<String, Object> env = getSessionRegistry().getEnvironmentByCopy();

        // Store the principal
        env.put( Context.SECURITY_PRINCIPAL, principal );

        // Store the credentials
        if ( credentials != null )
        {
            env.put( Context.SECURITY_CREDENTIALS, credentials );
        }

        // Store the authentication level
        env.put( Context.SECURITY_AUTHENTICATION, authenticationLevel );

        // Store the referral handling method
        if ( bindRequest.getControls().containsKey( ManageDsaITControl.CONTROL_OID ) )
        {
            env.put( Context.REFERRAL, "ignore" );
        }
        else
        {
            env.put( Context.REFERRAL, "throw" );
        }

        return env;
    }

    /**
     * Create the Context associated with the BindRequest.
     *
     * @param bindRequest the bind request
     * @param env the environment to create the context with
     * @param session the MINA IoSession
     * @return the ldap context for the session
     */
    private LdapContext getLdapContext( IoSession session, BindRequest bindRequest, Hashtable<String, Object> env )
    {
        LdapResult result = bindRequest.getResultResponse().getLdapResult();
        LdapContext context = null;

        try
        {
            if ( env.containsKey( "server.use.factory.instance" ) )
            {
                InitialContextFactory factory = ( InitialContextFactory ) env.get( "server.use.factory.instance" );

                if ( factory == null )
                {
                    LOG.error( "The property 'server.use.factory.instance'  was set in env but was null" );
                    throw new NullPointerException( "server.use.factory.instance was set in env but was null" );
                }

                // Bind is a special case where we have to use the referral property to deal
                context = ( LdapContext ) factory.getInitialContext( env );
            }
            else
            {
                //noinspection SuspiciousToArrayCall
                MutableControl[] connCtls = bindRequest.getControls().values().toArray( EMPTY_CONTROL );
                context = new InitialLdapContext( env, connCtls );
            }
        }
        catch ( NamingException e )
        {
            ResultCodeEnum code = null;

            if ( e instanceof LdapException )
            {
                code = ( ( LdapException ) e ).getResultCode();
                result.setResultCode( code );
            }
            else
            {
                code = ResultCodeEnum.getBestEstimate( e, bindRequest.getType() );
                result.setResultCode( code );
            }

            String msg = "Bind failed: " + e.getMessage();

            if ( LOG.isDebugEnabled() )
            {
                msg += ":\n" + ExceptionUtils.getStackTrace( e );
                msg += "\n\nBindRequest = \n" + bindRequest.toString();
                LOG.debug(  msg  );
            }

            if ( ( e.getResolvedName() != null )
                && ( ( code == ResultCodeEnum.NO_SUCH_OBJECT ) || ( code == ResultCodeEnum.ALIAS_PROBLEM )
                    || ( code == ResultCodeEnum.INVALID_DN_SYNTAX ) || ( code == ResultCodeEnum.ALIAS_DEREFERENCING_PROBLEM ) ) )
            {
                result.setMatchedDn( ( LdapDN ) e.getResolvedName() );
            }

            result.setErrorMessage( msg );
            session.write( bindRequest.getResultResponse() );
            context = null;
        }

        return context;
    }

    /**
     * This method handles 'simple' authentication. 
     *
     * @param bindRequest the bind request
     * @param session the mina IoSession
     * @throws NamingException if the bind fails
     */
    private void handleSimpleAuth( IoSession session, BindRequest bindRequest ) throws NamingException
    {
        LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();

        // Initialize the environment which will be used to create the context
        Hashtable<String, Object> env = getEnvironment( bindRequest, AuthenticationLevel.SIMPLE.toString() );

        // Now, get the context
        LdapContext context = getLdapContext( session, bindRequest, env );

        // Test that we successfully got one. If not, an error has already been returned.
        if ( context != null )
        {
            ServerLdapContext newCtx = ( ServerLdapContext ) context.lookup( "" );
            setRequestControls( newCtx, bindRequest );
            getSessionRegistry().setLdapContext( session, newCtx );
            bindResult.setResultCode( ResultCodeEnum.SUCCESS );
            BindResponse response = ( BindResponse ) bindRequest.getResultResponse();
            response.addAll( newCtx.getResponseControls() );
            session.write( response );
            LOG.debug( "Returned SUCCESS message." );
        }
    }

    
    /**
     * Handle the SASL authentication.
     *
     * @param session The associated Session
     * @param message The BindRequest received
     * @throws Exception If the authentication cannot be done
     */
    public void handleSaslAuth( IoSession session, Object message ) throws Exception
    {
        LdapServer ldapServer = ( LdapServer )
                session.getAttribute( LdapServer.class.toString() );

        Map<String, String> saslProps = new HashMap<String, String>();
        saslProps.put( Sasl.QOP, ldapServer.getSaslQopString() );
        saslProps.put( "com.sun.security.sasl.digest.realm", getActiveRealms( ldapServer ) );
        session.setAttribute( "saslProps", saslProps );

        session.setAttribute( "saslHost", ldapServer.getSaslHost() );
        session.setAttribute( "baseDn", ldapServer.getSearchBaseDn() );

        Set<String> activeMechanisms = ldapServer.getSupportedMechanisms();

        if ( activeMechanisms.contains( SupportedSaslMechanisms.GSSAPI ) )
        {
            try
            {
                Subject saslSubject = getSubject( ldapServer );
                session.setAttribute( "saslSubject", saslSubject );
            }
            catch ( ServiceConfigurationException sce )
            {
                activeMechanisms.remove( "GSSAPI" );
                LOG.warn( sce.getMessage() );
            }
        }

        BindRequest bindRequest = ( BindRequest ) message;

        // Guard clause:  Reject unsupported SASL mechanisms.
        if ( !ldapServer.getSupportedMechanisms().contains( bindRequest.getSaslMechanism() ) )
        {
            LOG.error( "Bind error : {} mechanism not supported. Please check the server.xml " + 
                "configuration file (supportedMechanisms field)", 
                bindRequest.getSaslMechanism() );

            LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
            bindResult.setResultCode( ResultCodeEnum.AUTH_METHOD_NOT_SUPPORTED );
            bindResult.setErrorMessage( bindRequest.getSaslMechanism() + " is not a supported mechanism." );
            session.write( bindRequest.getResultResponse() );
            return;
        }

        handleSasl( session, bindRequest );
    }

    
    /**
     * Deal with a SASL bind request
     * 
     * @param session The IoSession for this Bind Request
     * @param bindRequest The BindRequest received
     * 
     * @exception Exception if the mechanism cannot handle the authentication
     */
    public void handleSasl( IoSession session, BindRequest bindRequest ) throws Exception
    {
        String sessionMechanism = bindRequest.getSaslMechanism();

        if ( sessionMechanism.equals( SupportedSaslMechanisms.PLAIN ) )
        {
            /*
             * This is the principal name that will be used to bind to the DIT.
             */
            session.setAttribute( Context.SECURITY_PRINCIPAL, bindRequest.getName() );

            /*
             * These are the credentials that will be used to bind to the DIT.
             * For the simple mechanism, this will be a password, possibly one-way hashed.
             */
            session.setAttribute( Context.SECURITY_CREDENTIALS, bindRequest.getCredentials() );

            getLdapContext( session, bindRequest );
        }
        else
        {
            MechanismHandler mechanismHandler = handlers.get( sessionMechanism );

            if ( mechanismHandler == null )
            {
                LOG.error( "Handler unavailable for " + sessionMechanism );
                throw new IllegalArgumentException( "Handler unavailable for " + sessionMechanism );
            }

            SaslServer ss = mechanismHandler.handleMechanism( session, bindRequest );
            
            LdapResult result = bindRequest.getResultResponse().getLdapResult();

            if ( !ss.isComplete() )
            {
                try
                {
                    /*
                     * SaslServer will throw an exception if the credentials are null.
                     */
                    if ( bindRequest.getCredentials() == null )
                    {
                        bindRequest.setCredentials( new byte[0] );
                    }

                    byte[] tokenBytes = ss.evaluateResponse( bindRequest.getCredentials() );

                    if ( ss.isComplete() )
                    {
                        if ( tokenBytes != null )
                        {
                            /*
                             * There may be a token to return to the client.  We set it here
                             * so it will be returned in a SUCCESS message, after an LdapContext
                             * has been initialized for the client.
                             */
                            session.setAttribute( "saslCreds", tokenBytes );
                        }

                        /*
                         * If we got here, we're ready to try getting an initial LDAP context.
                         */
                        getLdapContext( session, bindRequest );
                    }
                    else
                    {
                        LOG.info( "Continuation token had length " + tokenBytes.length );
                        result.setResultCode( ResultCodeEnum.SASL_BIND_IN_PROGRESS );
                        BindResponse resp = ( BindResponse ) bindRequest.getResultResponse();
                        resp.setServerSaslCreds( tokenBytes );
                        session.write( resp );
                        LOG.debug( "Returning final authentication data to client to complete context." );
                    }
                }
                catch ( SaslException se )
                {
                    LOG.error( se.getMessage() );
                    result.setResultCode( ResultCodeEnum.INVALID_CREDENTIALS );
                    result.setErrorMessage( se.getMessage() );
                    session.write( bindRequest.getResultResponse() );
                }
            }
        }
    }

    
    /**
     * Create a list of all the configured realms.
     * 
     * @param ldapServer the LdapServer for which we want to get the realms
     * @return a list of relms, separated by spaces
     */
    private String getActiveRealms( LdapServer ldapServer )
    {
        StringBuilder realms = new StringBuilder();
        boolean isFirst = true;

        for ( String realm:ldapServer.getSaslRealms() )
        {
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                realms.append( ' ' );
            }
            
            realms.append( realm );
        }

        return realms.toString();
    }


    private Subject getSubject( LdapServer ldapServer ) //throws ServiceConfigurationException
    {
        String servicePrincipalName = ldapServer.getSaslPrincipal();

        KerberosPrincipal servicePrincipal = new KerberosPrincipal( servicePrincipalName );
        GetPrincipal getPrincipal = new GetPrincipal( servicePrincipal );

        PrincipalStoreEntry entry = null;

        try
        {
            entry = findPrincipal( ldapServer, getPrincipal );
        }
        catch ( ServiceConfigurationException sce )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message, sce );
        }

        if ( entry == null )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message );
        }

        Subject subject = new Subject();

        for ( EncryptionType encryptionType:entry.getKeyMap().keySet() )
        {
            EncryptionKey key = entry.getKeyMap().get( encryptionType );

            byte[] keyBytes = key.getKeyValue();
            int type = key.getKeyType().getOrdinal();
            int kvno = key.getKeyVersion();

            KerberosKey serviceKey = new KerberosKey( servicePrincipal, keyBytes, type, kvno );

            subject.getPrivateCredentials().add( serviceKey );
        }

        return subject;
    }
    
    
    private PrincipalStoreEntry findPrincipal( LdapServer ldapServer, GetPrincipal getPrincipal )
    {
        if ( ctx == null )
        {
            try
            {
                LdapDN adminDN = new LdapDN( ServerDNConstants.ADMIN_SYSTEM_DN );
                
                adminDN.normalize( 
                    ldapServer.getDirectoryService().getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
                LdapPrincipal principal = new LdapPrincipal( adminDN, AuthenticationLevel.SIMPLE );
                ctx = ldapServer.getDirectoryService().getJndiContext( principal, ldapServer.getSearchBaseDn() );
            }
            catch ( NamingException ne )
            {
                String message = "Failed to get initial context " + ldapServer.getSearchBaseDn();
                throw new ServiceConfigurationException( message, ne );
            }
        }

        return (PrincipalStoreEntry)getPrincipal.execute( ctx, null );
    }    
    
    
    private Hashtable<String, Object> getEnvironment( IoSession session, BindRequest bindRequest )
    {
        Object principal = session.getAttribute( Context.SECURITY_PRINCIPAL );

        /**
         * For simple, this is a password.  For strong, this is unused.
         */
        Object credentials = session.getAttribute( Context.SECURITY_CREDENTIALS );

        String sessionMechanism = bindRequest.getSaslMechanism();
        String authenticationLevel = getAuthenticationLevel( sessionMechanism );

        LOG.debug( "{} {}", Context.SECURITY_PRINCIPAL, principal );
        LOG.debug( "{} {}", Context.SECURITY_CREDENTIALS, credentials );
        LOG.debug( "{} {}", Context.SECURITY_AUTHENTICATION, authenticationLevel );

        // clone the environment first then add the required security settings
        Hashtable<String, Object> env = registry.getEnvironmentByCopy();
        env.put( Context.SECURITY_PRINCIPAL, principal );

        if ( credentials != null )
        {
            env.put( Context.SECURITY_CREDENTIALS, credentials );
        }

        env.put( Context.SECURITY_AUTHENTICATION, authenticationLevel );

        if ( bindRequest.getControls().containsKey( ManageDsaITControl.CONTROL_OID ) )
        {
            env.put( Context.REFERRAL, "ignore" );
        }
        else
        {
            env.put( Context.REFERRAL, "throw" );
        }

        return env;
    }
    
    
    private void getLdapContext( IoSession session, BindRequest bindRequest ) //throws Exception
    {
        Hashtable<String, Object> env = getEnvironment( session, bindRequest );
        LdapResult result = bindRequest.getResultResponse().getLdapResult();
        LdapContext context = null;

        try
        {
            MutableControl[] connCtls = bindRequest.getControls().values().toArray( EMPTY_CONTROL );
            context = new InitialLdapContext( env, connCtls );

            registry.setLdapContext( session, context );
            
            // add the bind response controls 
            bindRequest.getResultResponse().addAll( context.getResponseControls() );
            
            returnSuccess( session, bindRequest );
        }
        catch ( NamingException e )
        {
            ResultCodeEnum code = null;

            if ( e instanceof LdapException )
            {
                code = ( ( LdapException ) e ).getResultCode();
                result.setResultCode( code );
            }
            else
            {
                code = ResultCodeEnum.getBestEstimate( e, bindRequest.getType() );
                result.setResultCode( code );
            }

            String msg = "Bind failed: " + e.getMessage();

            if ( LOG.isDebugEnabled() )
            {
                msg += ":\n" + ExceptionUtils.getStackTrace( e );
                msg += "\n\nBindRequest = \n" + bindRequest.toString();
            }

            if ( ( e.getResolvedName() != null )
                && ( ( code == ResultCodeEnum.NO_SUCH_OBJECT ) || ( code == ResultCodeEnum.ALIAS_PROBLEM )
                    || ( code == ResultCodeEnum.INVALID_DN_SYNTAX ) || ( code == ResultCodeEnum.ALIAS_DEREFERENCING_PROBLEM ) ) )
            {
                result.setMatchedDn( ( LdapDN ) e.getResolvedName() );
            }

            result.setErrorMessage( msg );
            session.write( bindRequest.getResultResponse() );
        }
    }

    
    private void returnSuccess( IoSession session, BindRequest bindRequest ) //throws Exception
    {
        /*
         * We have now both authenticated the client and retrieved a JNDI context for them.
         * We can return a success message to the client.
         */
        LdapResult result = bindRequest.getResultResponse().getLdapResult();

        byte[] tokenBytes = ( byte[] ) session.getAttribute( "saslCreds" );

        result.setResultCode( ResultCodeEnum.SUCCESS );
        BindResponse response = ( BindResponse ) bindRequest.getResultResponse();
        response.setServerSaslCreds( tokenBytes );

        String sessionMechanism = bindRequest.getSaslMechanism();

        /*
         * If the SASL mechanism is DIGEST-MD5 or GSSAPI, we insert a SASLFilter.
         */
        if ( sessionMechanism.equals( SupportedSaslMechanisms.DIGEST_MD5 ) ||
             sessionMechanism.equals( SupportedSaslMechanisms.GSSAPI ) )
        {
            LOG.debug( "Inserting SaslFilter to engage negotiated security layer." );

            IoFilterChain chain = session.getFilterChain();
            if ( !chain.contains( "SASL" ) )
            {
                SaslServer saslContext = ( SaslServer ) session.getAttribute( MechanismHandler.SASL_CONTEXT );
                chain.addBefore( "codec", "SASL", new SaslFilter( saslContext ) );
            }

            /*
             * We disable the SASL security layer once, to write the outbound SUCCESS
             * message without SASL security layer processing.
             */
            session.setAttribute( SaslFilter.DISABLE_SECURITY_LAYER_ONCE, Boolean.TRUE );
        }

        session.write( response );
        LOG.debug( "Returned SUCCESS message." );
    }

    
    /**
     * Convert a SASL mechanism to an Authentication level
     *
     * @param sessionMechanism The requested mechanism
     * @return The corresponding authentication level
     */
    private String getAuthenticationLevel( String sessionMechanism )
    {
        if ( sessionMechanism.equals( SupportedSaslMechanisms.PLAIN ) )
        {
            return AuthenticationLevel.SIMPLE.toString();
        }
        else
        {
            return AuthenticationLevel.STRONG.toString();
        }
    }
    
    
    /**
     * Deal with a received BindRequest
     * 
     * @param session The current session
     * @param bindRequest The received BindRequest
     * @throws Exception If the euthentication cannot be handled
     */
    protected void bindMessageReceived( IoSession session, BindRequest bindRequest ) throws Exception
    {
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "User {} is binding", bindRequest.getName() );

            if ( bindRequest.isSimple() )
            {
                LOG.debug( "Using simple authentication." );

            }
            else
            {
                LOG.debug( "Using SASL authentication with mechanism:  {}", bindRequest.getSaslMechanism() );
            }
        }
        
        // protect against insecure conns when confidentiality is required 
        if ( ! isConfidentialityRequirementSatisfied( session ) )
        {
        	LdapResult result = bindRequest.getResultResponse().getLdapResult();
        	result.setResultCode( ResultCodeEnum.CONFIDENTIALITY_REQUIRED );
        	result.setErrorMessage( "Confidentiality (TLS secured connection) is required." );
        	session.write( bindRequest.getResultResponse() );
        	return;
        }
        
        // Guard clause:  LDAP version 3
        if ( !bindRequest.getVersion3() )
        {
            LOG.error( "Bind error : Only LDAP v3 is supported." );
            LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
            bindResult.setResultCode( ResultCodeEnum.PROTOCOL_ERROR );
            bindResult.setErrorMessage( "Only LDAP v3 is supported." );
            session.write( bindRequest.getResultResponse() );
            return;
        }

        // Deal with the two kinds of authent :
        // - if it's simple, handle it in this class for speed
        // - for sasl, we go through a chain right now (but it may change in the near future)
        if ( bindRequest.isSimple() )
        {
            handleSimpleAuth( session, bindRequest );
        }
        else
        {
            handleSaslAuth( session, bindRequest );
        }
    }
}