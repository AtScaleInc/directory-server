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
package org.apache.directory.mitosis.operation;


import org.apache.directory.mitosis.common.CSN;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.name.LdapDN;

import javax.naming.NamingException;
import java.util.List;


/**
 * An {@link Operation} that adds an attribute to an entry.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AddAttributeOperation extends AttributeOperation
{
    private static final long serialVersionUID = 7373124294791982297L;


    /**
     * Creates a new operation that adds the specified attribute.
     * 
     * @param attribute an attribute to add
     */
    public AddAttributeOperation( CSN csn, LdapDN name, ServerAttribute attribute )
    {
        super( csn, name, attribute );
    }


    public String toString()
    {
        return super.toString() + ".add( " + getAttributeString() + " )";
    }


    protected void execute1( PartitionNexus nexus, Registries registries ) throws NamingException
    {
        ServerEntry serverEntry = new DefaultServerEntry( registries, LdapDN.EMPTY_LDAPDN );
        ServerAttribute attribute = getAttribute( registries.getAttributeTypeRegistry() );
        serverEntry.put( attribute );
        List<Modification> items = ModifyOperationContext.createModItems( serverEntry, ModificationOperation.ADD_ATTRIBUTE );
        nexus.modify( new ModifyOperationContext( registries, getName(), items ) );
    }
}
