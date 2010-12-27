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
package org.apache.directory.server.core.interceptor;


import java.util.HashSet;
import java.util.Set;

import javax.naming.Context;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.schema.AttributeType;


/**
 * A easy-to-use implementation of {@link Interceptor}.  All methods are
 * implemented to pass the flow of control to next interceptor by defaults.
 * Please override the methods you have concern in.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public abstract class BaseInterceptor implements Interceptor
{
    /** set of operational attribute types used for representing the password policy state of a user entry */
    protected static final Set<AttributeType> PWD_POLICY_STATE_ATTRIBUTE_TYPES = new HashSet<AttributeType>();

    /** Set of operational attribute types used to manage the AdmnistrativePoint SequenceNumbers  */
    protected static final Set<AttributeType> AP_SEQUENCE_NUMBER_ATTRIBUTE_TYPES = new HashSet<AttributeType>();

    /**
     * default interceptor name is its class, preventing accidental duplication of interceptors by naming
     * instances differently
     * @return (default, class name) interceptor name
     */
    public String getName()
    {
        return getClass().getName();
    }


    /**
     * TODO delete this since it uses static access
     * Returns {@link LdapPrincipal} of current context.
     * @return the authenticated principal
     */
    public static LdapPrincipal getPrincipal()
    {
        return getContext().getSession().getEffectivePrincipal();
    }


    /**
     * TODO delete this since it uses static access
     * Returns the current JNDI {@link Context}.
     * @return the context on the invocation stack
     */
    public static OperationContext getContext()
    {
        return InvocationStack.getInstance().peek();
    }


    /**
     * Creates a new instance.
     */
    protected BaseInterceptor()
    {
    }


    /**
     * This method does nothing by default.
     * @throws Exception 
     */
    public void init( DirectoryService directoryService ) throws LdapException
    {
    }


    /**
     * This method does nothing by default.
     */
    public void destroy()
    {
    }


    // ------------------------------------------------------------------------
    // Interceptor's Invoke Method
    // ------------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        next.add( addContext );
    }


    public void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException
    {
        next.delete( deleteContext );
    }


    public Entry getRootDSE( NextInterceptor next, GetRootDSEOperationContext getRootDseContext ) throws LdapException
    {
        return next.getRootDSE( getRootDseContext );
    }


    public boolean hasEntry( NextInterceptor next, EntryOperationContext hasEntryContext ) throws LdapException
    {
        return next.hasEntry( hasEntryContext );
    }


    public EntryFilteringCursor list( NextInterceptor next, ListOperationContext listContext ) throws LdapException
    {
        return next.list( listContext );
    }


    public Entry lookup( NextInterceptor next, LookupOperationContext lookupContext ) throws LdapException
    {
        return next.lookup( lookupContext );
    }


    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        next.modify( modifyContext );
    }


    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext )
        throws LdapException
    {
        next.moveAndRename( moveAndRenameContext );
    }


    public void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        next.rename( renameContext );
    }


    /**
     * {@inheritDoc}
     */
    public void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException
    {
        next.move( moveContext );
    }


    public EntryFilteringCursor search( NextInterceptor next, SearchOperationContext searchContext )
        throws LdapException
    {
        return next.search( searchContext );
    }


    public boolean compare( NextInterceptor next, CompareOperationContext compareContext ) throws LdapException
    {
        return next.compare( compareContext );
    }


    public void bind( NextInterceptor next, BindOperationContext bindContext ) throws LdapException
    {
        next.bind( bindContext );
    }


    public void unbind( NextInterceptor next, UnbindOperationContext unbindContext ) throws LdapException
    {
        next.unbind( unbindContext );
    }
}
