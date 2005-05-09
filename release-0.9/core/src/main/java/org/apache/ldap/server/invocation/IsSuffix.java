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
 * Represents an {@link Invocation} on {@link BackingStore#isSuffix(Name)}.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class IsSuffix extends Invocation
{
    private static final long serialVersionUID = 3256723987429273908L;

    private Name name;


    public IsSuffix( Name name )
    {
        if ( name == null )
        {
            throw new NullPointerException( "name" );
        }

        this.name = name;
    }


    public Name getName()
    {
        return name;
    }


    protected Object doExecute( BackingStore store ) throws NamingException
    {
        return store.isSuffix( name ) ? Boolean.TRUE : Boolean.FALSE;
    }


    public void setName( Name name )
    {
        this.name = name;
    }
}
