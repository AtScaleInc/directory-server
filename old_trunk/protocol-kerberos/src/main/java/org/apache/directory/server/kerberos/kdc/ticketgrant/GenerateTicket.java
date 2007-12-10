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
package org.apache.directory.server.kerberos.kdc.ticketgrant;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.directory.server.kerberos.kdc.KdcConfiguration;
import org.apache.directory.server.kerberos.shared.crypto.encryption.CipherTextHandler;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KeyUsage;
import org.apache.directory.server.kerberos.shared.crypto.encryption.RandomKeyFactory;
import org.apache.directory.server.kerberos.shared.exceptions.ErrorType;
import org.apache.directory.server.kerberos.shared.exceptions.KerberosException;
import org.apache.directory.server.kerberos.shared.messages.KdcRequest;
import org.apache.directory.server.kerberos.shared.messages.components.Authenticator;
import org.apache.directory.server.kerberos.shared.messages.components.EncTicketPart;
import org.apache.directory.server.kerberos.shared.messages.components.EncTicketPartModifier;
import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.server.kerberos.shared.messages.value.AuthorizationData;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.messages.value.KdcOptions;
import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;
import org.apache.directory.server.kerberos.shared.messages.value.TicketFlags;
import org.apache.mina.common.IoSession;
import org.apache.mina.handler.chain.IoHandlerCommand;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class GenerateTicket implements IoHandlerCommand
{
    private String contextKey = "context";


    public void execute( NextCommand next, IoSession session, Object message ) throws Exception
    {
        TicketGrantingContext tgsContext = ( TicketGrantingContext ) session.getAttribute( getContextKey() );

        KdcRequest request = tgsContext.getRequest();
        Ticket tgt = tgsContext.getTgt();
        Authenticator authenticator = tgsContext.getAuthenticator();
        CipherTextHandler cipherTextHandler = tgsContext.getCipherTextHandler();
        KerberosPrincipal ticketPrincipal = request.getServerPrincipal();

        EncryptionType encryptionType = tgsContext.getEncryptionType();
        EncryptionKey serverKey = tgsContext.getRequestPrincipalEntry().getKeyMap().get( encryptionType );

        KdcConfiguration config = tgsContext.getConfig();

        EncTicketPartModifier newTicketBody = new EncTicketPartModifier();

        newTicketBody.setClientAddresses( tgt.getClientAddresses() );

        processFlags( config, request, tgt, newTicketBody );

        EncryptionKey sessionKey = RandomKeyFactory.getRandomKey( tgsContext.getEncryptionType() );
        newTicketBody.setSessionKey( sessionKey );

        newTicketBody.setClientPrincipal( tgt.getClientPrincipal() );

        if ( request.getEncAuthorizationData() != null )
        {
            AuthorizationData authData = ( AuthorizationData ) cipherTextHandler.unseal( AuthorizationData.class,
                authenticator.getSubSessionKey(), request.getEncAuthorizationData(), KeyUsage.NUMBER4 );
            authData.add( tgt.getAuthorizationData() );
            newTicketBody.setAuthorizationData( authData );
        }

        processTransited( newTicketBody, tgt );

        processTimes( config, request, newTicketBody, tgt );

        EncTicketPart ticketPart = newTicketBody.getEncTicketPart();

        if ( request.getOption( KdcOptions.ENC_TKT_IN_SKEY ) )
        {
            /*
             * if (server not specified) then
             *         server = req.second_ticket.client;
             * endif
             * 
             * if ((req.second_ticket is not a TGT) or
             *     (req.second_ticket.client != server)) then
             *         error_out(KDC_ERR_POLICY);
             * endif
             * 
             * new_tkt.enc-part := encrypt OCTET STRING using etype_for_key(second-ticket.key), second-ticket.key;
             */
            throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
        }
        else
        {
            EncryptedData encryptedData = cipherTextHandler.seal( serverKey, ticketPart, KeyUsage.NUMBER2 );

            Ticket newTicket = new Ticket( ticketPrincipal, encryptedData );
            newTicket.setEncTicketPart( ticketPart );

            tgsContext.setNewTicket( newTicket );
        }

        next.execute( session, message );
    }


    private void processFlags( KdcConfiguration config, KdcRequest request, Ticket tgt,
        EncTicketPartModifier newTicketBody ) throws KerberosException
    {
        if ( tgt.getFlag( TicketFlags.PRE_AUTHENT ) )
        {
            newTicketBody.setFlag( TicketFlags.PRE_AUTHENT );
        }

        if ( request.getOption( KdcOptions.FORWARDABLE ) )
        {
            if ( !config.isForwardableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.FORWARDABLE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            newTicketBody.setFlag( TicketFlags.FORWARDABLE );
        }

        if ( request.getOption( KdcOptions.FORWARDED ) )
        {
            if ( !config.isForwardableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.FORWARDABLE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            if ( request.getAddresses() != null && request.getAddresses().getAddresses() != null
                && request.getAddresses().getAddresses().length > 0 )
            {
                newTicketBody.setClientAddresses( request.getAddresses() );
            }
            else
            {
                if ( !config.isEmptyAddressesAllowed() )
                {
                    throw new KerberosException( ErrorType.KDC_ERR_POLICY );
                }
            }

            newTicketBody.setFlag( TicketFlags.FORWARDED );
        }

        if ( tgt.getFlag( TicketFlags.FORWARDED ) )
        {
            newTicketBody.setFlag( TicketFlags.FORWARDED );
        }

        if ( request.getOption( KdcOptions.PROXIABLE ) )
        {
            if ( !config.isProxiableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.PROXIABLE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            newTicketBody.setFlag( TicketFlags.PROXIABLE );
        }

        if ( request.getOption( KdcOptions.PROXY ) )
        {
            if ( !config.isProxiableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.PROXIABLE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            if ( request.getAddresses() != null && request.getAddresses().getAddresses() != null
                && request.getAddresses().getAddresses().length > 0 )
            {
                newTicketBody.setClientAddresses( request.getAddresses() );
            }
            else
            {
                if ( !config.isEmptyAddressesAllowed() )
                {
                    throw new KerberosException( ErrorType.KDC_ERR_POLICY );
                }
            }

            newTicketBody.setFlag( TicketFlags.PROXY );
        }

        if ( request.getOption( KdcOptions.ALLOW_POSTDATE ) )
        {
            if ( !config.isPostdatedAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.MAY_POSTDATE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            newTicketBody.setFlag( TicketFlags.MAY_POSTDATE );
        }

        /*
         * "Otherwise, if the TGT has the MAY-POSTDATE flag set, then the resulting
         * ticket will be postdated, and the requested starttime is checked against
         * the policy of the local realm.  If acceptable, the ticket's starttime is
         * set as requested, and the INVALID flag is set.  The postdated ticket MUST
         * be validated before use by presenting it to the KDC after the starttime
         * has been reached.  However, in no case may the starttime, endtime, or
         * renew-till time of a newly-issued postdated ticket extend beyond the
         * renew-till time of the TGT."
         */
        if ( request.getOption( KdcOptions.POSTDATED ) )
        {
            if ( !config.isPostdatedAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.MAY_POSTDATE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            newTicketBody.setFlag( TicketFlags.POSTDATED );
            newTicketBody.setFlag( TicketFlags.INVALID );

            newTicketBody.setStartTime( request.getFrom() );
        }

        if ( request.getOption( KdcOptions.VALIDATE ) )
        {
            if ( !config.isPostdatedAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.INVALID ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            KerberosTime startTime = ( tgt.getStartTime() != null ) ? tgt.getStartTime() : tgt.getAuthTime();

            if ( startTime.greaterThan( new KerberosTime() ) )
            {
                throw new KerberosException( ErrorType.KRB_AP_ERR_TKT_NYV );
            }

            /*
             * if (check_hot_list(tgt)) then
             *         error_out(KRB_AP_ERR_REPEAT);
             * endif
             */

            echoTicket( newTicketBody, tgt );
            newTicketBody.clearFlag( TicketFlags.INVALID );
        }

        if ( request.getOption( KdcOptions.RESERVED ) )
        {
            throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
        }
    }


    private void processTimes( KdcConfiguration config, KdcRequest request, EncTicketPartModifier newTicketBody,
        Ticket tgt ) throws KerberosException
    {
        KerberosTime now = new KerberosTime();

        newTicketBody.setAuthTime( tgt.getAuthTime() );

        KerberosTime startTime = request.getFrom();

        /*
         * "If the requested starttime is absent, indicates a time in the past,
         * or is within the window of acceptable clock skew for the KDC and the
         * POSTDATE option has not been specified, then the starttime of the
         * ticket is set to the authentication server's current time."
         */
        if ( startTime == null || startTime.lessThan( now ) || startTime.isInClockSkew( config.getAllowableClockSkew() )
            && !request.getOption( KdcOptions.POSTDATED ) )
        {
            startTime = now;
        }

        /*
         * "If it indicates a time in the future beyond the acceptable clock skew,
         * but the POSTDATED option has not been specified or the MAY-POSTDATE flag
         * is not set in the TGT, then the error KDC_ERR_CANNOT_POSTDATE is
         * returned."
         */
        if ( startTime != null && startTime.greaterThan( now )
            && !startTime.isInClockSkew( config.getAllowableClockSkew() )
            && ( !request.getOption( KdcOptions.POSTDATED ) || !tgt.getFlag( TicketFlags.MAY_POSTDATE ) ) )
        {
            throw new KerberosException( ErrorType.KDC_ERR_CANNOT_POSTDATE );
        }

        KerberosTime renewalTime = null;
        KerberosTime kerberosEndTime = null;

        if ( request.getOption( KdcOptions.RENEW ) )
        {
            if ( !config.isRenewableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            if ( !tgt.getFlag( TicketFlags.RENEWABLE ) )
            {
                throw new KerberosException( ErrorType.KDC_ERR_BADOPTION );
            }

            if ( tgt.getRenewTill().lessThan( now ) )
            {
                throw new KerberosException( ErrorType.KRB_AP_ERR_TKT_EXPIRED );
            }

            echoTicket( newTicketBody, tgt );

            newTicketBody.setStartTime( now );

            KerberosTime tgtStartTime = ( tgt.getStartTime() != null ) ? tgt.getStartTime() : tgt.getAuthTime();

            long oldLife = tgt.getEndTime().getTime() - tgtStartTime.getTime();

            kerberosEndTime = new KerberosTime( Math.min( tgt.getRenewTill().getTime(), now.getTime() + oldLife ) );
            newTicketBody.setEndTime( kerberosEndTime );
        }
        else
        {
            if ( newTicketBody.getEncTicketPart().getStartTime() == null )
            {
                newTicketBody.setStartTime( now );
            }

            KerberosTime till;
            if ( request.getTill().isZero() )
            {
                till = KerberosTime.INFINITY;
            }
            else
            {
                till = request.getTill();
            }

            /*
             * The end time is the minimum of (a) the requested till time or (b)
             * the start time plus maximum lifetime as configured in policy or (c)
             * the end time of the TGT.
             */
            List<KerberosTime> minimizer = new ArrayList<KerberosTime>();
            minimizer.add( till );
            minimizer.add( new KerberosTime( startTime.getTime() + config.getMaximumTicketLifetime() ) );
            minimizer.add( tgt.getEndTime() );
            kerberosEndTime = Collections.min( minimizer );

            newTicketBody.setEndTime( kerberosEndTime );

            if ( request.getOption( KdcOptions.RENEWABLE_OK ) && kerberosEndTime.lessThan( request.getTill() )
                && tgt.getFlag( TicketFlags.RENEWABLE ) )
            {
                if ( !config.isRenewableAllowed() )
                {
                    throw new KerberosException( ErrorType.KDC_ERR_POLICY );
                }

                // We set the RENEWABLE option for later processing.                           
                request.setOption( KdcOptions.RENEWABLE );
                long rtime = Math.min( request.getTill().getTime(), tgt.getRenewTill().getTime() );
                renewalTime = new KerberosTime( rtime );
            }
        }

        if ( renewalTime == null )
        {
            renewalTime = request.getRtime();
        }

        KerberosTime rtime;
        if ( renewalTime != null && renewalTime.isZero() )
        {
            rtime = KerberosTime.INFINITY;
        }
        else
        {
            rtime = renewalTime;
        }

        if ( request.getOption( KdcOptions.RENEWABLE ) && tgt.getFlag( TicketFlags.RENEWABLE ) )
        {
            if ( !config.isRenewableAllowed() )
            {
                throw new KerberosException( ErrorType.KDC_ERR_POLICY );
            }

            newTicketBody.setFlag( TicketFlags.RENEWABLE );

            /*
             * The renew-till time is the minimum of (a) the requested renew-till
             * time or (b) the start time plus maximum renewable lifetime as
             * configured in policy or (c) the renew-till time of the TGT.
             */
            List<KerberosTime> minimizer = new ArrayList<KerberosTime>();

            /*
             * 'rtime' KerberosTime is OPTIONAL
             */
            if ( rtime != null )
            {
                minimizer.add( rtime );
            }

            minimizer.add( new KerberosTime( startTime.getTime() + config.getMaximumRenewableLifetime() ) );
            minimizer.add( tgt.getRenewTill() );
            newTicketBody.setRenewTill( Collections.min( minimizer ) );
        }

        /*
         * "If the requested expiration time minus the starttime (as determined
         * above) is less than a site-determined minimum lifetime, an error
         * message with code KDC_ERR_NEVER_VALID is returned."
         */
        if ( kerberosEndTime.lessThan( startTime ) )
        {
            throw new KerberosException( ErrorType.KDC_ERR_NEVER_VALID );
        }

        long ticketLifeTime = Math.abs( startTime.getTime() - kerberosEndTime.getTime() );
        if ( ticketLifeTime < config.getAllowableClockSkew() )
        {
            throw new KerberosException( ErrorType.KDC_ERR_NEVER_VALID );
        }
    }


    /*
     * if (realm_tgt_is_for(tgt) := tgt.realm) then
     *         // tgt issued by local realm
     *         new_tkt.transited := tgt.transited;
     * else
     *         // was issued for this realm by some other realm
     *         if (tgt.transited.tr-type not supported) then
     *                 error_out(KDC_ERR_TRTYPE_NOSUPP);
     *         endif
     * 
     *         new_tkt.transited := compress_transited(tgt.transited + tgt.realm)
     * endif
     */
    private void processTransited( EncTicketPartModifier newTicketBody, Ticket tgt )
    {
        // TODO - currently no transited support other than local
        newTicketBody.setTransitedEncoding( tgt.getTransitedEncoding() );
    }


    protected void echoTicket( EncTicketPartModifier newTicketBody, Ticket tgt )
    {
        newTicketBody.setAuthorizationData( tgt.getAuthorizationData() );
        newTicketBody.setAuthTime( tgt.getAuthTime() );
        newTicketBody.setClientAddresses( tgt.getClientAddresses() );
        newTicketBody.setClientPrincipal( tgt.getClientPrincipal() );
        newTicketBody.setEndTime( tgt.getEndTime() );
        newTicketBody.setFlags( tgt.getFlags() );
        newTicketBody.setRenewTill( tgt.getRenewTill() );
        newTicketBody.setSessionKey( tgt.getSessionKey() );
        newTicketBody.setTransitedEncoding( tgt.getTransitedEncoding() );
    }


    protected String getContextKey()
    {
        return ( this.contextKey );
    }
}
