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
package org.apache.directory.server.ldap.handlers.extended;


import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.directory.server.core.security.CoreKeyStoreSpi;
import org.apache.directory.server.ldap.ExtendedOperationHandler;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.shared.ldap.message.ExtendedRequest;
import org.apache.directory.shared.ldap.message.ExtendedResponse;
import org.apache.directory.shared.ldap.message.ExtendedResponseImpl;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.filter.ssl.SslFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Handler for the StartTLS extended operation.
 *
 * @org.apache.xbean.XBean
 * @see <a href="http://www.ietf.org/rfc/rfc2830.txt">RFC 2830</a>
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class StartTlsHandler implements ExtendedOperationHandler
{
    public static final String EXTENSION_OID = "1.3.6.1.4.1.1466.20037";

    private static final Set<String> EXTENSION_OIDS;
    private static final Logger LOG = LoggerFactory.getLogger( StartTlsHandler.class );
    
    private SSLContext sslContext;

    
    static
    {
        Set<String> set = new HashSet<String>( 3 );
        set.add( EXTENSION_OID );
        EXTENSION_OIDS = Collections.unmodifiableSet( set );
    }
    

    public void handleExtendedOperation( LdapSession session, ExtendedRequest req ) throws Exception
    {
        LOG.info( "Handling StartTLS request." );
        
        IoFilterChain chain = session.getIoSession().getFilterChain();
        SslFilter sslFilter = ( SslFilter ) chain.get( "sslFilter" );
        if( sslFilter == null )
        {
            sslFilter = new SslFilter( sslContext );
            chain.addFirst( "sslFilter", sslFilter );
        }
        else
        {
            sslFilter.startSsl( session.getIoSession() );
        }
        
        ExtendedResponse res = new ExtendedResponseImpl( req.getMessageId() );
        LdapResult result = res.getLdapResult();
        result.setResultCode( ResultCodeEnum.SUCCESS );
        res.setResponseName( EXTENSION_OID );
        res.setResponse( new byte[ 0 ] );

        // Send a response.
        session.getIoSession().setAttribute( SslFilter.DISABLE_ENCRYPTION_ONCE );
        session.getIoSession().write( res );
    }
    
    
    class ServerX509TrustManager implements X509TrustManager
    {
        public void checkClientTrusted( X509Certificate[] chain, String authType ) throws CertificateException
        {
            LOG.debug( "checkClientTrusted() called" );
        }

        public void checkServerTrusted( X509Certificate[] chain, String authType ) throws CertificateException
        {
            LOG.debug( "checkServerTrusted() called" );
        }

        public X509Certificate[] getAcceptedIssuers()
        {
            LOG.debug( "getAcceptedIssuers() called" );
            return new X509Certificate[0];
        }
    }


    public final Set<String> getExtensionOids()
    {
        return EXTENSION_OIDS;
    }


    public final String getOid()
    {
        return EXTENSION_OID;
    }

    
    public void setLdapServer( LdapService ldapService )
    {
        LOG.debug( "Setting LDAP Service" );
        Provider provider = Security.getProvider( "SUN" );
        LOG.debug( "provider = {}", provider );
        CoreKeyStoreSpi coreKeyStoreSpi = new CoreKeyStoreSpi( ldapService.getDirectoryService() );
        KeyStore keyStore = new KeyStore( coreKeyStoreSpi, provider, "JKS" ) {};

        try
        {
            keyStore.load( null, null );
        }
        catch ( Exception e1 )
        {
            throw new RuntimeException( "Failed on keystore load which should never really happen." );
        }
        
        KeyManagerFactory keyManagerFactory = null;
        try
        {
            keyManagerFactory = KeyManagerFactory.getInstance( "SunX509" );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to create KeyManagerFactory", e );
        }
        
        try
        {
            keyManagerFactory.init( keyStore, null );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to initialize KeyManagerFactory", e );
        }
        
        try
        {
            sslContext = SSLContext.getInstance( "TLS" );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to create SSLContext", e );
        }
        
        try
        {
            sslContext.init( keyManagerFactory.getKeyManagers(), 
                new TrustManager[] { new ServerX509TrustManager() }, 
                new SecureRandom() );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to initialize SSLContext", e );
        }
    }
}
