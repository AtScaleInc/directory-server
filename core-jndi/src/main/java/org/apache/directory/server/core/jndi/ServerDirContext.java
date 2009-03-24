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
package org.apache.directory.server.core.jndi;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.event.DirectoryListener;
import org.apache.directory.server.core.event.NotificationCriteria;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.client.ClientBinaryValue;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.AttributeTypeAndValue;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.StringTools;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.event.EventDirContext;
import javax.naming.event.NamingListener;
import javax.naming.spi.DirStateFactory;
import javax.naming.spi.DirectoryManager;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;


/**
 * The DirContext implementation for the Server Side JNDI LDAP provider.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class ServerDirContext extends ServerContext implements EventDirContext
{
    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Creates a new ServerDirContext by reading the PROVIDER_URL to resolve the
     * distinguished name for this context.
     *
     * @param service the parent service that manages this context
     * @param env the environment used for this context
     * @throws NamingException if something goes wrong
     */
    public ServerDirContext( DirectoryService service, Hashtable<String, Object> env ) throws Exception
    {
        super( service, env );
    }


    /**
     * Creates a new ServerDirContext with a distinguished name which is used to
     * set the PROVIDER_URL to the distinguished name for this context.
     *
     * @param principal the principal which is propagated
     * @param dn the distinguished name of this context
     */
    public ServerDirContext( DirectoryService service, LdapPrincipal principal, Name dn ) throws Exception
    {
        super( service, principal, dn );
    }


    // ------------------------------------------------------------------------
    // DirContext Implementations
    // ------------------------------------------------------------------------

    
    public ServerDirContext( DirectoryService service, CoreSession session, LdapDN bindDn ) throws Exception
    {
        super( service, session, bindDn );
    }


    /**
     * @see javax.naming.directory.DirContext#getAttributes(java.lang.String)
     */
    public Attributes getAttributes( String name ) throws NamingException
    {
        return getAttributes( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.directory.DirContext#getAttributes(javax.naming.Name)
     */
    public Attributes getAttributes( Name name ) throws NamingException
    {
        Attributes attrs = null;
        
        try
        {
            attrs = ServerEntryUtils.toBasicAttributes( doLookupOperation( buildTarget( name ) ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        return attrs;
    }


    /**
     * @see javax.naming.directory.DirContext#getAttributes(java.lang.String,
     *      java.lang.String[])
     */
    public Attributes getAttributes( String name, String[] attrIds ) throws NamingException
    {
        return getAttributes( new LdapDN( name ), attrIds );
    }


    /**
     * @see javax.naming.directory.DirContext#getAttributes(javax.naming.Name,
     *      java.lang.String[])
     */
    public Attributes getAttributes( Name name, String[] attrIds ) throws NamingException
    {
        Attributes attrs = null;
        try
        {
            attrs = ServerEntryUtils.toBasicAttributes( doLookupOperation( buildTarget( name ), attrIds ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        return attrs;
    }


    /**
     * @see javax.naming.directory.DirContext#modifyAttributes(java.lang.String,
     *      int, javax.naming.directory.Attributes)
     */
    public void modifyAttributes( String name, int modOp, Attributes attrs ) throws NamingException
    {
        modifyAttributes( new LdapDN( name ), modOp, AttributeUtils.toCaseInsensitive( attrs ) );
    }


    /**
     * @see javax.naming.directory.DirContext#modifyAttributes(java.lang.String,
     *      int, javax.naming.directory.Attributes)
     */
    public void modifyAttributes( Name name, int modOp, Attributes attrs ) throws NamingException
    {
        List<ModificationItem> modItems = null;

        if ( attrs != null )
        {
            modItems = new ArrayList<ModificationItem>( attrs.size() );
            NamingEnumeration<? extends Attribute> e = ( NamingEnumeration<? extends Attribute> ) attrs.getAll();

            while ( e.hasMore() )
            {
                modItems.add( new ModificationItem( modOp, e.next() ) );
            }
        }

        List<Modification> newMods = ServerEntryUtils.convertToServerModification( 
            modItems, 
            getDirectoryService().getRegistries().getAttributeTypeRegistry() );

        try
        {
            if ( name instanceof LdapDN )
            {
                doModifyOperation( buildTarget( name ), newMods );
            }
            else
            {
                doModifyOperation( buildTarget( new LdapDN( name ) ), newMods );
            }
        }
        catch( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    /**
     * @see javax.naming.directory.DirContext#modifyAttributes(java.lang.String,
     *      javax.naming.directory.ModificationItem[])
     */
    public void modifyAttributes( String name, ModificationItem[] mods ) throws NamingException
    {
        modifyAttributes( new LdapDN( name ), mods );
    }


    /**
     * @see javax.naming.directory.DirContext#modifyAttributes(
     * javax.naming.Name, javax.naming.directory.ModificationItem[])
     */
    public void modifyAttributes( Name name, ModificationItem[] mods ) throws NamingException
    {
        List<Modification> newMods = ServerEntryUtils
            .toServerModification( mods, getDirectoryService().getRegistries().getAttributeTypeRegistry() );
        try
        {
            doModifyOperation( buildTarget( new LdapDN( name ) ), newMods );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    /**
     * @see javax.naming.directory.DirContext#modifyAttributes(
     * javax.naming.Name, javax.naming.directory.ModificationItem[])
     */
    public void modifyAttributes( Name name, List<ModificationItem> mods ) throws NamingException
    {
        List<Modification> newMods = ServerEntryUtils
            .convertToServerModification( mods, 
                getDirectoryService().getRegistries().getAttributeTypeRegistry() );
        try
        {
            doModifyOperation( buildTarget( new LdapDN( name ) ), newMods );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    /**
     * @see javax.naming.directory.DirContext#bind(java.lang.String,
     *      java.lang.Object, javax.naming.directory.Attributes)
     */
    public void bind( String name, Object obj, Attributes attrs ) throws NamingException
    {
        bind( new LdapDN( name ), obj, AttributeUtils.toCaseInsensitive( attrs ) );
    }


    /**
     * @see javax.naming.directory.DirContext#bind(javax.naming.Name,
     *      java.lang.Object, javax.naming.directory.Attributes)
     */
    public void bind( Name name, Object obj, Attributes attrs ) throws NamingException
    {
        if ( ( null == obj ) && ( null == attrs ) )
        {
            throw new NamingException( "Both obj and attrs args are null. "
                + "At least one of these parameters must not be null." );
        }

        // A null attrs defaults this to the Context.bind() operation
        if ( null == attrs )
        {
            super.bind( name, obj );
            return;
        }

        LdapDN target = buildTarget( name );

        ServerEntry serverEntry = ServerEntryUtils.toServerEntry( AttributeUtils.toCaseInsensitive( attrs ), target,
            getDirectoryService().getRegistries() );

        // No object binding so we just add the attributes
        if ( null == obj )
        {
            ServerEntry clone = ( ServerEntry ) serverEntry.clone();
            try
            {
                doAddOperation( target, clone );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
            return;
        }

        // First, use state factories to do a transformation
        DirStateFactory.Result res = DirectoryManager.getStateToBind( obj, name, this, getEnvironment(), attrs );
        ServerEntry outServerEntry = ServerEntryUtils.toServerEntry( 
            res.getAttributes(), target, getDirectoryService().getRegistries() );

        if ( outServerEntry != serverEntry )
        {
            ServerEntry clone = ( ServerEntry ) serverEntry.clone();

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute attribute : outServerEntry )
                {
                    clone.put( attribute );
                }
            }

            try
            {
                // setup the op context
                doAddOperation( target, clone );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
            return;
        }

        // Check for Referenceable
        if ( obj instanceof Referenceable )
        {
            throw new NamingException( "Do not know how to store Referenceables yet!" );
        }

        // Store different formats
        if ( obj instanceof Reference )
        {
            // Store as ref and add outAttrs
            throw new NamingException( "Do not know how to store References yet!" );
        }
        else if ( obj instanceof Serializable )
        {
            // Serialize and add outAttrs
            ServerEntry clone = ( ServerEntry ) serverEntry.clone();

            if ( outServerEntry != null && outServerEntry.size() > 0 )
            {
                for ( EntryAttribute attribute : outServerEntry )
                {
                    clone.put( attribute );
                }
            }

            // Serialize object into entry attributes and add it.
            JavaLdapSupport.serialize( serverEntry, obj, getDirectoryService().getRegistries() );
            try
            {
                // setup the op context
                doAddOperation( target, clone );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        else if ( obj instanceof DirContext )
        {
            // Grab attributes and merge with outAttrs
            ServerEntry entry = ServerEntryUtils.toServerEntry( ( ( DirContext ) obj ).getAttributes( "" ), target,
                getDirectoryService().getRegistries() );

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute attribute : outServerEntry )
                {
                    entry.put( attribute );
                }
            }

            try
            {
                // setup the op context
                doAddOperation( target, entry );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        else
        {
            throw new NamingException( "Can't find a way to bind: " + obj );
        }
    }


    /**
     * @see javax.naming.directory.DirContext#rebind(java.lang.String,
     *      java.lang.Object, javax.naming.directory.Attributes)
     */
    public void rebind( String name, Object obj, Attributes attrs ) throws NamingException
    {
        rebind( new LdapDN( name ), obj, AttributeUtils.toCaseInsensitive( attrs ) );
    }


    /**
     * @see javax.naming.directory.DirContext#rebind(javax.naming.Name,
     *      java.lang.Object, javax.naming.directory.Attributes)
     */
    public void rebind( Name name, Object obj, Attributes attrs ) throws NamingException
    {
        LdapDN target = buildTarget( name );

        try
        {
            if ( getDirectoryService().getOperationManager().hasEntry( new EntryOperationContext( getSession(), target ) ) )
            {
                doDeleteOperation( target );
            }
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }

        bind( name, obj, AttributeUtils.toCaseInsensitive( attrs ) );
    }


    /**
     * @see javax.naming.directory.DirContext#createSubcontext(java.lang.String,
     *      javax.naming.directory.Attributes)
     */
    public DirContext createSubcontext( String name, Attributes attrs ) throws NamingException
    {
        return createSubcontext( new LdapDN( name ), AttributeUtils.toCaseInsensitive( attrs ) );
    }


    /**
     * @see javax.naming.directory.DirContext#createSubcontext(
     * javax.naming.Name, javax.naming.directory.Attributes)
     */
    public DirContext createSubcontext( Name name, Attributes attrs ) throws NamingException
    {
        if ( null == attrs )
        {
            return ( DirContext ) super.createSubcontext( name );
        }

        LdapDN target = buildTarget( name );
        Rdn rdn = target.getRdn( target.size() - 1 );

        attrs = AttributeUtils.toCaseInsensitive( attrs );
        Attributes attributes = ( Attributes ) attrs.clone();

        if ( rdn.size() == 1 )
        {
            String rdnAttribute = rdn.getUpType();
            String rdnValue = ( String ) rdn.getValue();

            // Add the Rdn attribute
            boolean doRdnPut = attributes.get( rdnAttribute ) == null;
            doRdnPut = doRdnPut || attributes.get( rdnAttribute ).size() == 0;

            // TODO Fix DIRSERVER-832
            doRdnPut = doRdnPut || !attributes.get( rdnAttribute ).contains( rdnValue );

            if ( doRdnPut )
            {
                attributes.put( rdnAttribute, rdnValue );
            }
        }
        else
        {
            for ( Iterator<AttributeTypeAndValue> ii = rdn.iterator(); ii.hasNext(); /**/)
            {
                AttributeTypeAndValue atav = ii.next();

                // Add the Rdn attribute
                boolean doRdnPut = attributes.get( atav.getNormType() ) == null;
                doRdnPut = doRdnPut || attributes.get( atav.getNormType() ).size() == 0;

                // TODO Fix DIRSERVER-832
                doRdnPut = doRdnPut || !attributes.get( atav.getNormType() ).contains( atav.getNormValue() );

                if ( doRdnPut )
                {
                    attributes.put( atav.getNormType(), atav.getNormValue() );
                }
            }
        }

        // Add the new context to the server which as a side effect adds
        try
        {
            ServerEntry serverEntry = ServerEntryUtils.toServerEntry( attributes, 
                target, getDirectoryService().getRegistries() );
            doAddOperation( target, serverEntry );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }

        // Initialize the new context
        ServerLdapContext ctx = null;
        
        try
        {
            ctx = new ServerLdapContext( getService(), getSession().getEffectivePrincipal(), target );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        
        return ctx;
    }


    /**
     * Presently unsupported operation!
     */
    public DirContext getSchema( Name name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Presently unsupported operation!
     */
    public DirContext getSchema( String name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Presently unsupported operation!
     */
    public DirContext getSchemaClassDefinition( Name name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Presently unsupported operation!
     */
    public DirContext getSchemaClassDefinition( String name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    // ------------------------------------------------------------------------
    // Search Operation Implementations
    // ------------------------------------------------------------------------

    /**
     * @see javax.naming.directory.DirContext#search(java.lang.String,
     *      javax.naming.directory.Attributes)
     */
    public NamingEnumeration<SearchResult> search( String name, Attributes matchingAttributes ) throws NamingException
    {
        return search( new LdapDN( name ), matchingAttributes, null );
    }


    /**
     * @see javax.naming.directory.DirContext#search(javax.naming.Name,
     *      javax.naming.directory.Attributes)
     */
    public NamingEnumeration<SearchResult> search( Name name, Attributes matchingAttributes ) throws NamingException
    {
        return search( name, AttributeUtils.toCaseInsensitive( matchingAttributes ), null );
    }


    /**
     * @see javax.naming.directory.DirContext#search(java.lang.String,
     *      javax.naming.directory.Attributes, java.lang.String[])
     */
    public NamingEnumeration<SearchResult> search( String name, Attributes matchingAttributes,
        String[] attributesToReturn ) throws NamingException
    {
        return search( new LdapDN( name ), AttributeUtils.toCaseInsensitive( matchingAttributes ), attributesToReturn );
    }


    /**
     * @see javax.naming.directory.DirContext#search(javax.naming.Name,
     *      javax.naming.directory.Attributes, java.lang.String[])
     */
    public NamingEnumeration<SearchResult> search( Name name, Attributes matchingAttributes, String[] attributesToReturn )
        throws NamingException
    {
        SearchControls ctls = new SearchControls();
        LdapDN target = buildTarget( name );

        // If we need to return specific attributes add em to the SearchControls
        if ( null != attributesToReturn )
        {
            ctls.setReturningAttributes( attributesToReturn );
        }

        // If matchingAttributes is null/empty use a match for everything filter
        matchingAttributes = AttributeUtils.toCaseInsensitive( matchingAttributes );

        if ( ( null == matchingAttributes ) || ( matchingAttributes.size() <= 0 ) )
        {
            PresenceNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
            AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
            try
            {
                EntryFilteringCursor cursor = doSearchOperation( target, aliasDerefMode, filter, ctls );               
                return new NamingEnumerationAdapter ( cursor );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }

        // Handle simple filter expressions without multiple terms
        if ( matchingAttributes.size() == 1 )
        {
            NamingEnumeration<? extends Attribute> list = matchingAttributes.getAll();
            Attribute attr = list.next();
            list.close();

            if ( attr.size() == 1 )
            {
                Object value = attr.get();
                SimpleNode<?> node;

                if ( value instanceof byte[] )
                {
                    node = new EqualityNode<byte[]>( attr.getID(), new ClientBinaryValue( ( byte[] ) value ) );
                }
                else
                {
                    node = new EqualityNode<String>( attr.getID(), new ClientStringValue( ( String ) value ) );
                }

                AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
                try
                {
                    EntryFilteringCursor cursor = doSearchOperation( target, aliasDerefMode, node, ctls );
                    return new NamingEnumerationAdapter ( cursor ); 
                }
                catch ( Exception e )
                {
                    JndiUtils.wrap( e );
                    return null; // shut compiler up
                }
            }
        }

        /*
         * Go through the set of attributes using each attribute value pair as 
         * an attribute value assertion within one big AND filter expression.
         */
        Attribute attr;
        SimpleNode node;
        BranchNode filter = new AndNode();
        NamingEnumeration<? extends Attribute> list = matchingAttributes.getAll();

        // Loop through each attribute value pair
        while ( list.hasMore() )
        {
            attr = list.next();

            /*
             * According to JNDI if an attribute in the matchingAttributes
             * list does not have any values then we match for just the presence
             * of the attribute in the entry
             */
            if ( attr.size() == 0 )
            {
                filter.addNode( new PresenceNode( attr.getID() ) );
                continue;
            }

            /*
             * With 1 or more value we build a set of simple nodes and add them
             * to the AND node - each attribute value pair is a simple AVA node.
             */
            for ( int ii = 0; ii < attr.size(); ii++ )
            {
                Object val = attr.get( ii );

                // Add simpel AVA node if its value is a String 
                if ( val instanceof String )
                {
                    node = new EqualityNode<String>( attr.getID(), new ClientStringValue( ( String ) val ) );
                    filter.addNode( node );
                }
            }
        }

        AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
        try
        {
            EntryFilteringCursor cursor = doSearchOperation( target, aliasDerefMode, filter, ctls );
            return new NamingEnumerationAdapter( cursor );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
            return null; // shut compiler up
        }
    }


    /**
     * @see javax.naming.directory.DirContext#search(java.lang.String,
     *      java.lang.String, javax.naming.directory.SearchControls)
     */
    public NamingEnumeration<SearchResult> search( String name, String filter, SearchControls cons )
        throws NamingException
    {
        return search( new LdapDN( name ), filter, cons );
    }


    /**
     * A search overload that is used for optimizing search handling in the
     * LDAP protocol provider which deals with an ExprNode instance rather than
     * a String for the filter.
     *
     * @param name the relative name of the object serving as the search base
     * @param filter the search filter as an expression tree
     * @param cons the search controls to use
     * @return an enumeration over the SearchResults
     * @throws NamingException if there are problems performing the search
     */
    public NamingEnumeration<SearchResult> search( Name name, ExprNode filter, SearchControls cons )
        throws NamingException
    {
        LdapDN target = buildTarget( name );
        AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
        try
        {
            return new NamingEnumerationAdapter( doSearchOperation( target, aliasDerefMode, filter, cons ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
            return null; // shut compiler up
        }
    }


    /**
     * @see javax.naming.directory.DirContext#search(javax.naming.Name,
     *      java.lang.String, javax.naming.directory.SearchControls)
     */
    public NamingEnumeration<SearchResult> search( Name name, String filter, SearchControls cons )
        throws NamingException
    {
        ExprNode filterNode;
        LdapDN target = buildTarget( name );

        try
        {
            filterNode = FilterParser.parse( filter );
        }
        catch ( ParseException pe )
        {
            InvalidSearchFilterException isfe = new InvalidSearchFilterException(
                "Encountered parse exception while parsing the filter: '" + filter + "'" );
            isfe.setRootCause( pe );
            throw isfe;
        }

        AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
        try
        {
            EntryFilteringCursor cursor = doSearchOperation( target, aliasDerefMode, filterNode, cons );
            return new NamingEnumerationAdapter( cursor );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
            return null; // shut compiler up
        }
    }


    /**
     * @see javax.naming.directory.DirContext#search(java.lang.String,
     *      java.lang.String, java.lang.Object[],
     *      javax.naming.directory.SearchControls)
     */
    public NamingEnumeration<SearchResult> search( String name, String filterExpr, Object[] filterArgs,
        SearchControls cons ) throws NamingException
    {
        return search( new LdapDN( name ), filterExpr, filterArgs, cons );
    }


    /**
     * @see javax.naming.directory.DirContext#search(javax.naming.Name,
     *      java.lang.String, java.lang.Object[],
     *      javax.naming.directory.SearchControls)
     */
    public NamingEnumeration<SearchResult> search( Name name, String filterExpr, Object[] filterArgs,
        SearchControls cons ) throws NamingException
    {
        int start;
        int index;

        StringBuffer buf = new StringBuffer( filterExpr );

        // Scan until we hit the end of the string buffer 
        for ( int ii = 0; ii < buf.length(); ii++ )
        {
            try
            {
                // Advance until we hit the start of a variable
                while ( ii < buf.length() && '{' != buf.charAt( ii ) )
                {
                    ii++;
                }

                // Record start of variable at '{'
                start = ii;

                // Advance to the end of a variable at '}'
                while ( '}' != buf.charAt( ii ) )
                {
                    ii++;
                }
            }
            catch ( IndexOutOfBoundsException e )
            {
                // End of filter so done.
                break;
            }

            // Parse index
            index = Integer.parseInt( buf.substring( start + 1, ii ) );

            if ( filterArgs[index] instanceof String )
            {
                /*
                 * Replace the '{ i }' with the string representation of the value
                 * held in the filterArgs array at index index.
                 */
                buf.replace( start, ii + 1, ( String ) filterArgs[index] );
            }
            else if ( filterArgs[index] instanceof byte[] )
            {
                String hexstr = "#" + StringTools.toHexString( ( byte[] ) filterArgs[index] );
                buf.replace( start, ii + 1, hexstr );
            }
            else
            {
                /*
                 * Replace the '{ i }' with the string representation of the value
                 * held in the filterArgs array at index index.
                 */
                buf.replace( start, ii + 1, filterArgs[index].toString() );
            }
        }

        return search( name, buf.toString(), cons );
    }


    // ------------------------------------------------------------------------
    // EventDirContext implementations
    // ------------------------------------------------------------------------

    
    public void addNamingListener( Name name, String filterStr, SearchControls searchControls,
        NamingListener namingListener ) throws NamingException
    {
        ExprNode filter;

        try
        {
            filter = FilterParser.parse( filterStr );
        }
        catch ( Exception e )
        {
            NamingException e2 = new NamingException( "could not parse filter: " + filterStr );
            e2.setRootCause( e );
            throw e2;
        }

        try
        {
            DirectoryListener listener = new EventListenerAdapter( ( ServerLdapContext ) this, namingListener );
            NotificationCriteria criteria = new NotificationCriteria();
            criteria.setFilter( filter );
            criteria.setScope( SearchScope.getSearchScope( searchControls ) );
            criteria.setAliasDerefMode( AliasDerefMode.getEnum( getEnvironment() ) );
            criteria.setBase( buildTarget( name ) );
            
            getDirectoryService().getEventService().addListener( listener );
            getListeners().put( namingListener, listener );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    public void addNamingListener( String name, String filter, SearchControls searchControls,
        NamingListener namingListener ) throws NamingException
    {
        addNamingListener( new LdapDN( name ), filter, searchControls, namingListener );
    }


    public void addNamingListener( Name name, String filterExpr, Object[] filterArgs, SearchControls searchControls,
        NamingListener namingListener ) throws NamingException
    {
        int start;
        StringBuffer buf = new StringBuffer( filterExpr );

        // Scan until we hit the end of the string buffer
        for ( int ii = 0; ii < buf.length(); ii++ )
        {
            // Advance until we hit the start of a variable
            while ( '{' != buf.charAt( ii ) )
            {
                ii++;
            }

            // Record start of variable at '{'
            start = ii;

            // Advance to the end of a variable at '}'
            while ( '}' != buf.charAt( ii ) )
            {
                ii++;
            }

            /*
             * Replace the '{ i }' with the string representation of the value
             * held in the filterArgs array at index index.
             */
            buf.replace( start, ii + 1, filterArgs[ii].toString() );
        }

        addNamingListener( name, buf.toString(), searchControls, namingListener );
    }


    public void addNamingListener( String name, String filter, Object[] objects, SearchControls searchControls,
        NamingListener namingListener ) throws NamingException
    {
        addNamingListener( new LdapDN( name ), filter, objects, searchControls, namingListener );
    }
}
