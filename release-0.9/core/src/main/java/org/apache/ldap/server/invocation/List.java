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
package org.apache.ldap.server.invocation;


import org.apache.ldap.server.BackingStore;

import javax.naming.Name;
import javax.naming.NamingException;


/**
 * Represents an {@link org.apache.ldap.server.invocation.Invocation} on {@link BackingStore#list(Name)}.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class List extends Invocation
{
    private static final long serialVersionUID = 3761971574936776755L;

    private Name baseName;


    public List( Name baseName )
    {
        if ( baseName == null )
        {
            throw new NullPointerException( "baseName" );
        }

        this.baseName = baseName;
    }


    public Name getBaseName()
    {
        return baseName;
    }


    protected Object doExecute( BackingStore store ) throws NamingException
    {
        return store.list( baseName );
    }


    public void setBaseName( Name baseName )
    {
        this.baseName = baseName;
    }
}
