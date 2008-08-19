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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.trigger.StoredProcedureParameter;

public class ModifyStoredProcedureParameterInjector extends AbstractStoredProcedureParameterInjector
{
    private LdapDN modifiedEntryName;
    private List<Modification> modifications;
    private ServerEntry oldEntry;
    
    
    public ModifyStoredProcedureParameterInjector( Invocation invocation, ModifyOperationContext opContext ) throws NamingException
    {
        super( invocation );
        modifiedEntryName = opContext.getDn();
        modifications = opContext.getModItems();
        this.oldEntry = getEntry( opContext.getRegistries() );
        Map<Class<?>, MicroInjector> injectors = super.getInjectors();
        injectors.put( StoredProcedureParameter.Modify_OBJECT.class, $objectInjector );
        injectors.put( StoredProcedureParameter.Modify_MODIFICATION.class, $modificationInjector );
        injectors.put( StoredProcedureParameter.Modify_OLD_ENTRY.class, $oldEntryInjector );
        injectors.put( StoredProcedureParameter.Modify_NEW_ENTRY.class, $newEntryInjector );
    }
    
    MicroInjector $objectInjector = new MicroInjector()
    {
        public Object inject( Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            // Return a safe copy constructed with user provided name.
            return new LdapDN( modifiedEntryName.getUpName() );
        }
    };
    
    MicroInjector $modificationInjector = new MicroInjector()
    {
        public Object inject( Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            List<Modification> newMods = new ArrayList<Modification>();
            
            for ( Modification mod:modifications )
            {
                newMods.add( mod.clone() );
            }
            
            return newMods;
        }
    };
    
    MicroInjector $oldEntryInjector = new MicroInjector()
    {
        public Object inject( Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            return oldEntry;
        }
    };
    
    MicroInjector $newEntryInjector = new MicroInjector()
    {
        public Object inject( Registries registries, StoredProcedureParameter param ) throws NamingException
        {
            return getEntry( registries );
        }
    };
    
    private ServerEntry getEntry( Registries registries ) throws NamingException
    {
        PartitionNexusProxy proxy = getInvocation().getProxy();
        /**
         * Using LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS here to exclude operational attributes
         * especially subentry related ones like "triggerExecutionSubentries".
         */
        return proxy.lookup( new LookupOperationContext( registries, modifiedEntryName ), PartitionNexusProxy.LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS );
    }

}
