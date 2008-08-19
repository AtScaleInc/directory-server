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


import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.OperationAbandonedException;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.AbandonListener;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ManageDsaITControl;
import org.apache.directory.shared.ldap.message.PersistentSearchControl;
import org.apache.directory.shared.ldap.message.ReferralImpl;
import org.apache.directory.shared.ldap.message.Response;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.ResultResponse;
import org.apache.directory.shared.ldap.message.ScopeEnum;
import org.apache.directory.shared.ldap.message.SearchRequest;
import org.apache.directory.shared.ldap.message.SearchResponseDone;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ReferralException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;


/**
 * A handler for processing search requests.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultSearchHandler extends SearchHandler
{
    private static final Logger LOG = LoggerFactory.getLogger( SearchHandler.class );
    private static final String DEREFALIASES_KEY = JndiPropertyConstants.JNDI_LDAP_DAP_DEREF_ALIASES;

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /**
     * Builds the JNDI search controls for a SearchRequest.
     *
     * @param req the search request.
     * @param ids the ids to return
     * @return the SearchControls to use with the ApacheDS server side JNDI provider
     * @param isAdmin whether or not user is an admin
     * @param maxSize the maximum size for the search in # of entries returned
     * @param maxTime the maximum length of time for the search in seconds
     */
    private SearchControls getSearchControls( SearchRequest req, String[] ids, boolean isAdmin, int maxSize, int maxTime )
    {
        // prepare all the search controls
        SearchControls controls = new SearchControls();

        // take the minimum of system limit with request specified value
        if ( isAdmin )
        {
            controls.setCountLimit( req.getSizeLimit() );

            // The setTimeLimit needs a number of milliseconds
            // when the search control is expressed in seconds
            int timeLimit = req.getTimeLimit();

            // Just check that we are not exceeding the maximum for a long
            if ( timeLimit > Integer.MAX_VALUE / 1000 )
            {
                timeLimit = 0;
            }

            // The maximum time we can wait is around 24 days ...
            // Is it enough ? ;)
            controls.setTimeLimit( timeLimit * 1000 );
        }
        else
        {
            controls.setCountLimit( Math.min( req.getSizeLimit(), maxSize ) );
            controls.setTimeLimit( Math.min( req.getTimeLimit(), maxTime ) );
        }

        controls.setSearchScope( req.getScope().getValue() );
        controls.setReturningObjFlag( req.getTypesOnly() );
        controls.setReturningAttributes( ids );
        controls.setDerefLinkFlag( true );
        
        return controls;
    }


    /**
     * Determines if a search request is on the RootDSE of the server.
     * 
     * It is a RootDSE search if :
     * - the base DN is empty
     * - and the scope is BASE OBJECT
     * - and the filter is (ObjectClass = *)
     * 
     * (RFC 4511, 5.1, par. 1 & 2)
     *
     * @param req the request issued
     * @return true if the search is on the RootDSE false otherwise
     */
    private static boolean isRootDSESearch( SearchRequest req )
    {
        boolean isBaseIsRoot = req.getBase().isEmpty();
        boolean isBaseScope = req.getScope() == ScopeEnum.BASE_OBJECT;
        boolean isRootDSEFilter = false;
        
        if ( req.getFilter() instanceof PresenceNode )
        {
            String attribute = ( ( PresenceNode ) req.getFilter() ).getAttribute();
            isRootDSEFilter = attribute.equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) ||
                                attribute.equals( SchemaConstants.OBJECT_CLASS_AT_OID );
        }
        
        return isBaseIsRoot && isBaseScope && isRootDSEFilter;
    }

    
    private void handlePersistentSearch( IoSession session, SearchRequest req, ServerLdapContext ctx, 
        SearchControls controls, PersistentSearchControl psearchControl, 
        NamingEnumeration<SearchResult> list ) throws NamingException 
    {
        // there are no limits for psearch processing
        controls.setCountLimit( 0 );
        controls.setTimeLimit( 0 );

        if ( !psearchControl.isChangesOnly() )
        {
            list = ctx.search( req.getBase(), req.getFilter(),
                controls );
            
            if ( list instanceof AbandonListener )
            {
                req.addAbandonListener( ( AbandonListener ) list );
            }
            
            if ( list.hasMore() )
            {
                Iterator<Response> it = new SearchResponseIterator( req, ctx, list, controls.getSearchScope(),
                        session, getSessionRegistry() );
                
                while ( it.hasNext() )
                {
                    Response resp = it.next();
                    
                    if ( resp instanceof SearchResponseDone )
                    {
                        // ok if normal search beforehand failed somehow quickly abandon psearch
                        ResultCodeEnum rcode = ( ( SearchResponseDone ) resp ).getLdapResult().getResultCode();

                        if ( rcode != ResultCodeEnum.SUCCESS )
                        {
                            session.write( resp );
                            return;
                        }
                        // if search was fine then we returned all entries so now
                        // instead of returning the DONE response we break from the
                        // loop and user the notification listener to send back
                        // notificationss to the client in never ending search
                        else
                        {
                            break;
                        }
                    }
                    else
                    {
                        session.write( resp );
                    }
                }
            }
        }

        // now we process entries for ever as they change
        PersistentSearchListener handler = new PersistentSearchListener( getSessionRegistry(),
                ctx, session, req );
        ctx.addNamingListener( req.getBase(), req.getFilter().toString(), controls, handler );
        return;
    }

    /**
     * Main message handing method for search requests.
     */
    public void searchMessageReceived( IoSession session, SearchRequest req ) throws Exception
    {
        LdapServer ldapServer = ( LdapServer )
                session.getAttribute(  LdapServer.class.toString() );

        if ( IS_DEBUG )
        {
            LOG.debug( "Message received:  {}", req.toString() );
        }

        ServerLdapContext ctx;
        NamingEnumeration<SearchResult> list = null;
        String[] ids = null;
        Collection<String> retAttrs = new HashSet<String>();
        retAttrs.addAll( req.getAttributes() );

        // add the search request to the registry of outstanding requests for this session
        getSessionRegistry().addOutstandingRequest( session, req );

        // check the attributes to see if a referral's ref attribute is included
        if ( retAttrs.size() > 0 && !retAttrs.contains( SchemaConstants.REF_AT ) )
        {
            retAttrs.add( SchemaConstants.REF_AT );
            ids = retAttrs.toArray( ArrayUtils.EMPTY_STRING_ARRAY );
        }
        else if ( retAttrs.size() > 0 )
        {
            ids = retAttrs.toArray( ArrayUtils.EMPTY_STRING_ARRAY );
        }

        try
        {
            // protect against insecure conns when confidentiality is required 
            if ( ! isConfidentialityRequirementSatisfied( session ) )
            {
            	LdapResult result = req.getResultResponse().getLdapResult();
            	result.setResultCode( ResultCodeEnum.CONFIDENTIALITY_REQUIRED );
            	result.setErrorMessage( "Confidentiality (TLS secured connection) is required." );
            	session.write( req.getResultResponse() );
            	return;
            }

            // ===============================================================
            // Find session context
            // ===============================================================

            boolean isRootDSESearch = isRootDSESearch( req );

            // bypass checks to disallow anonymous binds for search on RootDSE with base obj scope
            if ( isRootDSESearch )
            {
                LdapContext unknown = getSessionRegistry().getLdapContextOnRootDSEAccess( session, null );

                if ( !( unknown instanceof ServerLdapContext ) )
                {
                    ctx = ( ServerLdapContext ) unknown.lookup( "" );
                }
                else
                {
                    ctx = ( ServerLdapContext ) unknown;
                }
            }
            // all those search operations are subject to anonymous bind checks when anonymous binda are disallowed
            else
            {
                LdapContext unknown = getSessionRegistry().getLdapContext( session, null, true );

                if ( !( unknown instanceof ServerLdapContext ) )
                {
                    ctx = ( ServerLdapContext ) unknown.lookup( "" );
                }
                else
                {
                    ctx = ( ServerLdapContext ) unknown;
                }
            }

            // Inject controls into the context
            setRequestControls( ctx, req );

            ctx.addToEnvironment( DEREFALIASES_KEY, req.getDerefAliases().getJndiValue() );

            if ( req.getControls().containsKey( ManageDsaITControl.CONTROL_OID ) )
            {
                ctx.addToEnvironment( Context.REFERRAL, "ignore" );
            }
            else
            {
                ctx.addToEnvironment( Context.REFERRAL, "throw-finding-base" );
            }

            // ===============================================================
            // Handle anonymous binds
            // ===============================================================

            boolean allowAnonymousBinds = ldapServer.isAllowAnonymousAccess();
            boolean isAnonymousUser = ctx.getPrincipal().getName().trim().equals( "" );

            if ( isAnonymousUser && !allowAnonymousBinds && !isRootDSESearch )
            {
                LdapResult result = req.getResultResponse().getLdapResult();
                result.setResultCode( ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS );
                String msg = "Bind failure: Anonymous binds have been disabled!";
                result.setErrorMessage( msg );
                session.write( req.getResultResponse() );
                return;
            }


            // ===============================================================
            // Set search limits differently based on user's identity
            // ===============================================================

            int maxSize = ldapServer.getMaxSizeLimit();
            int maxTime = ldapServer.getMaxTimeLimit();

            SearchControls controls;
            
            if ( isAnonymousUser )
            {
                controls = getSearchControls( req, ids, false, maxSize, maxTime );
            }
            else if ( ctx.getPrincipal().getName()
                .trim().equals( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED ) )
            {
                controls = getSearchControls( req, ids, true, maxSize, maxTime );
            }
            else
            {
                controls = getSearchControls( req, ids, false, maxSize, maxTime );
            }


            // ===============================================================
            // Handle psearch differently
            // ===============================================================

            PersistentSearchControl psearchControl = ( PersistentSearchControl ) req.getControls().get(
                PersistentSearchControl.CONTROL_OID );
            
            if ( psearchControl != null )
            {
                handlePersistentSearch( session, req, ctx, controls, psearchControl, list );
                return;
            }

            // ===============================================================
            // Handle regular search requests from here down
            // ===============================================================

            /*
             * Iterate through all search results building and sending back responses
             * for each search result returned.
             */
            list = ctx.search( req.getBase(), req.getFilter(), controls, ( InetSocketAddress ) session.getRemoteAddress() );
            
            if ( list instanceof AbandonListener )
            {
                req.addAbandonListener( ( AbandonListener ) list );
            }

            if ( list.hasMore() )
            {
                Iterator<Response> it = new SearchResponseIterator( req, ctx, list, controls.getSearchScope(),
                        session, getSessionRegistry() );
                
                while ( it.hasNext() )
                {
                    session.write( it.next() );
                }
            }
            else
            {
                list.close();
                req.getResultResponse().getLdapResult().setResultCode( ResultCodeEnum.SUCCESS );
                
                for ( ResultResponse resultResponse : Collections.singleton( req.getResultResponse() ) )
                {
                    session.write( resultResponse );
                }
            }
        }
        catch ( ReferralException e )
        {
            LdapResult result = req.getResultResponse().getLdapResult();
            ReferralImpl refs = new ReferralImpl();
            result.setReferral( refs );
            result.setResultCode( ResultCodeEnum.REFERRAL );
            result.setErrorMessage( "Encountered referral attempting to handle add request." );

            do
            {
                refs.addLdapUrl( ( String ) e.getReferralInfo() );
            }
            while ( e.skipReferral() );
            
            session.write( req.getResultResponse() );
            getSessionRegistry().removeOutstandingRequest( session, req.getMessageId() );
        }
        catch ( NamingException e )
        {
            /*
             * From RFC 2251 Section 4.11:
             *
             * In the event that a server receives an Abandon Request on a Search
             * operation in the midst of transmitting responses to the Search, that
             * server MUST cease transmitting entry responses to the abandoned
             * request immediately, and MUST NOT send the SearchResultDone. Of
             * course, the server MUST ensure that only properly encoded LDAPMessage
             * PDUs are transmitted.
             *
             * SO DON'T SEND BACK ANYTHING!!!!!
             */
            if ( e instanceof OperationAbandonedException )
            {
                return;
            }

            String msg = "failed on search operation: " + e.getMessage();
            
            if ( LOG.isDebugEnabled() )
            {
                msg += ":\n" + req + ":\n" + ExceptionUtils.getStackTrace( e );
            }

            ResultCodeEnum code;
            
            if ( e instanceof LdapException )
            {
                code = ( ( LdapException ) e ).getResultCode();
            }
            else
            {
                code = ResultCodeEnum.getBestEstimate( e, req.getType() );
            }

            LdapResult result = req.getResultResponse().getLdapResult();
            result.setResultCode( code );
            result.setErrorMessage( msg );

            if ( ( e.getResolvedName() != null )
                && ( ( code == ResultCodeEnum.NO_SUCH_OBJECT ) || ( code == ResultCodeEnum.ALIAS_PROBLEM )
                    || ( code == ResultCodeEnum.INVALID_DN_SYNTAX ) || ( code == ResultCodeEnum.ALIAS_DEREFERENCING_PROBLEM ) ) )
            {
                result.setMatchedDn( (LdapDN)e.getResolvedName() );
            }

            for ( ResultResponse resultResponse : Collections.singleton( req.getResultResponse() ) )
            {
                session.write( resultResponse );
            }
            
            getSessionRegistry().removeOutstandingRequest( session, req.getMessageId() );
        }
        finally
        {
            if ( list != null )
            {
                try
                {
                    list.close();
                }
                catch ( NamingException e )
                {
                    LOG.error( "failed on list.close()", e );
                }
            }
        }
    }
}