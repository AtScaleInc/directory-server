/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.jndi;


import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.ldap.common.exception.LdapAuthenticationException;
import org.apache.ldap.common.exception.LdapAuthenticationNotSupportedException;
import org.apache.ldap.common.message.ResultCodeEnum;
import org.apache.ldap.common.util.StringTools;
import org.apache.ldap.server.auth.AbstractAuthenticator;
import org.apache.ldap.server.auth.AnonymousAuthenticator;
import org.apache.ldap.server.auth.AuthenticatorConfig;
import org.apache.ldap.server.auth.AuthenticatorContext;
import org.apache.ldap.server.auth.LdapPrincipal;
import org.apache.ldap.server.auth.SimpleAuthenticator;
import org.apache.ldap.server.jndi.invocation.Invocation;
import org.apache.ldap.server.jndi.invocation.interceptor.Interceptor;
import org.apache.ldap.server.jndi.invocation.interceptor.InterceptorContext;
import org.apache.ldap.server.jndi.invocation.interceptor.NextInterceptor;

/**
 * An {@link Interceptor} that authenticates users.
 *
 * @author Apache Directory Project (dev@directory.apache.org)
 * @author Alex Karasulu (akarasulu@apache.org)
 * @author Trustin Lee (trustin@apache.org)
 * @version $Rev$, $Date$
 */
public class Authenticator implements Interceptor
{
    /** short for Context.SECURITY_AUTHENTICATION */
    private static final String AUTH_TYPE = Context.SECURITY_AUTHENTICATION;

    /** short for Context.SECURITY_CREDENTIALS */
    private static final String CREDS = Context.SECURITY_CREDENTIALS;

    /** authenticators **/
    public Map authenticators = new LinkedHashMap();


    /**
     * Creates an authentication service interceptor.
     */
    public Authenticator()
    {
    }

    public void init( InterceptorContext ctx ) throws NamingException
    {
        /*
         * Create and add the Authentication service interceptor to before
         * interceptor chain.
         */
        boolean allowAnonymous = !ctx.getEnvironment().containsKey( EnvKeys.DISABLE_ANONYMOUS );

        // create authenticator context
        AuthenticatorContext authenticatorContext = new AuthenticatorContext();
        authenticatorContext.setPartitionNexus( ctx.getRootNexus() );
        authenticatorContext.setAllowAnonymous( allowAnonymous );

        try // initialize default authenticators
        {
            // create anonymous authenticator
            AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
            authenticatorConfig.setAuthenticatorName( "none" );
            authenticatorConfig.setAuthenticatorContext( authenticatorContext );

            org.apache.ldap.server.auth.Authenticator authenticator = new AnonymousAuthenticator();
            authenticator.init( authenticatorConfig );
            this.register( authenticator );

            // create simple authenticator
            authenticatorConfig = new AuthenticatorConfig();
            authenticatorConfig.setAuthenticatorName( "simple" );
            authenticatorConfig.setAuthenticatorContext( authenticatorContext );

            authenticator = new SimpleAuthenticator();
            authenticator.init( authenticatorConfig );
            this.register( authenticator );
        }
        catch ( Exception e )
        {
            throw new NamingException( e.getMessage() );
        }

        AuthenticatorConfig[] configs = null;
        configs = AuthenticatorConfigBuilder
                .getAuthenticatorConfigs( new Hashtable( ctx.getEnvironment() ) );

        for ( int ii = 0; ii < configs.length; ii++ )
        {
            try
            {
                configs[ii].setAuthenticatorContext( authenticatorContext );

                String authenticatorClass = configs[ii].getAuthenticatorClass();
                Class clazz = Class.forName( authenticatorClass );
                Constructor constructor = clazz.getConstructor( new Class[] { } );

                AbstractAuthenticator authenticator = ( AbstractAuthenticator ) constructor.newInstance( new Object[] { } );
                authenticator.init( configs[ii] );

                this.register( authenticator );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }

    }
    
    public void destroy()
    {
        authenticators.clear();
    }

    /**
     * Registers an Authenticator with this AuthenticatorService.  Called by each
     * Authenticator implementation after it has started to register for
     * authentication operation calls.
     *
     * @param authenticator Authenticator component to register with this
     * AuthenticatorService.
     */
    public void register( org.apache.ldap.server.auth.Authenticator authenticator )
    {
        Collection authenticatorList = getAuthenticators( authenticator.getType() );
        if ( authenticatorList == null )
        {
            authenticatorList = new ArrayList();
            authenticators.put( authenticator.getType(), authenticatorList );
        }
        authenticatorList.add( authenticator );
    }

    /**
     * Unregisters an Authenticator with this AuthenticatorService.  Called for each
     * registered Authenticator right before it is to be stopped.  This prevents
     * protocol server calls from reaching the Backend and effectively puts
     * the ContextPartition's naming context offline.
     *
     * @param authenticator Authenticator component to unregister with this
     * AuthenticatorService.
     */
    public void unregister( org.apache.ldap.server.auth.Authenticator authenticator )
    {
        Collection authenticatorList = getAuthenticators( authenticator.getType() );
        if ( authenticatorList == null )
        {
            return;
        }
        authenticatorList.remove( authenticator );
    }

    /**
     * Gets the authenticators with a specific type.
     *
     * @param type the authentication type
     * @return the authenticators with the specified type
     */
    public Collection getAuthenticators( String type )
    {
        return (Collection)authenticators.get( type );
    }
    
    public void process( NextInterceptor nextProcessor, Invocation call ) throws NamingException
    {
        // check if we are already authenticated and if so we return making
        // sure first that the credentials are not exposed within context
        ServerContext ctx = ( ServerLdapContext ) call.getContextStack().peek();
        if ( ctx.getPrincipal() != null )
        {
            if ( ctx.getEnvironment().containsKey( CREDS ) )
            {
                ctx.removeFromEnvironment( CREDS );
            }

            nextProcessor.process(call);
            return;
        }

        String authList = ( String ) ctx.getEnvironment().get( AUTH_TYPE );

        if ( authList == null )
        {
            if ( ctx.getEnvironment().containsKey( CREDS ) )
            {
                // authentication type is simple here
                authList = "simple";
            }
            else
            {
                // authentication type is anonymous
                authList = "none";
            }

        }

        authList = StringTools.deepTrim( authList );
        String[] auth = authList.split( " " );

        Collection authenticators = null;

        // pick the first matching authenticator type
        for ( int i=0; i<auth.length; i++)
        {
            authenticators = getAuthenticators( auth[i] );
            if ( authenticators != null ) break;
        }

        if ( authenticators == null )
        {
            ctx.getEnvironment(); // shut's up idea's yellow light
            ResultCodeEnum rc = ResultCodeEnum.AUTHMETHODNOTSUPPORTED;
            throw new LdapAuthenticationNotSupportedException( rc );
        }

        // try each authenticators
        for ( Iterator i = authenticators.iterator(); i.hasNext(); )
        {
            try
            {
                org.apache.ldap.server.auth.Authenticator authenticator =
                        ( org.apache.ldap.server.auth.Authenticator ) i.next();

                // perform the authentication
                LdapPrincipal authorizationId = authenticator.authenticate( ctx );

                // authentication was successful
                ctx.setPrincipal( authorizationId );

                // remove creds so there is no security risk
                ctx.removeFromEnvironment( CREDS );
                nextProcessor.process(call);
                return;
            }
            catch ( LdapAuthenticationException e )
            {
                // authentication failed, try the next authenticator
            }
        }

        throw new LdapAuthenticationException();
    }
}
