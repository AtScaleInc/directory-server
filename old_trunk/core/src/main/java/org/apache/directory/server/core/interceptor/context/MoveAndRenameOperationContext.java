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
package org.apache.directory.server.core.interceptor.context;


import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;


/**
 * A Move And Rename context used for Interceptors. It contains all the informations
 * needed for the modify DN operation, and used by all the interceptors
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MoveAndRenameOperationContext extends RenameOperationContext
{
    /** The parent DN */
    private LdapDN parent;


    /**
     * Creates a new instance of MoveAndRenameOperationContext.
     */
    public MoveAndRenameOperationContext( Registries registries )
    {
        super( registries );
    }


    /**
     * Creates a new instance of MoveAndRenameOperationContext.
     *
     * @param oldDn the original source entry DN to be moved and renamed
     * @param parent the new entry superior of the target after the move
     * @param newRdn the new rdn to use for the target once renamed
     * @param delOldRdn true if the old rdn value is deleted, false otherwise
     */
    public MoveAndRenameOperationContext( Registries registries, LdapDN oldDn, LdapDN parent, Rdn newRdn, boolean delOldRdn )
    {
        super( registries, oldDn, newRdn, delOldRdn );
        this.parent = parent;
    }


    /**
     *  @return The parent DN
     */
    public LdapDN getParent()
    {
        return parent;
    }


    /**
     * Set the parent DN
     *
     * @param parent The parent
     */
    public void setParent( LdapDN parent )
    {
        this.parent = parent;
    }


    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "ReplaceContext for old DN '" + getDn().getUpName() + "'" +
        ", parent '" + parent + "'";
    }
}
