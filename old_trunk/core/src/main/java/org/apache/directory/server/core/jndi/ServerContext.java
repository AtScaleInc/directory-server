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


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.AttributeTypeAndValue;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.StringTools;

import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.event.EventContext;
import javax.naming.event.NamingListener;
import javax.naming.ldap.Control;
import javax.naming.spi.DirStateFactory;
import javax.naming.spi.DirectoryManager;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;


/**
 * A non-federated abstract Context implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class ServerContext implements EventContext
{
    /** property key used for deleting the old RDN on a rename */
    public static final String DELETE_OLD_RDN_PROP = JndiPropertyConstants.JNDI_LDAP_DELETE_RDN;

    /** Empty array of controls for use in dealing with them */
    protected static final Control[] EMPTY_CONTROLS = new Control[0];

    /** The directory service which owns this context **/
    private final DirectoryService service;

    /** The global registries **/
    protected final Registries registries;

    /** The interceptor proxy to the backend nexus */
    private final PartitionNexus nexusProxy;

    /** The cloned environment used by this Context */
    private final Hashtable<String, Object> env;

    /** The distinguished name of this Context */
    private final LdapDN dn;

    /** The set of registered NamingListeners */
    private final Set<NamingListener> listeners = new HashSet<NamingListener>();

    /** The Principal associated with this context */
    private LdapPrincipal principal;

    /** The request controls to set on operations before performing them */
    protected Control[] requestControls = EMPTY_CONTROLS;

    /** The response controls to set after performing operations */
    protected Control[] responseControls = EMPTY_CONTROLS;

    /** Connection level controls associated with the session */
    protected Control[] connectControls = EMPTY_CONTROLS;


    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Must be called by all subclasses to initialize the nexus proxy and the
     * environment settings to be used by this Context implementation.  This
     * specific contstructor relies on the presence of the {@link
     * Context#PROVIDER_URL} key and value to determine the distinguished name
     * of the newly created context.  It also checks to make sure the
     * referenced name actually exists within the system.  This constructor
     * is used for all InitialContext requests.
     * 
     * @param service the parent service that manages this context
     * @param env the environment properties used by this context.
     * @throws NamingException if the environment parameters are not set 
     * correctly.
     */
    @SuppressWarnings(value =
        { "unchecked" })
    protected ServerContext( DirectoryService service, Hashtable<String, Object> env ) throws NamingException
    {
        this.service = service;

        // set references to cloned env and the proxy
        this.nexusProxy = new PartitionNexusProxy( this, service );

        this.env = env;
        LdapJndiProperties props = LdapJndiProperties.getLdapJndiProperties( this.env );
        dn = props.getProviderDn();

        // need to issue a bind operation here
        doBindOperation( props.getBindDn(), props.getCredentials(), props.getAuthenticationMechanisms(), props
            .getSaslAuthId() );

        registries = service.getRegistries();

        if ( !nexusProxy.hasEntry( new EntryOperationContext( registries, dn ) ) )
        {
            throw new NameNotFoundException( dn + " does not exist" );
        }
    }


    /**
     * Must be called by all subclasses to initialize the nexus proxy and the
     * environment settings to be used by this Context implementation.  This
     * constructor is used to propagate new contexts from existing contexts.
     *
     * @param principal the directory user principal that is propagated
     * @param dn the distinguished name of this context
     * @param service the directory service core
     * @throws NamingException if there is a problem creating the new context
     */
    public ServerContext( DirectoryService service, LdapPrincipal principal, Name dn ) throws NamingException
    {
        this.service = service;
        this.dn = ( LdapDN ) dn.clone();

        this.env = new Hashtable<String, Object>();
        this.env.put( PROVIDER_URL, dn.toString() );
        this.env.put( DirectoryService.JNDI_KEY, service );
        this.nexusProxy = new PartitionNexusProxy( this, service );
        this.principal = principal;
        registries = service.getRegistries();
    }


    // ------------------------------------------------------------------------
    // Protected Methods for Control [De]Marshalling 
    // ------------------------------------------------------------------------
    // Use these methods instead of manually calling the nexusProxy so we can
    // add request controls to operation contexts before the call and extract 
    // response controls from the contexts after the call.  NOTE that the 
    // requestControls must be cleared after each operation.  This makes a 
    // context not thread safe.
    // ------------------------------------------------------------------------

    /**
     * Used to encapsulate [de]marshalling of controls before and after add operations.
     * @param entry
     * @param target
     */
    protected void doAddOperation( LdapDN target, ServerEntry entry ) throws NamingException
    {
        // setup the op context and populate with request controls
        AddOperationContext opCtx = new AddOperationContext( service.getRegistries(), entry );

        opCtx.addRequestControls( requestControls );

        // execute add operation
        nexusProxy.add( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after delete operations.
     * @param target
     */
    protected void doDeleteOperation( LdapDN target ) throws NamingException
    {
        // setup the op context and populate with request controls
        DeleteOperationContext opCtx = new DeleteOperationContext( registries, target );
        opCtx.addRequestControls( requestControls );

        // execute delete operation
        nexusProxy.delete( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after list operations.
     * @param dn
     * @param aliasDerefMode
     * @param filter
     * @param searchControls
     * @return NamingEnumeration
     */
    protected NamingEnumeration<ServerSearchResult> doSearchOperation( LdapDN dn, AliasDerefMode aliasDerefMode,
        ExprNode filter, SearchControls searchControls ) throws NamingException
    {
        // setup the op context and populate with request controls
        SearchOperationContext opCtx = new SearchOperationContext( registries, dn, aliasDerefMode, filter,
            searchControls );
        opCtx.addRequestControls( requestControls );

        // execute search operation
        NamingEnumeration<ServerSearchResult> results = nexusProxy.search( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        return results;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after list operations.
     * @param dn
     * @param aliasDerefMode
     * @param filter
     * @param searchControls
     * @return NamingEnumeration
     */
    protected NamingEnumeration<ServerSearchResult> doSearchOperation( LdapDN dn, AliasDerefMode aliasDerefMode,
        ExprNode filter, SearchControls searchControls, InetSocketAddress clientAddress ) throws NamingException
    {
        // setup the op context and populate with request controls
        SearchOperationContext opCtx = new SearchOperationContext( registries, dn, aliasDerefMode, filter,
            searchControls );

        opCtx.put( "client_hostname", clientAddress.getHostName() );
        opCtx.put( "client_port", String.valueOf( clientAddress.getPort() ) );
        opCtx.addRequestControls( requestControls );

        // execute search operation
        NamingEnumeration<ServerSearchResult> results = nexusProxy.search( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        return results;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after list operations.
     */
    protected NamingEnumeration<ServerSearchResult> doListOperation( LdapDN target ) throws NamingException
    {
        // setup the op context and populate with request controls
        ListOperationContext opCtx = new ListOperationContext( registries, target );
        opCtx.addRequestControls( requestControls );

        // execute list operation
        NamingEnumeration<ServerSearchResult> results = nexusProxy.list( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        return results;
    }


    protected ServerEntry doGetRootDSEOperation( LdapDN target ) throws NamingException
    {
        GetRootDSEOperationContext opCtx = new GetRootDSEOperationContext( registries, target );
        opCtx.addRequestControls( requestControls );

        // do not reset request controls since this is not an external 
        // operation and not do bother setting the response controls either
        return nexusProxy.getRootDSE( opCtx );
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after lookup operations.
     */
    protected ServerEntry doLookupOperation( LdapDN target ) throws NamingException
    {
        // setup the op context and populate with request controls
        LookupOperationContext opCtx;

        // execute lookup/getRootDSE operation
        opCtx = new LookupOperationContext( registries, target );
        opCtx.addRequestControls( requestControls );
        ServerEntry serverEntry = nexusProxy.lookup( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
        return serverEntry;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after lookup operations.
     */
    protected ServerEntry doLookupOperation( LdapDN target, String[] attrIds ) throws NamingException
    {
        // setup the op context and populate with request controls
        LookupOperationContext opCtx;

        // execute lookup/getRootDSE operation
        opCtx = new LookupOperationContext( registries, target, attrIds );
        opCtx.addRequestControls( requestControls );
        ServerEntry serverEntry = nexusProxy.lookup( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        // Now remove the ObjectClass attribute if it has not been requested
        if ( ( opCtx.getAttrsId() != null ) && ( opCtx.getAttrsId().size() != 0 ) )
        {
            if ( ( serverEntry.get( SchemaConstants.OBJECT_CLASS_AT ) != null )
                && ( serverEntry.get( SchemaConstants.OBJECT_CLASS_AT ).size() == 0 ) )
            {
                serverEntry.removeAttributes( SchemaConstants.OBJECT_CLASS_AT );
            }
        }

        return serverEntry;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after bind operations.
     */
    protected void doBindOperation( LdapDN bindDn, byte[] credentials, List<String> mechanisms, String saslAuthId )
        throws NamingException
    {
        // setup the op context and populate with request controls
        BindOperationContext opCtx = new BindOperationContext( registries );
        opCtx.setDn( bindDn );
        opCtx.setCredentials( credentials );
        opCtx.setMechanisms( mechanisms );
        opCtx.setSaslAuthId( saslAuthId );
        opCtx.addRequestControls( requestControls );

        // execute bind operation
        this.nexusProxy.bind( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after moveAndRename operations.
     */
    protected void doMoveAndRenameOperation( LdapDN oldDn, LdapDN parent, String newRdn, boolean delOldDn )
        throws NamingException
    {
        // setup the op context and populate with request controls
        MoveAndRenameOperationContext opCtx = new MoveAndRenameOperationContext( registries, oldDn, parent, new Rdn(
            newRdn ), delOldDn );
        opCtx.addRequestControls( requestControls );

        // execute moveAndRename operation
        nexusProxy.moveAndRename( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after modify operations.
     */
    protected void doModifyOperation( LdapDN dn, List<Modification> modifications ) throws NamingException
    {
        // setup the op context and populate with request controls
        ModifyOperationContext opCtx = new ModifyOperationContext( registries, dn, modifications );
        opCtx.addRequestControls( requestControls );

        // execute modify operation
        nexusProxy.modify( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after moveAndRename operations.
     */
    protected void doMove( LdapDN oldDn, LdapDN target ) throws NamingException
    {
        // setup the op context and populate with request controls
        MoveOperationContext opCtx = new MoveOperationContext( registries, oldDn, target );
        opCtx.addRequestControls( requestControls );

        // execute move operation
        nexusProxy.move( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after rename operations.
     */
    protected void doRename( LdapDN oldDn, String newRdn, boolean delOldRdn ) throws NamingException
    {
        // setup the op context and populate with request controls
        RenameOperationContext opCtx = new RenameOperationContext( registries, oldDn, new Rdn( newRdn ), delOldRdn );
        opCtx.addRequestControls( requestControls );

        // execute rename operation
        nexusProxy.rename( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    // ------------------------------------------------------------------------
    // New Impl Specific Public Methods
    // ------------------------------------------------------------------------

    /**
     * Gets a handle on the root context of the DIT.  The RootDSE as the present user.
     *
     * @return the rootDSE context
     * @throws NamingException if this fails
     */
    public abstract ServerContext getRootContext() throws NamingException;


    /**
     * Gets the {@link DirectoryService} associated with this context.
     *
     * @return the directory service associated with this context
     */
    public DirectoryService getService()
    {
        return service;
    }


    /**
     * Gets the principal of the authenticated user which also happens to own
     *
     * @return the principal associated with this context
     */
    public LdapPrincipal getPrincipal()
    {
        return principal;
    }


    /**
     * Sets the principal of the authenticated user which also happens to own.
     * This method can be invoked only once to keep this property safe.  This
     * method has been changed to be public but it can only be set by the
     * AuthenticationInterceptor to prevent malicious code from changing the
     * effective principal.
     *
     * @param wrapper the wrapper - has to go
     * @todo get ride of using this wrapper and protect this call with a security manager
     */
    public void setPrincipal( AuthenticationInterceptor.TrustedPrincipalWrapper wrapper )
    {
        this.principal = wrapper.getPrincipal();
    }


    // ------------------------------------------------------------------------
    // Protected Accessor Methods
    // ------------------------------------------------------------------------

    /**
     * Gets the RootNexus proxy.
     *
     * @return the proxy to the backend nexus.
     */
    protected PartitionNexus getNexusProxy()
    {
        return nexusProxy;
    }


    /**
     * Gets the distinguished name of the entry associated with this Context.
     * 
     * @return the distinguished name of this Context's entry.
     */
    protected Name getDn()
    {
        return dn;
    }


    // ------------------------------------------------------------------------
    // JNDI Context Interface Methods
    // ------------------------------------------------------------------------

    /**
     * @see javax.naming.Context#close()
     */
    public void close() throws NamingException
    {
        for ( NamingListener listener : listeners )
        {
            ( ( PartitionNexusProxy ) this.nexusProxy ).removeNamingListener( this, listener );
        }
    }


    /**
     * @see javax.naming.Context#getNameInNamespace()
     */
    public String getNameInNamespace() throws NamingException
    {
        return dn.getUpName();
    }


    /**
     * @see javax.naming.Context#getEnvironment()
     */
    public Hashtable<String, Object> getEnvironment()
    {
        return env;
    }


    /**
     * @see javax.naming.Context#addToEnvironment(java.lang.String, 
     * java.lang.Object)
     */
    public Object addToEnvironment( String propName, Object propVal ) throws NamingException
    {
        return env.put( propName, propVal );
    }


    /**
     * @see javax.naming.Context#removeFromEnvironment(java.lang.String)
     */
    public Object removeFromEnvironment( String propName ) throws NamingException
    {
        return env.remove( propName );
    }


    /**
     * @see javax.naming.Context#createSubcontext(java.lang.String)
     */
    public Context createSubcontext( String name ) throws NamingException
    {
        return createSubcontext( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#createSubcontext(javax.naming.Name)
     */
    public Context createSubcontext( Name name ) throws NamingException
    {
        LdapDN target = buildTarget( name );
        ServerEntry serverEntry = new DefaultServerEntry( registries, target );
        serverEntry.add( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, JavaLdapSupport.JCONTAINER_ATTR );

        // Now add the CN attribute, which is mandatory
        Rdn rdn = target.getRdn();

        if ( rdn != null )
        {
            if ( SchemaConstants.CN_AT.equals( rdn.getNormType() ) )
            {
                serverEntry.put( rdn.getUpType(), ( String ) rdn.getUpValue() );
            }
            else
            {
                // No CN in the rdn, this is an error
                throw new LdapSchemaViolationException( name
                    + " does not contains the mandatory 'cn' attribute for JavaContainer ObjectClass!",
                    ResultCodeEnum.OBJECT_CLASS_VIOLATION );
            }
        }
        else
        {
            // No CN in the rdn, this is an error
            throw new LdapSchemaViolationException( name
                + " does not contains the mandatory 'cn' attribute for JavaContainer ObjectClass!",
                ResultCodeEnum.OBJECT_CLASS_VIOLATION );
        }

        /*
         * Add the new context to the server which as a side effect adds 
         * operational attributes to the serverEntry refering instance which
         * can them be used to initialize a new ServerLdapContext.  Remember
         * we need to copy over the controls as well to propagate the complete 
         * environment besides what's in the hashtable for env.
         */
        doAddOperation( target, serverEntry );
        return new ServerLdapContext( service, principal, target );
    }


    /**
     * @see javax.naming.Context#destroySubcontext(java.lang.String)
     */
    public void destroySubcontext( String name ) throws NamingException
    {
        destroySubcontext( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#destroySubcontext(javax.naming.Name)
     */
    public void destroySubcontext( Name name ) throws NamingException
    {
        LdapDN target = buildTarget( name );

        if ( target.size() == 0 )
        {
            throw new LdapNoPermissionException( "can't delete the rootDSE" );
        }

        doDeleteOperation( target );
    }


    /**
     * @see javax.naming.Context#bind(java.lang.String, java.lang.Object)
     */
    public void bind( String name, Object obj ) throws NamingException
    {
        bind( new LdapDN( name ), obj );
    }


    private void injectRdnAttributeValues( LdapDN target, ServerEntry serverEntry ) throws NamingException
    {
        // Add all the RDN attributes and their values to this entry
        Rdn rdn = target.getRdn( target.size() - 1 );

        if ( rdn.size() == 1 )
        {
            serverEntry.put( rdn.getUpType(), ( String ) rdn.getValue() );
        }
        else
        {
            for ( AttributeTypeAndValue atav : rdn )
            {
                serverEntry.put( atav.getUpType(), ( String ) atav.getNormValue() );
            }
        }
    }


    /**
     * @see javax.naming.Context#bind(javax.naming.Name, java.lang.Object)
     */
    public void bind( Name name, Object obj ) throws NamingException
    {
        // First, use state factories to do a transformation
        DirStateFactory.Result res = DirectoryManager.getStateToBind( obj, name, this, env, null );

        LdapDN target = buildTarget( name );

        // let's be sure that the Attributes is case insensitive
        ServerEntry outServerEntry = ServerEntryUtils.toServerEntry( AttributeUtils.toCaseInsensitive( res
            .getAttributes() ), target, registries );

        if ( outServerEntry != null )
        {
            doAddOperation( target, outServerEntry );
            return;
        }

        if ( obj instanceof ServerEntry )
        {
            doAddOperation( target, ( ServerEntry ) obj );
        }
        // Check for Referenceable
        else if ( obj instanceof Referenceable )
        {
            throw new NamingException( "Do not know how to store Referenceables yet!" );
        }
        // Store different formats
        else if ( obj instanceof Reference )
        {
            // Store as ref and add outAttrs
            throw new NamingException( "Do not know how to store References yet!" );
        }
        else if ( obj instanceof Serializable )
        {
            // Serialize and add outAttrs
            ServerEntry serverEntry = new DefaultServerEntry( registries, target );

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute serverAttribute : outServerEntry )
                {
                    serverEntry.put( serverAttribute );
                }
            }

            // Get target and inject all rdn attributes into entry
            injectRdnAttributeValues( target, serverEntry );

            // Serialize object into entry attributes and add it.
            JavaLdapSupport.serialize( serverEntry, obj, registries );
            doAddOperation( target, serverEntry );
        }
        else if ( obj instanceof DirContext )
        {
            // Grab attributes and merge with outAttrs
            ServerEntry serverEntry = ServerEntryUtils.toServerEntry( ( ( DirContext ) obj ).getAttributes( "" ),
                target, registries );

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute serverAttribute : outServerEntry )
                {
                    serverEntry.put( serverAttribute );
                }
            }

            injectRdnAttributeValues( target, serverEntry );
            doAddOperation( target, serverEntry );
        }
        else
        {
            throw new NamingException( "Can't find a way to bind: " + obj );
        }
    }


    /**
     * @see javax.naming.Context#rename(java.lang.String, java.lang.String)
     */
    public void rename( String oldName, String newName ) throws NamingException
    {
        rename( new LdapDN( oldName ), new LdapDN( newName ) );
    }


    /**
     * @see javax.naming.Context#rename(javax.naming.Name, javax.naming.Name)
     */
    public void rename( Name oldName, Name newName ) throws NamingException
    {
        LdapDN oldDn = buildTarget( oldName );
        LdapDN newDn = buildTarget( newName );

        if ( oldDn.size() == 0 )
        {
            throw new LdapNoPermissionException( "can't rename the rootDSE" );
        }

        // calculate parents
        LdapDN oldBase = ( LdapDN ) oldName.clone();
        oldBase.remove( oldName.size() - 1 );
        LdapDN newBase = ( LdapDN ) newName.clone();
        newBase.remove( newName.size() - 1 );

        String newRdn = newName.get( newName.size() - 1 );
        String oldRdn = oldName.get( oldName.size() - 1 );
        boolean delOldRdn = true;

        /*
         * Attempt to use the java.naming.ldap.deleteRDN environment property
         * to get an override for the deleteOldRdn option to modifyRdn.  
         */
        if ( null != env.get( DELETE_OLD_RDN_PROP ) )
        {
            String delOldRdnStr = ( String ) env.get( DELETE_OLD_RDN_PROP );
            delOldRdn = !delOldRdnStr.equalsIgnoreCase( "false" ) && !delOldRdnStr.equalsIgnoreCase( "no" )
                && !delOldRdnStr.equals( "0" );
        }

        /*
         * We need to determine if this rename operation corresponds to a simple
         * RDN name change or a move operation.  If the two names are the same
         * except for the RDN then it is a simple modifyRdn operation.  If the
         * names differ in size or have a different baseDN then the operation is
         * a move operation.  Furthermore if the RDN in the move operation 
         * changes it is both an RDN change and a move operation.
         */
        if ( ( oldName.size() == newName.size() ) && oldBase.equals( newBase ) )
        {
            doRename( oldDn, newRdn, delOldRdn );
        }
        else
        {
            LdapDN target = ( LdapDN ) newDn.clone();
            target.remove( newDn.size() - 1 );

            if ( newRdn.equalsIgnoreCase( oldRdn ) )
            {
                doMove( oldDn, target );
            }
            else
            {
                doMoveAndRenameOperation( oldDn, target, newRdn, delOldRdn );
            }
        }
    }


    /**
     * @see javax.naming.Context#rebind(java.lang.String, java.lang.Object)
     */
    public void rebind( String name, Object obj ) throws NamingException
    {
        rebind( new LdapDN( name ), obj );
    }


    /**
     * @see javax.naming.Context#rebind(javax.naming.Name, java.lang.Object)
     */
    public void rebind( Name name, Object obj ) throws NamingException
    {
        LdapDN target = buildTarget( name );

        if ( nexusProxy.hasEntry( new EntryOperationContext( registries, target ) ) )
        {
            doDeleteOperation( target );
        }

        bind( name, obj );
    }


    /**
     * @see javax.naming.Context#unbind(java.lang.String)
     */
    public void unbind( String name ) throws NamingException
    {
        unbind( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#unbind(javax.naming.Name)
     */
    public void unbind( Name name ) throws NamingException
    {
        doDeleteOperation( buildTarget( name ) );
    }


    /**
     * @see javax.naming.Context#lookup(java.lang.String)
     */
    public Object lookup( String name ) throws NamingException
    {
        if ( StringTools.isEmpty( name ) )
        {
            return lookup( LdapDN.EMPTY_LDAPDN );
        }
        else
        {
            return lookup( new LdapDN( name ) );
        }
    }


    /**
     * @see javax.naming.Context#lookup(javax.naming.Name)
     */
    public Object lookup( Name name ) throws NamingException
    {
        Object obj;
        LdapDN target = buildTarget( name );

        ServerEntry serverEntry;

        if ( name.size() == 0 )
        {
            serverEntry = doGetRootDSEOperation( target );
        }
        else
        {
            serverEntry = doLookupOperation( target );
        }

        try
        {
            obj = DirectoryManager.getObjectInstance( null, name, this, env, ServerEntryUtils
                .toAttributesImpl( serverEntry ) );
        }
        catch ( Exception e )
        {
            String msg = "Failed to create an object for " + target;
            msg += " using object factories within the context's environment.";
            NamingException ne = new NamingException( msg );
            ne.setRootCause( e );
            throw ne;
        }

        if ( obj != null )
        {
            return obj;
        }

        // First lets test and see if the entry is a serialized java object
        if ( serverEntry.get( JavaLdapSupport.JCLASSNAME_ATTR ) != null )
        {
            // Give back serialized object and not a context
            return JavaLdapSupport.deserialize( serverEntry );
        }

        // Initialize and return a context since the entry is not a java object
        return new ServerLdapContext( service, principal, target );
    }


    /**
     * @see javax.naming.Context#lookupLink(java.lang.String)
     */
    public Object lookupLink( String name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * @see javax.naming.Context#lookupLink(javax.naming.Name)
     */
    public Object lookupLink( Name name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Non-federated implementation presuming the name argument is not a 
     * composite name spanning multiple namespaces but a compound name in 
     * the same LDAP namespace.  Hence the parser returned is always the
     * same as calling this method with the empty String. 
     * 
     * @see javax.naming.Context#getNameParser(java.lang.String)
     */
    public NameParser getNameParser( String name ) throws NamingException
    {
        return new NameParser()
        {
            public Name parse( String name ) throws NamingException
            {
                return new LdapDN( name );
            }
        };
    }


    /**
     * Non-federated implementation presuming the name argument is not a 
     * composite name spanning multiple namespaces but a compound name in 
     * the same LDAP namespace.  Hence the parser returned is always the
     * same as calling this method with the empty String Name.
     * 
     * @see javax.naming.Context#getNameParser(javax.naming.Name)
     */
    public NameParser getNameParser( Name name ) throws NamingException
    {
        return new NameParser()
        {
            public Name parse( String name ) throws NamingException
            {
                return new LdapDN( name );
            }
        };
    }


    /**
     * @see javax.naming.Context#list(java.lang.String)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration list( String name ) throws NamingException
    {
        return list( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#list(javax.naming.Name)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration list( Name name ) throws NamingException
    {
        return ServerEntryUtils.toSearchResultEnum( doListOperation( buildTarget( name ) ) );
    }


    /**
     * @see javax.naming.Context#listBindings(java.lang.String)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration listBindings( String name ) throws NamingException
    {
        return listBindings( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#listBindings(javax.naming.Name)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration listBindings( Name name ) throws NamingException
    {
        // Conduct a special one level search at base for all objects
        LdapDN base = buildTarget( name );
        PresenceNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
        return doSearchOperation( base, aliasDerefMode, filter, ctls );
    }


    /**
     * @see javax.naming.Context#composeName(java.lang.String, java.lang.String)
     */
    public String composeName( String name, String prefix ) throws NamingException
    {
        return composeName( new LdapDN( name ), new LdapDN( prefix ) ).toString();
    }


    /**
     * @see javax.naming.Context#composeName(javax.naming.Name,
     * javax.naming.Name)
     */
    public Name composeName( Name name, Name prefix ) throws NamingException
    {
        // No prefix reduces to name, or the name relative to this context
        if ( prefix == null || prefix.size() == 0 )
        {
            return name;
        }

        /*
         * Example: This context is ou=people and say name is the relative
         * name of uid=jwalker and the prefix is dc=domain.  Then we must
         * compose the name relative to prefix which would be:
         * 
         * uid=jwalker,ou=people,dc=domain.
         * 
         * The following general algorithm generates the right name:
         *      1). Find the Dn for name and walk it from the head to tail
         *          trying to match for the head of prefix.
         *      2). Remove name components from the Dn until a match for the 
         *          head of the prefix is found.
         *      3). Return the remainder of the fqn or Dn after chewing off some
         */

        // 1). Find the Dn for name and walk it from the head to tail
        Name fqn = buildTarget( name );
        String head = prefix.get( 0 );

        // 2). Walk the fqn trying to match for the head of the prefix
        while ( fqn.size() > 0 )
        {
            // match found end loop
            if ( fqn.get( 0 ).equalsIgnoreCase( head ) )
            {
                return fqn;
            }
            else
            // 2). Remove name components from the Dn until a match 
            {
                fqn.remove( 0 );
            }
        }

        String msg = "The prefix '" + prefix + "' is not an ancestor of this ";
        msg += "entry '" + dn + "'";
        throw new NamingException( msg );
    }


    // ------------------------------------------------------------------------
    // EventContext implementations
    // ------------------------------------------------------------------------

    public void addNamingListener( Name name, int scope, NamingListener namingListener ) throws NamingException
    {
        ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( scope );
        ( ( PartitionNexusProxy ) this.nexusProxy ).addNamingListener( this, buildTarget( name ), filter, controls,
            namingListener );
        listeners.add( namingListener );
    }


    public void addNamingListener( String name, int scope, NamingListener namingListener ) throws NamingException
    {
        addNamingListener( new LdapDN( name ), scope, namingListener );
    }


    public void removeNamingListener( NamingListener namingListener ) throws NamingException
    {
        ( ( PartitionNexusProxy ) this.nexusProxy ).removeNamingListener( this, namingListener );
        listeners.remove( namingListener );
    }


    public boolean targetMustExist() throws NamingException
    {
        return false;
    }


    /**
     * Allows subclasses to register and unregister listeners.
     *
     * @return the set of listeners used for tracking registered name listeners.
     */
    protected Set<NamingListener> getListeners()
    {
        return listeners;
    }


    // ------------------------------------------------------------------------
    // Utility Methods to Reduce Code
    // ------------------------------------------------------------------------

    /**
     * Clones this context's DN and adds the components of the name relative to 
     * this context to the left hand side of this context's cloned DN. 
     * 
     * @param relativeName a name relative to this context.
     * @return the name of the target
     * @throws InvalidNameException if relativeName is not a valid name in
     *      the LDAP namespace.
     */
    LdapDN buildTarget( Name relativeName ) throws InvalidNameException
    {
        LdapDN target = ( LdapDN ) dn.clone();

        // Add to left hand side of cloned DN the relative name arg
        target.addAllNormalized( target.size(), relativeName );
        return target;
    }
}
