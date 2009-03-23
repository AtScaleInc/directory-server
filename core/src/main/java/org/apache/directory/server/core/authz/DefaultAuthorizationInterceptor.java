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
package org.apache.directory.server.core.authz;


import javax.naming.NoPermissionException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.filtering.EntryFilter;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.impl.DefaultCoreSession;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An {@link Interceptor} that controls access to {@link PartitionNexus}.
 * If a user tries to perform any operations that requires
 * permission he or she doesn't have, {@link NoPermissionException} will be
 * thrown and therefore the current invocation chain will terminate.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DefaultAuthorizationInterceptor extends BaseInterceptor
{
    /** the logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultAuthorizationInterceptor.class );

    /**
     * the base distinguished {@link Name} for all users
     */
    private static LdapDN USER_BASE_DN;

    /**
     * the base distinguished {@link Name} for all groups
     */
    private static LdapDN GROUP_BASE_DN;

    /**
     * the distinguished {@link Name} for the administrator group
     */
    private static LdapDN ADMIN_GROUP_DN;

    /**
     * the name parser used by this service
     */
    private boolean enabled = true;
    
    private Set<String> administrators = new HashSet<String>(2);
    
    /** The normalizer mapping containing a relation between an OID and a normalizer */
    private Map<String, OidNormalizer> normalizerMapping;
    
    private PartitionNexus nexus;

    /** A starage for the uniqueMember attributeType */
    private AttributeType uniqueMemberAT;


    /**
     * Creates a new instance.
     */
    public DefaultAuthorizationInterceptor()
    {
    }


    public void init( DirectoryService directoryService ) throws Exception
    {
        nexus = directoryService.getPartitionNexus();
        normalizerMapping = directoryService.getRegistries().getAttributeTypeRegistry().getNormalizerMapping();

        // disable this static module if basic access control mechanisms are enabled
        enabled = ! directoryService.isAccessControlEnabled();
        
        USER_BASE_DN = PartitionNexus.getUsersBaseName();
        USER_BASE_DN.normalize( normalizerMapping );
        
        GROUP_BASE_DN = PartitionNexus.getGroupsBaseName();
        GROUP_BASE_DN.normalize( normalizerMapping );
     
        ADMIN_GROUP_DN = new LdapDN( ServerDNConstants.ADMINISTRATORS_GROUP_DN );
        ADMIN_GROUP_DN.normalize( normalizerMapping );

        AttributeTypeRegistry attrRegistry = directoryService.getRegistries().getAttributeTypeRegistry();
        
        uniqueMemberAT = attrRegistry.lookup( SchemaConstants.UNIQUE_MEMBER_AT_OID );
        
        loadAdministrators( directoryService );
    }
    
    
    private void loadAdministrators( DirectoryService directoryService ) throws Exception
    {
        // read in the administrators and cache their normalized names
        Set<String> newAdministrators = new HashSet<String>( 2 );
        LdapDN adminDn = new LdapDN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
        adminDn.normalize( directoryService.getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
        CoreSession adminSession = new DefaultCoreSession( 
            new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );

        ServerEntry adminGroup = nexus.lookup( new LookupOperationContext( adminSession, ADMIN_GROUP_DN ) );
        
        if ( adminGroup == null )
        {
            return;
        }
        
        EntryAttribute uniqueMember = adminGroup.get( uniqueMemberAT );
        
        for ( Value<?> value:uniqueMember )
        {
            LdapDN memberDn = new LdapDN( ( String ) value.get() );
            memberDn.normalize( normalizerMapping );
            newAdministrators.add( memberDn.getNormName() );
        }
        
        administrators = newAdministrators;
    }

    
    // Note:
    //    Lookup, search and list operations need to be handled using a filter
    // and so we need access to the filter service.

    public void delete( NextInterceptor nextInterceptor, DeleteOperationContext opContext ) throws Exception
    {
        LdapDN name = opContext.getDn();
        
        if ( !enabled )
        {
            nextInterceptor.delete( opContext );
            return;
        }

        LdapDN principalDn = getPrincipal().getJndiName();

        if ( name.isEmpty() )
        {
            String msg = "The rootDSE cannot be deleted!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( name.getNormName().equals( ADMIN_GROUP_DN.getNormName() ) )
        {
            String msg = "The Administrators group cannot be deleted!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( isTheAdministrator( name ) )
        {
            String msg = "User " + principalDn.getUpName();
            msg += " does not have permission to delete the admin account.";
            msg += " No one not even the admin can delete this account!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( name.size() > 2 )
        {
            if ( !isAnAdministrator( principalDn ) )
            {
                if ( name.startsWith( USER_BASE_DN ) )
                {
                    String msg = "User " + principalDn.getUpName();
                    msg += " does not have permission to delete the user account: ";
                    msg += name.getUpName() + ". Only the admin can delete user accounts.";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }
        
                if ( name.startsWith( GROUP_BASE_DN ) )
                {
                    String msg = "User " + principalDn.getUpName();
                    msg += " does not have permission to delete the group entry: ";
                    msg += name.getUpName() + ". Only the admin can delete groups.";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }
            }
        }

        nextInterceptor.delete( opContext );
    }

    
    private boolean isTheAdministrator( LdapDN normalizedDn )
    {
        return normalizedDn.getNormName().equals( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
    }
    
    
    private boolean isAnAdministrator( LdapDN normalizedDn )
    {
        return isTheAdministrator( normalizedDn ) || administrators.contains( normalizedDn.getNormName() );

    }
    

    // ------------------------------------------------------------------------
    // Entry Modification Operations
    // ------------------------------------------------------------------------

    /**
     * This policy needs to be really tight too because some attributes may take
     * part in giving the user permissions to protected resources.  We do not want
     * users to self access these resources.  As far as we're concerned no one but
     * the admin needs access.
     */
    public void modify( NextInterceptor nextInterceptor, ModifyOperationContext opContext )
        throws Exception
    {
        if ( enabled )
        {
            LdapDN dn = opContext.getDn();
            
            protectModifyAlterations( dn );
            nextInterceptor.modify( opContext );

            // update administrators if we change administrators group
            if ( dn.getNormName().equals( ADMIN_GROUP_DN.getNormName() ) )
            {
                loadAdministrators( opContext.getSession().getDirectoryService() );
            }
        }
        else
        {
            nextInterceptor.modify( opContext );
        }
    }


    private void protectModifyAlterations( LdapDN dn ) throws Exception
    {
        LdapDN principalDn = getPrincipal().getJndiName();

        if ( dn.isEmpty() )
        {
            String msg = "The rootDSE cannot be modified!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( ! isAnAdministrator( principalDn ) )
        {
            // allow self modifications 
            if ( dn.getNormName().equals( getPrincipal().getJndiName().getNormName() ) )
            {
                return;
            }
            
            if ( dn.getNormName().equals( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED ) )
            {
                String msg = "User " + principalDn.getUpName();
                msg += " does not have permission to modify the account of the";
                msg += " admin user.";
                LOG.error( msg );
                throw new LdapNoPermissionException( msg );
            }

            if ( dn.size() > 2 ) 
                {
                if ( dn.startsWith( USER_BASE_DN ) )
                {
                    String msg = "User " + principalDn.getUpName();
                    msg += " does not have permission to modify the account of the";
                    msg += " user " + dn.getUpName() + ".\nEven the owner of an account cannot";
                    msg += " modify it.\nUser accounts can only be modified by the";
                    msg += " administrator.";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }
    
                if ( dn.startsWith( GROUP_BASE_DN ) )
                {
                    String msg = "User " + principalDn.getUpName();
                    msg += " does not have permission to modify the group entry ";
                    msg += dn.getUpName() + ".\nGroups can only be modified by the admin.";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }
            }
        }
    }
    
    
    // ------------------------------------------------------------------------
    // DN altering operations are a no no for any user entry.  Basically here
    // are the rules of conduct to follow:
    //
    //  o No user should have the ability to move or rename their entry
    //  o Only the administrator can move or rename non-admin user entries
    //  o The administrator entry cannot be moved or renamed by anyone
    // ------------------------------------------------------------------------

    public void rename( NextInterceptor nextInterceptor, RenameOperationContext opContext )
        throws Exception
    {
        if ( enabled )
        {
            protectDnAlterations( opContext.getDn() );
        }
        
        nextInterceptor.rename( opContext );
    }


    public void move( NextInterceptor nextInterceptor, MoveOperationContext opContext ) throws Exception
    {
        if ( enabled )
        {
            protectDnAlterations( opContext.getDn() );
        }
        
        nextInterceptor.move( opContext );
    }


    public void moveAndRename( NextInterceptor nextInterceptor, MoveAndRenameOperationContext opContext ) throws Exception
    {
        if ( enabled )
        {
            protectDnAlterations( opContext.getDn() );
        }
        
        nextInterceptor.moveAndRename( opContext );
    }


    private void protectDnAlterations( LdapDN dn ) throws Exception
    {
        LdapDN principalDn = getPrincipal().getJndiName();

        if ( dn.isEmpty() )
        {
            String msg = "The rootDSE cannot be moved or renamed!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( dn.getNormName().equals( ADMIN_GROUP_DN.getNormName() ) )
        {
            String msg = "The Administrators group cannot be moved or renamed!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }
        
        if ( isTheAdministrator( dn ) )
        {
            String msg = "User '" + principalDn.getUpName();
            msg += "' does not have permission to move or rename the admin";
            msg += " account.  No one not even the admin can move or";
            msg += " rename " + dn.getUpName() + "!";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( dn.size() > 2 && dn.startsWith( USER_BASE_DN ) && !isAnAdministrator( principalDn ) )
        {
            String msg = "User '" + principalDn.getUpName();
            msg += "' does not have permission to move or rename the user";
            msg += " account: " + dn.getUpName() + ". Only the admin can move or";
            msg += " rename user accounts.";
            LOG.error( msg );
            throw new LdapNoPermissionException( msg );
        }

        if ( dn.size() > 2 && dn.startsWith( GROUP_BASE_DN ) && !isAnAdministrator( principalDn ) )
        {
            String msg = "User " + principalDn.getUpName();
            msg += " does not have permission to move or rename the group entry ";
            msg += dn.getUpName() + ".\nGroups can only be moved or renamed by the admin.";
            throw new LdapNoPermissionException( msg );
        }
    }


    public ClonedServerEntry lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws Exception
    {
        ClonedServerEntry serverEntry = nextInterceptor.lookup( opContext );
        
        if ( !enabled || ( serverEntry == null ) )
        {
            return serverEntry;
        }

        protectLookUp( opContext.getSession().getEffectivePrincipal().getJndiName(), opContext.getDn() );
        return serverEntry;
    }


    private void protectLookUp( LdapDN principalDn, LdapDN normalizedDn ) throws Exception
    {
        if ( !isAnAdministrator( principalDn ) )
        {
            if ( normalizedDn.size() > 2 )
            {
                if( normalizedDn.startsWith( USER_BASE_DN ) )
                {
                    // allow for self reads
                    if ( normalizedDn.getNormName().equals( principalDn.getNormName() ) )
                    {
                        return;
                    }
    
                    String msg = "Access to user account '" + normalizedDn.getUpName() + "' not permitted";
                    msg += " for user '" + principalDn.getUpName() + "'.  Only the admin can";
                    msg += " access user account information";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }

                if ( normalizedDn.startsWith( GROUP_BASE_DN ) )
                {
                    // allow for self reads
                    if ( normalizedDn.getNormName().equals( principalDn.getNormName() ) )
                    {
                        return;
                    }
    
                    String msg = "Access to group '" + normalizedDn.getUpName() + "' not permitted";
                    msg += " for user '" + principalDn.getUpName() + "'.  Only the admin can";
                    msg += " access group information";
                    LOG.error( msg );
                    throw new LdapNoPermissionException( msg );
                }
            }

            if ( isTheAdministrator( normalizedDn ) )
            {
                // allow for self reads
                if ( normalizedDn.getNormName().equals( principalDn.getNormName() ) )
                {
                    return;
                }

                String msg = "Access to admin account not permitted for user '";
                msg += principalDn.getUpName() + "'.  Only the admin can";
                msg += " access admin account information";
                LOG.error( msg );
                throw new LdapNoPermissionException( msg );
            }
        }
    }


    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.search( opContext );

        if ( !enabled )
        {
            return cursor;
        }

        cursor.addEntryFilter( new EntryFilter() {
            public boolean accept( SearchingOperationContext operation, ClonedServerEntry result ) throws Exception
            {
                return DefaultAuthorizationInterceptor.this.isSearchable( operation, result );
            }
        } );
        return cursor;
    }


    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.list( opContext );
        
        if ( !enabled )
        {
            return cursor;
        }

        cursor.addEntryFilter( new EntryFilter()
        {
            public boolean accept( SearchingOperationContext operation, ClonedServerEntry entry ) throws Exception
            {
                return DefaultAuthorizationInterceptor.this.isSearchable( operation, entry );
            }
        } );
        return cursor;
    }


    private boolean isSearchable( OperationContext opContext, ClonedServerEntry result ) throws Exception
    {
        LdapDN principalDn = opContext.getSession().getEffectivePrincipal().getJndiName();
        LdapDN dn = result.getDn();
        
        if ( !dn.isNormalized() )
        {
            dn.normalize( normalizerMapping );
        }

        // Admin users gets full access to all entries
        if ( isAnAdministrator( principalDn ) )
        {
            return true;
        }
        
        // Users reading their own entries should be allowed to see all
        boolean isSelfRead = dn.getNormName().equals( principalDn.getNormName() );
        
        if ( isSelfRead )
        {
            return true;
        }
        
        // Block off reads to anything under ou=users and ou=groups if not a self read
        if ( dn.size() > 2 )
        {
            // stuff this if in here instead of up in outer if to prevent 
            // constant needless reexecution for all entries in other depths
            
            if ( dn.getNormName().endsWith( USER_BASE_DN.getNormName() ) 
                || dn.getNormName().endsWith( GROUP_BASE_DN.getNormName() ) )
            {
                return false;
            }
        }
        
        // Non-admin users cannot read the admin entry
        return ! isTheAdministrator( dn );

    }
}
