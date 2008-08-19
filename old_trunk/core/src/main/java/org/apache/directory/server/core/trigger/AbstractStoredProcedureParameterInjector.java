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

package org.apache.directory.server.core.trigger;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.Name;
import javax.naming.NamingException;

import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.jndi.ServerContext;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.trigger.StoredProcedureParameter;
import org.apache.directory.shared.ldap.trigger.StoredProcedureParameter.Generic_LDAP_CONTEXT;

public abstract class AbstractStoredProcedureParameterInjector implements StoredProcedureParameterInjector
{
    private Invocation invocation;
    private Map<Class<?>, MicroInjector> injectors;
    
    public AbstractStoredProcedureParameterInjector( Invocation invocation )
    {
        this.invocation = invocation;
        injectors = new HashMap<Class<?>, MicroInjector>();
        injectors.put( StoredProcedureParameter.Generic_OPERATION_PRINCIPAL.class, $operationPrincipalInjector );
        injectors.put( StoredProcedureParameter.Generic_LDAP_CONTEXT.class, $ldapContextInjector );
    }
    
    protected Name getOperationPrincipal() throws NamingException
    {
        Principal principal = ( ( ServerContext ) invocation.getCaller() ).getPrincipal();
        Name userName = new LdapDN( principal.getName() );
        return userName;
    }
    
    protected Map<Class<?>, MicroInjector> getInjectors()
    {
        return injectors;
    }
    
    public Invocation getInvocation()
    {
        return invocation;
    }
    
    public void setInvocation( Invocation invocation )
    {
        this.invocation = invocation;
    }
    
    public final List<Object> getArgumentsToInject( Registries registries, List<StoredProcedureParameter> parameterList ) throws NamingException
    {
        List<Object> arguments = new ArrayList<Object>();
        
        Iterator<StoredProcedureParameter> it = parameterList.iterator();
        
        while ( it.hasNext() )
        {
            StoredProcedureParameter spParameter = it.next();
            MicroInjector injector = injectors.get( spParameter.getClass() );
            arguments.add( injector.inject( registries, spParameter ) );
        }
        
        return arguments;
    }
    
    MicroInjector $operationPrincipalInjector = new MicroInjector()
    {
        public Object inject( Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            return getOperationPrincipal();
        }
    };
    
    MicroInjector $ldapContextInjector = new MicroInjector()
    {
        public Object inject(  Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            Generic_LDAP_CONTEXT ldapCtxParam = ( Generic_LDAP_CONTEXT ) param;
            LdapDN ldapCtxName = ldapCtxParam.getCtxName();
            return ( ( ServerLdapContext ) ( ( ServerLdapContext ) invocation.getCaller() ).getRootContext()).lookup( ldapCtxName );
        }
    };

}
