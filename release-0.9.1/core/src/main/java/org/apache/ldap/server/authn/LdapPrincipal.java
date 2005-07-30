/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.authn;


import java.io.Serializable;
import java.security.Principal;

import javax.naming.Name;

import org.apache.ldap.common.name.LdapName;


/**
 * An alternative X500 user implementation that has access to the distinguished
 * name of the principal as well as the String representation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public final class LdapPrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = 3906650782395676720L;

    /** the normalized distinguished name of the principal */
    private final Name name;

    /** the no name anonymous user whose DN is the empty String */
    public static final LdapPrincipal ANONYMOUS = new LdapPrincipal();


    /**
     * Creates a new LDAP/X500 principal.  Keep this package friendly so only code
     * in the package can create a trusted principal.
     *
     * @param name the normalized distinguished name of the principal
     */
    LdapPrincipal( Name name )
    {
        this.name = name;
    }


    /**
     * Creates a principal for the no name anonymous user whose DN is the empty
     * String.
     */
    private LdapPrincipal()
    {
        this.name = new LdapName();
    }


    /**
     * Gets a cloned copy of the normalized distinguished name of this
     * principal as a JNDI {@link Name}.
     *
     * @return the normalized distinguished name of the principal as a JNDI {@link Name}
     */
    public Name getJndiName()
    {
        return ( Name ) name.clone();
    }


    /**
     * Returns the normalized distinguished name of the principal as a String.
     */
    public String getName()
    {
        return name.toString();
    }
    
    /**
     * Returns string representation of the normalized distinguished name
     * of this principal.
     */
    public String toString()
    {
        return name.toString();
    }
}
