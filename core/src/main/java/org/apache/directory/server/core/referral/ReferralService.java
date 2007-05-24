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
package org.apache.directory.server.core.referral;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.directory.server.core.DirectoryServiceConfiguration;
import org.apache.directory.server.core.authn.AuthenticationService;
import org.apache.directory.server.core.authz.AuthorizationService;
import org.apache.directory.server.core.authz.DefaultAuthorizationService;
import org.apache.directory.server.core.configuration.InterceptorConfiguration;
import org.apache.directory.server.core.enumeration.ReferralHandlingEnumeration;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.event.EventService;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.core.normalization.NormalizationService;
import org.apache.directory.server.core.operational.OperationalAttributeService;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.server.core.schema.SchemaService;
import org.apache.directory.server.core.subtree.SubentryService;
import org.apache.directory.server.core.trigger.TriggerService;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.codec.util.LdapURL;
import org.apache.directory.shared.ldap.codec.util.LdapURLEncodingException;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.exception.LdapReferralException;
import org.apache.directory.shared.ldap.filter.AssertionEnum;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An service which is responsible referral handling behavoirs.  It manages 
 * referral handling behavoir when the {@link Context.REFERRAL} is implicitly
 * or explicitly set to "ignore", when set to "throw" and when set to "follow". 
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ReferralService extends BaseInterceptor
{
    /** The service name */
    public static final String NAME = "referralService";
    
    private static final Logger log = LoggerFactory.getLogger( ReferralService.class );
    private static final String IGNORE = "ignore";
    private static final String THROW_FINDING_BASE = "throw-finding-base";
    private static final String THROW = "throw";
    private static final String FOLLOW = "follow";
    private static final String REFERRAL_OC = "referral";
    private static final Collection<String> SEARCH_BYPASS;
    private static final String REF_ATTR = "ref";

    private ReferralLut lut = new ReferralLut();
    private PartitionNexus nexus;
    private Hashtable env;
    private AttributeTypeRegistry attrRegistry;
    private OidRegistry oidRegistry;

    
    static
    {
        /*
         * These are the services that we will bypass while searching for referrals in
         * partitions of the system during startup and during add/remove partition ops
         */
        Collection<String> c = new HashSet<String>();
        c.add( NormalizationService.NAME );
        c.add( AuthenticationService.NAME );
        c.add( AuthorizationService.NAME );
        c.add( DefaultAuthorizationService.NAME );
        c.add( SchemaService.NAME );
        c.add( SubentryService.NAME );
        c.add( OperationalAttributeService.NAME );
        c.add( ReferralService.NAME );
        c.add( EventService.NAME );
        c.add( TriggerService.NAME );
        SEARCH_BYPASS = Collections.unmodifiableCollection( c );
    }


    static boolean hasValue( Attribute attr, String value ) throws NamingException
    {
        if ( attr == null )
        {
            return false;
        }
        for ( int ii = 0; ii < attr.size(); ii++ )
        {
            if ( !( attr.get( ii ) instanceof String ) )
            {
                continue;
            }
            if ( value.equalsIgnoreCase( ( String ) attr.get( ii ) ) )
            {
                return true;
            }
        }
        return false;
    }


    static boolean isReferral( Attributes entry ) throws NamingException
    {
        Attribute oc = entry.get( SchemaConstants.OBJECT_CLASS_AT );
        if ( oc == null )
        {
            log.warn( "could not find objectClass attribute in entry: " + entry );
            return false;
        }
        for ( int ii = 0; ii < oc.size(); ii++ )
        {
            if ( REFERRAL_OC.equalsIgnoreCase( ( String ) oc.get( ii ) ) )
            {
                return true;
            }
        }
        return false;
    }


    public void init( DirectoryServiceConfiguration dsConfig, InterceptorConfiguration cfg ) throws NamingException
    {
        nexus = dsConfig.getPartitionNexus();
        attrRegistry = dsConfig.getRegistries().getAttributeTypeRegistry();
        oidRegistry = dsConfig.getRegistries().getOidRegistry();
        env = dsConfig.getEnvironment();

        Iterator suffixes = nexus.listSuffixes( null );
        
        while ( suffixes.hasNext() )
        {
            LdapDN suffix = new LdapDN( ( String ) suffixes.next() );
            addReferrals( 
                nexus.search( 
                    new SearchOperationContext( suffix, env, getReferralFilter(), getControls() ) ), suffix );
        }
    }


    public void doReferralException( LdapDN farthest, LdapDN targetUpdn, Attribute refs ) throws NamingException
    {
        // handle referral here
        List<String> list = new ArrayList<String>( refs.size() );
        
        for ( int ii = 0; ii < refs.size(); ii++ )
        {
            String val = ( String ) refs.get( ii );

            // need to add non-ldap URLs as-is
            if ( !val.startsWith( "ldap" ) )
            {
                list.add( val );
                continue;
            }

            // parse the ref value and normalize the DN according to schema 
            LdapURL ldapUrl = new LdapURL();
            try
            {
                ldapUrl.parse( val.toCharArray() );
            }
            catch ( LdapURLEncodingException e )
            {
                log.error( "Bad URL (" + val + ") for ref in " + farthest + ".  Reference will be ignored." );
            }

            LdapDN urlDn = new LdapDN( ldapUrl.getDn().toNormName() );
            urlDn.normalize( attrRegistry.getNormalizerMapping() );
            
            if ( urlDn.equals( farthest ) )
            {
                // according to the protocol there is no need for the dn since it is the same as this request
                StringBuffer buf = new StringBuffer();
                buf.append( ldapUrl.getScheme() );
                buf.append( ldapUrl.getHost() );
            
                if ( ldapUrl.getPort() > 0 )
                {
                    buf.append( ":" );
                    buf.append( ldapUrl.getPort() );
                }

                list.add( buf.toString() );
                continue;
            }

            /*
             * If we get here then the DN of the referral was not the same as the 
             * DN of the ref LDAP URL.  We must calculate the remaining (difference)
             * name past the farthest referral DN which the target name extends.
             */
            int diff = targetUpdn.size() - farthest.size();
            LdapDN extra = new LdapDN();
            
            for ( int jj = 0; jj < diff; jj++ )
            {
                extra.add( targetUpdn.get( farthest.size() + jj ) );
            }

            urlDn.addAll( extra );
            StringBuffer buf = new StringBuffer();
            buf.append( ldapUrl.getScheme() );
            buf.append( ldapUrl.getHost() );
            
            if ( ldapUrl.getPort() > 0 )
            {
                buf.append( ":" );
                buf.append( ldapUrl.getPort() );
            }
            
            buf.append( "/" );
            buf.append( LdapURL.urlEncode( urlDn.getUpName(), false ) );
            list.add( buf.toString() );
        }
        
        LdapReferralException lre = new LdapReferralException( list );
        throw lre;
    }


    public void add(NextInterceptor next, OperationContext opContext ) throws NamingException
    {
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );
        LdapDN name = opContext.getDn();
        Attributes entry = ((AddOperationContext)opContext).getEntry();

        // handle a normal add without following referrals
        if ( ( refval == null ) || refval.equals( IGNORE ) )
        {
            next.add( opContext );
            
            if ( isReferral( entry ) )
            {
                lut.referralAdded( name );
            }
            
            return;
        }
        else if ( refval.equals( THROW ) )
        {
            LdapDN farthest = lut.getFarthestReferralAncestor( name );
        
            if ( farthest == null )
            {
                next.add( opContext );
                
                if ( isReferral( entry ) )
                {
                    lut.referralAdded( name );
                }
                return;
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            AttributeType refsType = attrRegistry.lookup( oidRegistry.getOid( REF_ATTR ) );
            Attribute refs = AttributeUtils.getAttribute( referral, refsType );
            doReferralException( farthest, new LdapDN( name.getUpName() ), refs );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    public boolean compare( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
    	LdapDN name = opContext.getDn();
    	
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );

        // handle a normal add without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            return next.compare( opContext );
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthest = lut.getFarthestReferralAncestor( name );
            if ( farthest == null )
            {
                return next.compare( opContext );
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            Attribute refs = referral.get( REF_ATTR );
            doReferralException( farthest, new LdapDN( name.getUpName() ), refs );

            // we really can't get here since doReferralException will throw an exception
            return false;
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    public void delete( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
    	LdapDN name = opContext.getDn();
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );

        // handle a normal delete without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            next.delete( opContext );
            
            if ( lut.isReferral( name ) )
            {
                lut.referralDeleted( name );
            }
            
            return;
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthest = lut.getFarthestReferralAncestor( name );
            
            if ( farthest == null )
            {
                next.delete( opContext );
                
                if ( lut.isReferral( name ) )
                {
                    lut.referralDeleted( name );
                }
                
                return;
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            Attribute refs = referral.get( REF_ATTR );
            doReferralException( farthest, new LdapDN( name.getUpName() ), refs );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    /* -----------------------------------------------------------------------
     * Special handling instructions for ModifyDn operations:
     * ======================================================
     * 
     * From RFC 3296 here => http://www.ietf.org/rfc/rfc3296.txt
     * 
     * 5.6.2 Modify DN
     *
     * If the newSuperior is a referral object or is subordinate to a
     * referral object, the server SHOULD return affectsMultipleDSAs.  If
     * the newRDN already exists but is a referral object, the server SHOULD
     * return affectsMultipleDSAs instead of entryAlreadyExists.
     * -----------------------------------------------------------------------
     */

    public void move( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
        LdapDN oldName = opContext.getDn();
        
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );
        LdapDN newName = ( LdapDN ) ((MoveOperationContext)opContext).getParent().clone();
        newName.add( oldName.get( oldName.size() - 1 ) );

        // handle a normal modify without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            next.move( opContext );
            
            if ( lut.isReferral( oldName ) )
            {
                lut.referralChanged( oldName, newName );
            }
            
            return;
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthestSrc = lut.getFarthestReferralAncestor( oldName );
            LdapDN farthestDst = lut.getFarthestReferralAncestor( newName ); // note will not return newName so safe
            if ( farthestSrc == null && farthestDst == null && !lut.isReferral( newName ) )
            {
                next.move( opContext );
                
                if ( lut.isReferral( oldName ) )
                {
                    lut.referralChanged( oldName, newName );
                }
                
                return;
            }
            else if ( farthestSrc != null )
            {
                Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthestSrc ),
                    PartitionNexusProxy.LOOKUP_BYPASS );
                Attribute refs = referral.get( REF_ATTR );
                doReferralException( farthestSrc, new LdapDN( oldName.getUpName() ), refs );
            }
            else if ( farthestDst != null )
            {
                throw new LdapNamingException( farthestDst + " ancestor is a referral for modifyDn on " + newName
                    + " so it affects multiple DSAs", ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }
            else if ( lut.isReferral( newName ) )
            {
                throw new LdapNamingException( newName
                    + " exists and is a referral for modifyDn destination so it affects multiple DSAs",
                    ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }

            throw new IllegalStateException( "If you get this exception the server's logic was flawed in handling a "
                + "modifyDn operation while processing referrals.  Report this as a bug!" );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    public void moveAndRename( NextInterceptor next, OperationContext opContext )
        throws NamingException
    {
        LdapDN oldName = opContext.getDn();
        
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );
        LdapDN newName = ( LdapDN ) ((MoveAndRenameOperationContext)opContext).getParent().clone();
        newName.add( ((MoveAndRenameOperationContext)opContext).getNewRdn() );

        // handle a normal modify without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            next.moveAndRename( opContext );
            if ( lut.isReferral( oldName ) )
            {
                lut.referralChanged( oldName, newName );
            }
            return;
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthestSrc = lut.getFarthestReferralAncestor( oldName );
            LdapDN farthestDst = lut.getFarthestReferralAncestor( newName ); // safe to use - does not return newName
            if ( farthestSrc == null && farthestDst == null && !lut.isReferral( newName ) )
            {
                next.moveAndRename( opContext );
                if ( lut.isReferral( oldName ) )
                {
                    lut.referralChanged( oldName, newName );
                }
                return;
            }
            else if ( farthestSrc != null )
            {
                Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthestSrc ),
                    PartitionNexusProxy.LOOKUP_BYPASS );
                Attribute refs = referral.get( REF_ATTR );
                doReferralException( farthestSrc, new LdapDN( oldName.getUpName() ), refs );
            }
            else if ( farthestDst != null )
            {
                throw new LdapNamingException( farthestDst + " ancestor is a referral for modifyDn on " + newName
                    + " so it affects multiple DSAs", ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }
            else if ( lut.isReferral( newName ) )
            {
                throw new LdapNamingException( newName
                    + " exists and is a referral for modifyDn destination so it affects multiple DSAs",
                    ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }

            throw new IllegalStateException( "If you get this exception the server's logic was flawed in handling a "
                + "modifyDn operation while processing referrals.  Report this as a bug!" );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    public void rename( NextInterceptor next, OperationContext opContext )
        throws NamingException
    {
        LdapDN oldName = opContext.getDn();
        
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );
        LdapDN newName = ( LdapDN ) oldName.clone();
        newName.remove( oldName.size() - 1 );

        LdapDN newRdnName = new LdapDN( ((RenameOperationContext)opContext).getNewRdn() );
        newRdnName.normalize( attrRegistry.getNormalizerMapping() );
        newName.add( newRdnName.toNormName() );

        // handle a normal modify without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            next.rename( opContext );
            
            if ( lut.isReferral( oldName ) )
            {
                lut.referralChanged( oldName, newName );
            }
            
            return;
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthestSrc = lut.getFarthestReferralAncestor( oldName );
            LdapDN farthestDst = lut.getFarthestReferralAncestor( newName );
            
            if ( farthestSrc == null && farthestDst == null && !lut.isReferral( newName ) )
            {
                next.rename( opContext );
                
                if ( lut.isReferral( oldName ) )
                {
                    lut.referralChanged( oldName, newName );
                }
                
                return;
            }
            
            if ( farthestSrc != null )
            {
                Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthestSrc ),
                    PartitionNexusProxy.LOOKUP_BYPASS );
                Attribute refs = referral.get( REF_ATTR );
                doReferralException( farthestSrc, new LdapDN( oldName.getUpName() ), refs );
            }
            else if ( farthestDst != null )
            {
                throw new LdapNamingException( farthestDst + " ancestor is a referral for modifyDn on " + newName
                    + " so it affects multiple DSAs", ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }
            else if ( lut.isReferral( newName ) )
            {
                throw new LdapNamingException( newName
                    + " exists and is a referral for modifyDn destination so it affects multiple DSAs",
                    ResultCodeEnum.AFFECTS_MULTIPLE_DSAS );
            }

            throw new IllegalStateException( "If you get this exception the server's logic was flawed in handling a "
                + "modifyDn operation while processing referrals.  Report this as a bug!" );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    private void checkModify( LdapDN name, ModificationItemImpl[] mods ) throws NamingException
    {
        boolean isTargetReferral = lut.isReferral( name );

        // -------------------------------------------------------------------
        // Check and update lut if we change the objectClass 
        // -------------------------------------------------------------------

        for ( int ii = 0; ii < mods.length; ii++ )
        {
            if ( mods[ii].getAttribute().getID().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) )
            {
                boolean modsOcHasReferral = hasValue( mods[ii].getAttribute(), REFERRAL_OC );

                switch ( mods[ii].getModificationOp() )
                {
                    /* 
                     * if ADD op where refferal is added to objectClass of a
                     * non-referral entry then we add a new referral to lut
                     */
                    case ( DirContext.ADD_ATTRIBUTE  ):
                        if ( modsOcHasReferral && !isTargetReferral )
                        {
                            lut.referralAdded( name );
                        }
                        break;
                    /* 
                     * if REMOVE op where refferal is removed from objectClass of a
                     * referral entry then we remove the referral from lut
                     */
                    case ( DirContext.REMOVE_ATTRIBUTE  ):
                        if ( modsOcHasReferral && isTargetReferral )
                        {
                            lut.referralDeleted( name );
                        }
                        break;
                    /* 
                     * if REPLACE op on referral has new set of OC values which does 
                     * not contain a referral value then we remove the referral from 
                     * the lut
                     * 
                     * if REPLACE op on non-referral has new set of OC values with 
                     * referral value then we add the new referral to the lut
                     */
                    case ( DirContext.REPLACE_ATTRIBUTE  ):
                        if ( isTargetReferral && !modsOcHasReferral )
                        {
                            lut.referralDeleted( name );
                        }
                        else if ( !isTargetReferral && modsOcHasReferral )
                        {
                            lut.referralAdded( name );
                        }
                        break;
                    default:
                        throw new IllegalStateException( "undefined modification operation" );
                }

                break;
            }
        }
    }


    public void modify( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );
        LdapDN name = opContext.getDn();
        ModificationItemImpl[] mods = ((ModifyOperationContext)opContext).getModItems();

        // handle a normal modify without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            next.modify( opContext );
            checkModify( name, mods );
            return;
        }

        if ( refval.equals( THROW ) )
        {
            LdapDN farthest = lut.getFarthestReferralAncestor( name );
            if ( farthest == null )
            {
                next.modify( opContext );
                checkModify( name, mods );
                return;
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            Attribute refs = referral.get( REF_ATTR );
            doReferralException( farthest, new LdapDN( name.getUpName() ), refs );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }


    static ExprNode getReferralFilter()
    {
        return new SimpleNode( SchemaConstants.OBJECT_CLASS_AT, REFERRAL_OC, AssertionEnum.EQUALITY );
    }


    static SearchControls getControls()
    {
        SearchControls controls = new SearchControls();
        controls.setReturningObjFlag( false );
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        return controls;
    }


    public void addContextPartition( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
        next.addContextPartition( opContext );

        // add referrals immediately after adding the new partition
        Partition partition = ((AddContextPartitionOperationContext)opContext).getCfg().getContextPartition();
        LdapDN suffix = partition.getSuffix();
        Invocation invocation = InvocationStack.getInstance().peek();
        NamingEnumeration list = invocation.getProxy().search( 
            new SearchOperationContext( suffix, env, getReferralFilter(), getControls() ),
            SEARCH_BYPASS );
        addReferrals( list, suffix );
    }


    public void removeContextPartition( NextInterceptor next, OperationContext opContext ) throws NamingException
    {
        // remove referrals immediately before removing the partition
        Invocation invocation = InvocationStack.getInstance().peek();
        NamingEnumeration list = invocation.getProxy().search( 
            new SearchOperationContext( 
                opContext.getDn(), 
                env, 
                getReferralFilter(), 
                getControls() ),
            SEARCH_BYPASS );
        
        deleteReferrals( list, opContext.getDn() );

        next.removeContextPartition( opContext );
    }


    private void addReferrals( NamingEnumeration referrals, LdapDN base ) throws NamingException
    {
        while ( referrals.hasMore() )
        {   
            SearchResult r = ( SearchResult ) referrals.next();
            LdapDN referral = null;
            LdapDN result = new LdapDN( r.getName() );
            result.normalize( attrRegistry.getNormalizerMapping() );
            
            if ( r.isRelative() )
            {
                referral = ( LdapDN ) base.clone();
                referral.addAll( result );
            }
            else
            {
                referral = result;
            }
            
            // Now, add the referral to the cache
            lut.referralAdded( result );
        }
    }


    private void deleteReferrals( NamingEnumeration referrals, LdapDN base ) throws NamingException
    {
        while ( referrals.hasMore() )
        {
            SearchResult r = ( SearchResult ) referrals.next();
            LdapDN referral = null;
            LdapDN result = new LdapDN( r.getName() );
            result.normalize( attrRegistry.getNormalizerMapping() );

            if ( r.isRelative() )
            {
                referral = ( LdapDN ) base.clone();
                referral.addAll( result );
            }
            else
            {
                referral = result;
            }
            
            // Now, remove the referral from the cache
            lut.referralDeleted( result );
        }
    }


    public NamingEnumeration<SearchResult> search( NextInterceptor next, OperationContext opContext )
        throws NamingException
    {
        Invocation invocation = InvocationStack.getInstance().peek();
        ServerLdapContext caller = ( ServerLdapContext ) invocation.getCaller();
        String refval = ( String ) caller.getEnvironment().get( Context.REFERRAL );

        // handle a normal modify without following referrals
        if ( refval == null || refval.equals( IGNORE ) )
        {
            return next.search( opContext );
        }
        
        LdapDN base = opContext.getDn();
        SearchControls controls = ((SearchOperationContext)opContext).getSearchControls();
        

        /**
         * THROW_FINDING_BASE is a special setting which allows for finding base to 
         * throw exceptions but not when searching.  While search all results are 
         * returned as if they are regular entries.
         */
        if ( refval.equals( THROW_FINDING_BASE ) )
        {
            if ( lut.isReferral( base ) )
            {
                Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( base ), PartitionNexusProxy.LOOKUP_BYPASS );
                Attribute refs = referral.get( REF_ATTR );
                doReferralExceptionOnSearchBase( base, refs, controls.getSearchScope() );
            }

            LdapDN farthest = lut.getFarthestReferralAncestor( base );
            
            if ( farthest == null )
            {
                return next.search( opContext );
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            Attribute refs = referral.get( REF_ATTR );
            doReferralExceptionOnSearchBase( farthest, new LdapDN( base.getUpName() ), refs, controls.getSearchScope() );
            throw new IllegalStateException( "Should never get here: shutting up compiler" );
        }
        
        if ( refval.equals( THROW ) )
        {
            if ( lut.isReferral( base ) )
            {
                Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( base ), PartitionNexusProxy.LOOKUP_BYPASS );
                Attribute refs = referral.get( REF_ATTR );
                doReferralExceptionOnSearchBase( base, refs, controls.getSearchScope() );
            }

            LdapDN farthest = lut.getFarthestReferralAncestor( base );
            
            if ( farthest == null )
            {
                SearchResultFilteringEnumeration srfe = 
                    ( SearchResultFilteringEnumeration ) next.search( opContext );
                return new ReferralHandlingEnumeration( srfe, lut, attrRegistry, nexus, controls.getSearchScope(), true );
            }

            Attributes referral = invocation.getProxy().lookup( new LookupOperationContext( farthest ), PartitionNexusProxy.LOOKUP_BYPASS );
            Attribute refs = referral.get( REF_ATTR );
            doReferralExceptionOnSearchBase( farthest, new LdapDN( base.getUpName() ), refs, controls.getSearchScope() );
            throw new IllegalStateException( "Should never get here: shutting up compiler" );
        }
        else if ( refval.equals( FOLLOW ) )
        {
            throw new NotImplementedException( FOLLOW + " referral handling mode not implemented" );
        }
        else
        {
            throw new LdapNamingException( "Undefined value for " + Context.REFERRAL + " key: " + refval,
                ResultCodeEnum.OTHER );
        }
    }

    class ReferralFilter implements SearchResultFilter//, SearchResultEnumerationAppender 
    {
        public boolean accept( Invocation invocation, SearchResult result, SearchControls controls )
            throws NamingException
        {
            return false;
        }
    }


    public void doReferralExceptionOnSearchBase( LdapDN base, Attribute refs, int scope ) throws NamingException
    {
        // handle referral here
        List<String> list = new ArrayList<String>( refs.size() );
        for ( int ii = 0; ii < refs.size(); ii++ )
        {
            String val = ( String ) refs.get( ii );

            // need to add non-ldap URLs as-is
            if ( !val.startsWith( "ldap" ) )
            {
                list.add( val );
                continue;
            }

            // parse the ref value and normalize the DN according to schema 
            LdapURL ldapUrl = new LdapURL();
            try
            {
                ldapUrl.parse( val.toCharArray() );
            }
            catch ( LdapURLEncodingException e )
            {
                log.error( "Bad URL (" + val + ") for ref in " + base + ".  Reference will be ignored." );
            }

            StringBuffer buf = new StringBuffer();
            buf.append( ldapUrl.getScheme() );
            buf.append( ldapUrl.getHost() );
            if ( ldapUrl.getPort() > 0 )
            {
                buf.append( ":" );
                buf.append( ldapUrl.getPort() );
            }
            buf.append( "/" );
            buf.append( LdapURL.urlEncode( ldapUrl.getDn().getUpName(), false ) );
            buf.append( "??" );

            switch ( scope )
            {
                case ( SearchControls.SUBTREE_SCOPE  ):
                    buf.append( "sub" );
                    break;
                case ( SearchControls.ONELEVEL_SCOPE  ):
                    buf.append( "one" );
                    break;
                case ( SearchControls.OBJECT_SCOPE  ):
                    buf.append( "base" );
                    break;
                default:
                    throw new IllegalStateException( "Unknown recognized search scope: " + scope );
            }

            list.add( buf.toString() );
        }
        LdapReferralException lre = new LdapReferralException( list );
        throw lre;
    }


    public void doReferralExceptionOnSearchBase( LdapDN farthest, LdapDN targetUpdn, Attribute refs, int scope )
        throws NamingException
    {
        // handle referral here
        List<String> list = new ArrayList<String>( refs.size() );
        for ( int ii = 0; ii < refs.size(); ii++ )
        {
            String val = ( String ) refs.get( ii );

            // need to add non-ldap URLs as-is
            if ( !val.startsWith( "ldap" ) )
            {
                list.add( val );
                continue;
            }

            // parse the ref value and normalize the DN according to schema 
            LdapURL ldapUrl = new LdapURL();
            try
            {
                ldapUrl.parse( val.toCharArray() );
            }
            catch ( LdapURLEncodingException e )
            {
                log.error( "Bad URL (" + val + ") for ref in " + farthest + ".  Reference will be ignored." );
            }

            LdapDN urlDn = new LdapDN( ldapUrl.getDn().toNormName() );
            urlDn.normalize( attrRegistry.getNormalizerMapping() );
            int diff = targetUpdn.size() - farthest.size();
            LdapDN extra = new LdapDN();
            for ( int jj = 0; jj < diff; jj++ )
            {
                extra.add( targetUpdn.get( farthest.size() + jj ) );
            }

            urlDn.addAll( extra );
            StringBuffer buf = new StringBuffer();
            buf.append( ldapUrl.getScheme() );
            buf.append( ldapUrl.getHost() );
            if ( ldapUrl.getPort() > 0 )
            {
                buf.append( ":" );
                buf.append( ldapUrl.getPort() );
            }
            buf.append( "/" );
            buf.append( LdapURL.urlEncode( urlDn.getUpName(), false ) );
            buf.append( "??" );

            switch ( scope )
            {
                case ( SearchControls.SUBTREE_SCOPE  ):
                    buf.append( "sub" );
                    break;
                case ( SearchControls.ONELEVEL_SCOPE  ):
                    buf.append( "one" );
                    break;
                case ( SearchControls.OBJECT_SCOPE  ):
                    buf.append( "base" );
                    break;
                default:
                    throw new IllegalStateException( "Unknown recognized search scope: " + scope );
            }
            list.add( buf.toString() );
        }
        LdapReferralException lre = new LdapReferralException( list );
        throw lre;
    }

    /**
     * Check if the given name is a referral or not.
     * 
     * @param name The DN to check
     * @return <code>true</code> if the DN is a referral
     * @throws NamingException I fthe DN is incorrect
     */
    public boolean isReferral( String name ) throws NamingException
    {
        if ( lut.isReferral( name ) )
        {
            return true;
        }

        LdapDN dn = new LdapDN( name );
        dn.normalize( attrRegistry.getNormalizerMapping() );

        return lut.isReferral( dn );
    }

    /**
     * Check if the given name is a referral or not.
     * 
     * @param name The DN to check
     * @return <code>true</code> if the DN is a referral
     * @throws NamingException I fthe DN is incorrect
     */
    public boolean isReferral( LdapDN name ) throws NamingException
    {
  		return lut.isReferral( name.isNormalized() ? name :  LdapDN.normalize( name, attrRegistry.getNormalizerMapping() ) );
    }
}
