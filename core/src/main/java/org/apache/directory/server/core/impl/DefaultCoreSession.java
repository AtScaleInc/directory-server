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
package org.apache.directory.server.core.impl;


import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.ldap.Control;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.OperationManager;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.ServerBinaryValue;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerModification;
import org.apache.directory.server.core.entry.ServerStringValue;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AbstractOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AddRequest;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.CompareRequest;
import org.apache.directory.shared.ldap.message.DeleteRequest;
import org.apache.directory.shared.ldap.message.ModifyDnRequest;
import org.apache.directory.shared.ldap.message.ModifyRequest;
import org.apache.directory.shared.ldap.message.SearchRequest;
import org.apache.directory.shared.ldap.message.UnbindRequest;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;
import org.apache.directory.shared.ldap.util.StringTools;


/**
 * The default CoreSession implementation.
 * 
 * TODO - has not been completed yet
 * TODO - need to supply controls and other parameters to setup opContexts
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DefaultCoreSession implements CoreSession
{
    private final DirectoryService directoryService;
    private final LdapPrincipal authenticatedPrincipal;
    private LdapPrincipal authorizedPrincipal;
    
    
    public DefaultCoreSession( LdapPrincipal principal, DirectoryService directoryService )
    {
        this.directoryService = directoryService;
        this.authenticatedPrincipal = principal;
    }

    
    /**
     * Set the ignoreRefferal flag for the current operationContext.
     *
     * @param opContext The current operationContext
     * @param ignoreReferral The flag 
     */
    private void setReferralHandling( AbstractOperationContext opContext, boolean ignoreReferral )
    {
        if ( ignoreReferral )
        {
            opContext.ignoreReferral();
        }
        else
        {
            opContext.throwReferral();
        }
    }
    
    
    /**
     * {@inheritDoc} 
     */
    public void add( ServerEntry entry ) throws Exception
    {
        add( entry, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void add( ServerEntry entry, boolean ignoreReferral ) throws Exception
    {
        add( entry, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void add( ServerEntry entry, LogChange log ) throws Exception
    {
        AddOperationContext opContext = new AddOperationContext( this, entry );

        opContext.setLogChange( log );
        
        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.add( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void add( ServerEntry entry, boolean ignoreReferral, LogChange log ) throws Exception
    {
        AddOperationContext opContext = new AddOperationContext( this, entry );

        opContext.setLogChange( log );
        setReferralHandling( opContext, ignoreReferral );
        
        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.add( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void add( AddRequest addRequest ) throws Exception
    {
        add( addRequest, LogChange.TRUE );
    }

    
    /**
     * {@inheritDoc} 
     */
    public void add( AddRequest addRequest, LogChange log ) throws Exception
    {
        AddOperationContext opContext = new AddOperationContext( this, addRequest );

        opContext.setLogChange( log );
        
        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.add( opContext );
        addRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }

    
    private Value<?> convertToValue( String oid, Object value ) throws NamingException
    {
        Value<?> val = null;
        
        AttributeType attributeType = directoryService.getRegistries().getAttributeTypeRegistry().lookup( oid );
        
        // make sure we add the request controls to operation
        if ( attributeType.getSyntax().isHumanReadable() )
        {
            if ( value instanceof String )
            {
                val = new ServerStringValue( attributeType, (String)value );
            }
            else if ( value instanceof byte[] )
            {
                val = new ServerStringValue( attributeType, StringTools.utf8ToString( (byte[])value ) );
            }
            else
            {
                throw new NamingException( "Bad value for the OID " + oid );
            }
        }
        else
        {
            if ( value instanceof String )
            {
                val = new ServerBinaryValue( attributeType, StringTools.getBytesUtf8( (String)value ) );
            }
            else if ( value instanceof byte[] )
            {
                val = new ServerBinaryValue( attributeType, (byte[])value );
            }
            else
            {
                throw new NamingException( "Bad value for the OID " + oid );
            }
        }
        
        return val;
    }

    /**
     * {@inheritDoc}
     */
    public boolean compare( LdapDN dn, String oid, Object value ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        
        return operationManager.compare( 
            new CompareOperationContext( this, dn, oid, 
                convertToValue( oid, value ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean compare( LdapDN dn, String oid, Object value, boolean ignoreReferral ) throws Exception
    {
        CompareOperationContext opContext =  
                new CompareOperationContext( this, dn, oid, 
                    convertToValue( oid, value ) );
        
        setReferralHandling( opContext, ignoreReferral );
        
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.compare( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void delete( LdapDN dn ) throws Exception
    {
        delete( dn, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void delete( LdapDN dn, LogChange log ) throws Exception
    {
        DeleteOperationContext opContext = new DeleteOperationContext( this, dn );

        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.delete( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void delete( LdapDN dn, boolean ignoreReferral  ) throws Exception
    {
        delete( dn, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void delete( LdapDN dn, boolean ignoreReferral, LogChange log ) throws Exception
    {
        DeleteOperationContext opContext = new DeleteOperationContext( this, dn );
        
        opContext.setLogChange( log );
        setReferralHandling( opContext, ignoreReferral );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.delete( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getAuthenticatedPrincipal()
     */
    public LdapPrincipal getAuthenticatedPrincipal()
    {
        return authenticatedPrincipal;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getAuthenticationLevel()
     */
    public AuthenticationLevel getAuthenticationLevel()
    {
        return getEffectivePrincipal().getAuthenticationLevel();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getClientAddress()
     */
    public SocketAddress getClientAddress()
    {
        // TODO Auto-generated method stub
        return null;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getControls()
     */
    public Set<Control> getControls()
    {
        // TODO Auto-generated method stub
        return null;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getDirectoryService()
     */
    public DirectoryService getDirectoryService()
    {
        return directoryService;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getEffectivePrincipal()
     */
    public LdapPrincipal getEffectivePrincipal()
    {
        if ( authorizedPrincipal == null )
        {
            return authenticatedPrincipal;
        }
        
        return authorizedPrincipal;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getOutstandingOperations()
     */
    public Set<OperationContext> getOutstandingOperations()
    {
        // TODO Auto-generated method stub
        return null;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#getServiceAddress()
     */
    public SocketAddress getServiceAddress()
    {
        // TODO Auto-generated method stub
        return null;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#isConfidential()
     */
    public boolean isConfidential()
    {
        // TODO Auto-generated method stub
        return false;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#isVirtual()
     */
    public boolean isVirtual()
    {
        // TODO Auto-generated method stub
        return true;
    }
    
    
    /**
     * TODO - perhaps we should just use a flag that is calculated on creation
     * of this session
     *  
     * @see org.apache.directory.server.core.CoreSession#isAdministrator()
     */
    public boolean isAdministrator()
    {
        String normName = getEffectivePrincipal().getJndiName().toNormName(); 
        return normName.equals( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
    }


    /**
     * TODO - this method impl does not check to see if the principal is in 
     * the administrators group - it only returns true of the principal is
     * the actual admin user.  need to make it check groups.
     * 
     * TODO - perhaps we should just use a flag that is calculated on creation
     * of this session
     *  
     * @see org.apache.directory.server.core.CoreSession#isAnAdministrator()
     */
    public boolean isAnAdministrator()
    {
        if ( isAdministrator() )
        {
            return true;
        }
        
        // TODO fix this so it checks groups
        return false;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#list(org.apache.directory.shared.ldap.name.LdapDN, org.apache.directory.shared.ldap.message.AliasDerefMode, java.util.Set)
     */
    public EntryFilteringCursor list( LdapDN dn, AliasDerefMode aliasDerefMode,
        Set<AttributeTypeOptions> returningAttributes ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.list( 
            new ListOperationContext( this, dn, aliasDerefMode, returningAttributes ) );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#list(org.apache.directory.shared.ldap.name.LdapDN, org.apache.directory.shared.ldap.message.AliasDerefMode, java.util.Set, int, int)
     */
    public EntryFilteringCursor list( LdapDN dn, AliasDerefMode aliasDerefMode,
        Set<AttributeTypeOptions> returningAttributes, int sizeLimit, int timeLimit ) throws Exception
    {
        ListOperationContext opContext = new ListOperationContext( this, dn, aliasDerefMode, returningAttributes );
        opContext.setSizeLimit( sizeLimit );
        opContext.setTimeLimit( timeLimit );
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.list( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public ClonedServerEntry lookup( LdapDN dn ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.lookup( new LookupOperationContext( this, dn ) );
    }


    /**
     * {@inheritDoc}
     */
    public ClonedServerEntry lookup( LdapDN dn, String[] attrId ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.lookup( 
            new LookupOperationContext( this, dn, attrId ) );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( LdapDN dn, List<Modification> mods ) throws Exception
    {
        modify( dn, mods, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( LdapDN dn, List<Modification> mods, LogChange log ) throws Exception
    {
        if ( mods == null )
        {
            return;
        }
        
        List<Modification> serverModifications = new ArrayList<Modification>( mods.size() );
        
        for ( Modification mod:mods )
        {
            serverModifications.add( new ServerModification( directoryService.getRegistries(), mod ) );
        }
        
        ModifyOperationContext opContext = new ModifyOperationContext( this, dn, serverModifications );

        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.modify( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( LdapDN dn, List<Modification> mods, boolean ignoreReferral ) throws Exception
    {
        modify( dn, mods, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( LdapDN dn, List<Modification> mods, boolean ignoreReferral, LogChange log ) throws Exception
    {
        if ( mods == null )
        {
            return;
        }
        
        List<Modification> serverModifications = new ArrayList<Modification>( mods.size() );
        
        for ( Modification mod:mods )
        {
            serverModifications.add( new ServerModification( directoryService.getRegistries(), mod ) );
        }

        ModifyOperationContext opContext = new ModifyOperationContext( this, dn, serverModifications );
        
        setReferralHandling( opContext, ignoreReferral );
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.modify( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( LdapDN dn, LdapDN newParent ) throws Exception
    {
        move( dn, newParent, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( LdapDN dn, LdapDN newParent, LogChange log ) throws Exception
    {
        MoveOperationContext opContext = new MoveOperationContext( this, dn, newParent );
        
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.move( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( LdapDN dn, LdapDN newParent, boolean ignoreReferral ) throws Exception
    {
        move( dn, newParent, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( LdapDN dn, LdapDN newParent, boolean ignoreReferral, LogChange log ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        MoveOperationContext opContext = new MoveOperationContext( this, dn, newParent );
        
        setReferralHandling( opContext, ignoreReferral );
        opContext.setLogChange( log );

        operationManager.move( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( LdapDN dn, LdapDN newParent, Rdn newRdn, boolean deleteOldRdn ) throws Exception
    {
        moveAndRename( dn, newParent, newRdn, deleteOldRdn, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( LdapDN dn, LdapDN newParent, Rdn newRdn, boolean deleteOldRdn, LogChange log ) throws Exception
    {
        MoveAndRenameOperationContext opContext = 
            new MoveAndRenameOperationContext( this, dn, newParent, newRdn, deleteOldRdn );
        
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.moveAndRename( opContext );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( LdapDN dn, LdapDN newParent, Rdn newRdn, boolean deleteOldRdn, boolean ignoreReferral ) throws Exception
    {
        moveAndRename( dn, newParent, newRdn, deleteOldRdn, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( LdapDN dn, LdapDN newParent, Rdn newRdn, boolean deleteOldRdn, boolean ignoreReferral, LogChange log ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        MoveAndRenameOperationContext opContext = new MoveAndRenameOperationContext( this, dn, newParent, newRdn, deleteOldRdn );
        
        opContext.setLogChange( log );
        setReferralHandling( opContext, ignoreReferral );

        operationManager.moveAndRename( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( LdapDN dn, Rdn newRdn, boolean deleteOldRdn ) throws Exception
    {
        rename( dn, newRdn, deleteOldRdn, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( LdapDN dn, Rdn newRdn, boolean deleteOldRdn, LogChange log ) throws Exception
    {
        RenameOperationContext opContext = new RenameOperationContext( this, dn, newRdn, deleteOldRdn );
        
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.rename( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( LdapDN dn, Rdn newRdn, boolean deleteOldRdn, boolean ignoreReferral ) throws Exception
    {
        rename( dn, newRdn, deleteOldRdn, ignoreReferral, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( LdapDN dn, Rdn newRdn, boolean deleteOldRdn, boolean ignoreReferral, LogChange log ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        RenameOperationContext opContext = new RenameOperationContext( this, dn, newRdn, deleteOldRdn );
        
        opContext.setLogChange( log );
        setReferralHandling( opContext, ignoreReferral );

        operationManager.rename( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public EntryFilteringCursor search( LdapDN dn, String filter ) throws Exception
    {
        return search( dn, filter, true );
    }


    /**
     * {@inheritDoc}
     */
    public EntryFilteringCursor search( LdapDN dn, String filter, boolean ignoreReferrals ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        ExprNode filterNode = FilterParser.parse( filter ); 
        
        SearchOperationContext opContext = new SearchOperationContext( 
            this, 
            dn, 
            SearchScope.OBJECT, 
            filterNode, 
            AliasDerefMode.DEREF_ALWAYS, 
            null );
        
        setReferralHandling( opContext, ignoreReferrals );

        return operationManager.search( opContext );
    }
    

    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#search(org.apache.directory.shared.ldap.name.LdapDN, org.apache.directory.shared.ldap.filter.SearchScope, org.apache.directory.shared.ldap.filter.ExprNode, org.apache.directory.shared.ldap.message.AliasDerefMode, java.util.Set)
     */
    public EntryFilteringCursor search( LdapDN dn, SearchScope scope, ExprNode filter, AliasDerefMode aliasDerefMode,
        Set<AttributeTypeOptions> returningAttributes ) throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.search( new SearchOperationContext( this, dn, scope, filter, 
            aliasDerefMode, returningAttributes ) );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.CoreSession#search(org.apache.directory.shared.ldap.name.LdapDN, org.apache.directory.shared.ldap.filter.SearchScope, org.apache.directory.shared.ldap.filter.ExprNode, org.apache.directory.shared.ldap.message.AliasDerefMode, java.util.Set, int, int)
     */
    public EntryFilteringCursor search( LdapDN dn, SearchScope scope, ExprNode filter, AliasDerefMode aliasDerefMode,
        Set<AttributeTypeOptions> returningAttributes, int sizeLimit, int timeLimit ) throws Exception
    {
        SearchOperationContext opContext = new SearchOperationContext( this, dn, scope, filter, 
            aliasDerefMode, returningAttributes );
        opContext.setSizeLimit( sizeLimit );
        opContext.setTimeLimit( timeLimit );
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.search( opContext );
    }


    public boolean isAnonymous()
    {
        return getEffectivePrincipal().getJndiName().isEmpty();
    }


    /**
     * {@inheritDoc}
     */
    public boolean compare( CompareRequest compareRequest ) throws Exception
    {
        CompareOperationContext opContext = new CompareOperationContext( this, compareRequest );
        OperationManager operationManager = directoryService.getOperationManager();
        boolean result = operationManager.compare( opContext );
        compareRequest.getResultResponse().addAll( opContext.getResponseControls() );
        return result;
    }


    /**
     * {@inheritDoc}
     */
    public void delete( DeleteRequest deleteRequest ) throws Exception
    {
        delete( deleteRequest, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void delete( DeleteRequest deleteRequest, LogChange log ) throws Exception
    {
        DeleteOperationContext opContext = new DeleteOperationContext( this, deleteRequest );
        
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.delete( opContext );
        deleteRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }


    public boolean exists( LdapDN dn ) throws Exception
    {
        EntryOperationContext opContext = new EntryOperationContext( this, dn );
        OperationManager operationManager = directoryService.getOperationManager();
        return operationManager.hasEntry( opContext );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( ModifyRequest modifyRequest ) throws Exception
    {
        modify( modifyRequest, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void modify( ModifyRequest modifyRequest, LogChange log ) throws Exception
    {
        ModifyOperationContext opContext = new ModifyOperationContext( this, modifyRequest );

        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.modify( opContext );
        modifyRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( ModifyDnRequest modifyDnRequest ) throws Exception
    {
        move( modifyDnRequest, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void move( ModifyDnRequest modifyDnRequest, LogChange log ) throws Exception
    {
        MoveOperationContext opContext = new MoveOperationContext( this, modifyDnRequest );
        
        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.move( opContext );
        modifyDnRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( ModifyDnRequest modifyDnRequest ) throws Exception
    {
        moveAndRename( modifyDnRequest, LogChange.TRUE );
    }


    /**
     * {@inheritDoc} 
     */
    public void moveAndRename( ModifyDnRequest modifyDnRequest, LogChange log ) throws Exception
    {
        MoveAndRenameOperationContext opContext = new MoveAndRenameOperationContext( this, modifyDnRequest );

        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.moveAndRename( opContext );
        modifyDnRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( ModifyDnRequest modifyDnRequest ) throws Exception
    {
        rename( modifyDnRequest, LogChange.TRUE );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( ModifyDnRequest modifyDnRequest, LogChange log ) throws Exception
    {
        RenameOperationContext opContext = new RenameOperationContext( this, modifyDnRequest );

        opContext.setLogChange( log );

        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.rename( opContext );
        modifyDnRequest.getResultResponse().addAll( opContext.getResponseControls() );
    }


    public EntryFilteringCursor search( SearchRequest searchRequest ) throws Exception
    {
        SearchOperationContext opContext = new SearchOperationContext( this, searchRequest );
        OperationManager operationManager = directoryService.getOperationManager();
        EntryFilteringCursor cursor = operationManager.search( opContext );
        searchRequest.getResultResponse().addAll( opContext.getResponseControls() );
        return cursor;
    }


    public void unbind() throws Exception
    {
        OperationManager operationManager = directoryService.getOperationManager();
        operationManager.unbind( new UnbindOperationContext( this ) );
    }


    public void unbind( UnbindRequest unbindRequest )
    {
        // TODO Auto-generated method stub
        
    }
}
