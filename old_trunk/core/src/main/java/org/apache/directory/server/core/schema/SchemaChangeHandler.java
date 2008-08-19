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
package org.apache.directory.server.core.schema;


import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;

import javax.naming.NamingException;
import java.util.List;


/**
 * A common interface used by schema change handlers which react to 
 * changes performed on schema entities.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface SchemaChangeHandler
{
    void add( LdapDN name, ServerEntry entry ) throws NamingException;
    
    void delete( LdapDN name, ServerEntry entry, boolean cascaded ) throws NamingException;
    
    void rename( LdapDN name, ServerEntry entry, Rdn newRdn, boolean cascaded ) throws NamingException;
    
    void modify( LdapDN name, ModificationOperation modOp, ServerEntry mods, ServerEntry entry, ServerEntry targetEntry, boolean cascaded ) 
        throws NamingException;
    
    void modify( LdapDN name, List<Modification> mods, ServerEntry entry, ServerEntry targetEntry, boolean cascaded )
        throws NamingException;
    
    void move( LdapDN oriChildName, LdapDN newParentName, Rdn newRn, boolean deleteOldRn, ServerEntry entry,
        boolean cascaded ) throws NamingException;
    
    void replace( LdapDN oriChildName, LdapDN newParentName, ServerEntry entry, boolean cascaded ) throws NamingException;
}
