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


import javax.naming.directory.SearchControls;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.OidNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * A cache for tracking static group membership.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class GroupCache
{
    /** the logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( GroupCache.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** String key for the DN of a group to a Set (HashSet) for the Strings of member DNs */
    private final Map<String, Set<String>> groups = new HashMap<String, Set<String>>();

    /** a handle on the partition nexus */
    private final PartitionNexus nexus;

    /** A storage for the member attributeType */
    private AttributeType memberAT;

    /** A storage for the uniqueMember attributeType */
    private AttributeType uniqueMemberAT;

    /**
     * The OIDs normalizer map
     */
    private Map<String, OidNormalizer> normalizerMap;

    /** the normalized dn of the administrators group */
    private LdapDN administratorsGroupDn;

    private static final Set<LdapDN> EMPTY_GROUPS = new HashSet<LdapDN>();


    /**
     * Creates a static group cache.
     *
     * @param directoryService the directory service core
     * @throws NamingException if there are failures on initialization 
     */
    public GroupCache( DirectoryService directoryService ) throws NamingException
    {
        normalizerMap = directoryService.getRegistries().getAttributeTypeRegistry().getNormalizerMapping();
        nexus = directoryService.getPartitionNexus();
        AttributeTypeRegistry attributeTypeRegistry = directoryService.getRegistries().getAttributeTypeRegistry();

        memberAT = attributeTypeRegistry.lookup( SchemaConstants.MEMBER_AT_OID );
        uniqueMemberAT = attributeTypeRegistry.lookup( SchemaConstants.UNIQUE_MEMBER_AT_OID );

        // stuff for dealing with the admin group
        administratorsGroupDn = parseNormalized( ServerDNConstants.ADMINISTRATORS_GROUP_DN );

        initialize( directoryService.getRegistries() );
    }


    private LdapDN parseNormalized( String name ) throws NamingException
    {
        LdapDN dn = new LdapDN( name );
        dn.normalize( normalizerMap );
        return dn;
    }


    private void initialize( Registries registries ) throws NamingException
    {
        // search all naming contexts for static groups and generate
        // normalized sets of members to cache within the map

        BranchNode filter = new OrNode();
        filter.addNode( new EqualityNode( SchemaConstants.OBJECT_CLASS_AT, new ClientStringValue(
            SchemaConstants.GROUP_OF_NAMES_OC ) ) );
        filter.addNode( new EqualityNode( SchemaConstants.OBJECT_CLASS_AT, new ClientStringValue(
            SchemaConstants.GROUP_OF_UNIQUE_NAMES_OC ) ) );

        Iterator<String> suffixes = nexus.listSuffixes( null );

        while ( suffixes.hasNext() )
        {
            String suffix = suffixes.next();
            LdapDN baseDn = new LdapDN( suffix );
            SearchControls ctls = new SearchControls();
            ctls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            NamingEnumeration<ServerSearchResult> results = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.DEREF_ALWAYS, filter, ctls ) );

            while ( results.hasMore() )
            {
                ServerSearchResult result = results.next();
                LdapDN groupDn = result.getDn().normalize( normalizerMap );
                EntryAttribute members = getMemberAttribute( result.getServerEntry() );

                if ( members != null )
                {
                    Set<String> memberSet = new HashSet<String>( members.size() );
                    addMembers( memberSet, members );
                    groups.put( groupDn.getNormName(), memberSet );
                }
                else
                {
                    LOG.warn( "Found group '{}' without any member or uniqueMember attributes", groupDn.getUpName() );
                }
            }

            results.close();
        }

        if ( IS_DEBUG )
        {
            LOG.debug( "group cache contents on startup:\n {}", groups );
        }
    }


    /**
     * Gets the member attribute regardless of whether groupOfNames or
     * groupOfUniqueNames is used.
     *
     * @param entry the entry inspected for member attributes
     * @return the member attribute
     */
    private EntryAttribute getMemberAttribute( ServerEntry entry ) throws NamingException
    {
        EntryAttribute oc = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( oc == null )
        {
            EntryAttribute member = entry.get( memberAT );

            if ( member != null )
            {
                return member;
            }

            EntryAttribute uniqueMember = entry.get( uniqueMemberAT );

            if ( uniqueMember != null )
            {
                return uniqueMember;
            }

            return null;
        }

        if ( oc.contains( SchemaConstants.GROUP_OF_NAMES_OC ) || oc.contains( SchemaConstants.GROUP_OF_NAMES_OC_OID ) )
        {
            return entry.get( memberAT );
        }

        if ( oc.contains( SchemaConstants.GROUP_OF_UNIQUE_NAMES_OC )
            || oc.contains( SchemaConstants.GROUP_OF_UNIQUE_NAMES_OC_OID ) )
        {
            return entry.get( uniqueMemberAT );
        }

        return null;
    }


    /**
     * Adds normalized member DNs to the set of normalized member names.
     *
     * @param memberSet the set of member Dns (Strings)
     * @param members the member attribute values being added
     * @throws NamingException if there are problems accessing the attr values
     */
    private void addMembers( Set<String> memberSet, EntryAttribute members ) throws NamingException
    {
        for ( Value<?> value : members )
        {

            // get and normalize the DN of the member
            String memberDn = ( String ) value.get();

            try
            {
                memberDn = parseNormalized( memberDn ).toString();
            }
            catch ( NamingException e )
            {
                LOG.warn( "Malformed member DN in groupOf[Unique]Names entry.  Member not added to GroupCache.", e );
            }

            memberSet.add( memberDn );
        }
    }


    /**
     * Removes a set of member names from an existing set.
     *
     * @param memberSet the set of normalized member DNs
     * @param members the set of member values
     * @throws NamingException if there are problems accessing the attr values
     */
    private void removeMembers( Set<String> memberSet, EntryAttribute members ) throws NamingException
    {
        for ( Value<?> value : members )
        {
            // get and normalize the DN of the member
            String memberDn = ( String ) value.get();

            try
            {
                memberDn = parseNormalized( memberDn ).toString();
            }
            catch ( NamingException e )
            {
                LOG.warn( "Malformed member DN in groupOf[Unique]Names entry.  Member not removed from GroupCache.", e );
            }

            memberSet.remove( memberDn );
        }
    }


    /**
     * Adds a groups members to the cache.  Called by interceptor to account for new
     * group additions.
     *
     * @param name the user provided name for the group entry
     * @param entry the group entry's attributes
     * @throws NamingException if there are problems accessing the attr values
     */
    public void groupAdded( LdapDN name, ServerEntry entry ) throws NamingException
    {
        EntryAttribute members = getMemberAttribute( entry );

        if ( members == null )
        {
            return;
        }

        Set<String> memberSet = new HashSet<String>( members.size() );
        addMembers( memberSet, members );
        groups.put( name.getNormName(), memberSet );

        if ( IS_DEBUG )
        {
            LOG.debug( "group cache contents after adding '{}' :\n {}", name.getUpName(), groups );
        }
    }


    /**
     * Deletes a group's members from the cache.  Called by interceptor to account for
     * the deletion of groups.
     *
     * @param name the normalized DN of the group entry
     * @param entry the attributes of entry being deleted
     */
    public void groupDeleted( LdapDN name, ServerEntry entry ) throws NamingException
    {
        EntryAttribute members = getMemberAttribute( entry );

        if ( members == null )
        {
            return;
        }

        groups.remove( name.getNormName() );

        if ( IS_DEBUG )
        {
            LOG.debug( "group cache contents after deleting '{}' :\n {}", name.getUpName(), groups );
        }
    }


    /**
     * Utility method to modify a set of member names based on a modify operation
     * that changes the members of a group.
     *
     * @param memberSet the set of members to be altered
     * @param modOp the type of modify operation being performed
     * @param members the members being added, removed or replaced
     * @throws NamingException if there are problems accessing attribute values
     */
    private void modify( Set<String> memberSet, ModificationOperation modOp, EntryAttribute members )
        throws NamingException
    {

        switch ( modOp )
        {
            case ADD_ATTRIBUTE:
                addMembers( memberSet, members );
                break;

            case REPLACE_ATTRIBUTE:
                if ( members.size() > 0 )
                {
                    memberSet.clear();
                    addMembers( memberSet, members );
                }

                break;

            case REMOVE_ATTRIBUTE:
                removeMembers( memberSet, members );
                break;

            default:
                throw new InternalError( "Undefined modify operation value of " + modOp );
        }
    }


    /**
     * Modifies the cache to reflect changes via modify operations to the group entries.
     * Called by the interceptor to account for modify ops on groups.
     *
     * @param name the normalized name of the group entry modified
     * @param mods the modification operations being performed
     * @param entry the group entry being modified
     * @throws NamingException if there are problems accessing attribute  values
     */
    public void groupModified( LdapDN name, List<Modification> mods, ServerEntry entry, Registries registries )
        throws NamingException
    {
        EntryAttribute members = null;
        String memberAttrId = null;
        EntryAttribute oc = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( oc.contains( SchemaConstants.GROUP_OF_NAMES_OC ) )
        {
            members = entry.get( memberAT );
            memberAttrId = SchemaConstants.MEMBER_AT;
        }

        if ( oc.contains( SchemaConstants.GROUP_OF_UNIQUE_NAMES_OC ) )
        {
            members = entry.get( uniqueMemberAT );
            memberAttrId = SchemaConstants.UNIQUE_MEMBER_AT;
        }

        if ( members == null )
        {
            return;
        }

        for ( Modification modification : mods )
        {
            if ( memberAttrId.equalsIgnoreCase( modification.getAttribute().getId() ) )
            {
                Set<String> memberSet = groups.get( name.getNormName() );

                if ( memberSet != null )
                {
                    modify( memberSet, modification.getOperation(), ( ServerAttribute ) modification.getAttribute() );
                }

                break;
            }
        }

        if ( IS_DEBUG )
        {
            LOG.debug( "group cache contents after modifying '{}' :\n {}", name.getUpName(), groups );
        }
    }


    /**
     * Modifies the cache to reflect changes via modify operations to the group entries.
     * Called by the interceptor to account for modify ops on groups.
     *
     * @param name the normalized name of the group entry modified
     * @param modOp the modify operation being performed
     * @param mods the modifications being performed
     * @throws NamingException if there are problems accessing attribute  values
     */
    public void groupModified( LdapDN name, ModificationOperation modOp, ServerEntry mods ) throws NamingException
    {
        EntryAttribute members = getMemberAttribute( mods );

        if ( members == null )
        {
            return;
        }

        Set<String> memberSet = groups.get( name.getNormName() );

        if ( memberSet != null )
        {
            modify( memberSet, modOp, members );
        }

        if ( IS_DEBUG )
        {
            LOG.debug( "group cache contents after modifying '{}' :\n {}", name.getUpName(), groups );
        }
    }


    /**
     * An optimization.  By having this method here we can directly access the group
     * membership information and lookup to see if the principalDn is contained within.
     * 
     * @param principalDn the normalized DN of the user to check if they are an admin
     * @return true if the principal is an admin or the admin
     */
    public final boolean isPrincipalAnAdministrator( LdapDN principalDn )
    {
        if ( principalDn.getNormName().equals( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED ) )
        {
            return true;
        }

        Set<String> members = groups.get( administratorsGroupDn.getNormName() );

        if ( members == null )
        {
            LOG.warn( "What do you mean there is no administrators group? This is bad news." );
            return false;
        }

        return members.contains( principalDn.toNormName() );
    }


    /**
     * Gets the set of groups a user is a member of.  The groups are returned
     * as normalized Name objects within the set.
     *
     * @param member the member (user) to get the groups for
     * @return a Set of Name objects representing the groups
     * @throws NamingException if there are problems accessing attribute  values
     */
    public Set<LdapDN> getGroups( String member ) throws NamingException
    {
        LdapDN normMember;

        try
        {
            normMember = parseNormalized( member );
        }
        catch ( NamingException e )
        {
            LOG
                .warn(
                    "Malformed member DN.  Could not find groups for member '{}' in GroupCache. Returning empty set for groups!",
                    member, e );
            return EMPTY_GROUPS;
        }

        Set<LdapDN> memberGroups = null;

        for ( String group : groups.keySet() )
        {
            Set<String> members = groups.get( group );

            if ( members == null )
            {
                continue;
            }

            if ( members.contains( normMember.getNormName() ) )
            {
                if ( memberGroups == null )
                {
                    memberGroups = new HashSet<LdapDN>();
                }

                memberGroups.add( parseNormalized( group ) );
            }
        }

        if ( memberGroups == null )
        {
            return EMPTY_GROUPS;
        }

        return memberGroups;
    }


    public boolean groupRenamed( LdapDN oldName, LdapDN newName )
    {
        Set<String> members = groups.remove( oldName.getNormName() );

        if ( members != null )
        {
            groups.put( newName.getNormName(), members );

            if ( IS_DEBUG )
            {
                LOG.debug( "group cache contents after renaming '{}' :\n{}", oldName.getUpName(), groups );
            }

            return true;
        }

        return false;
    }
}
