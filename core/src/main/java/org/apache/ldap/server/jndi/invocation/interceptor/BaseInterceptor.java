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
package org.apache.ldap.server.jndi.invocation.interceptor;


import org.apache.ldap.server.auth.LdapPrincipal;
import org.apache.ldap.server.jndi.ServerContext;
import org.apache.ldap.server.jndi.invocation.*;

import javax.naming.NamingException;


/**
 * A easy-to-use implementation of {@link Interceptor} that demultiplexes invocations using method signature
 * overloading.
 * <p/>
 * This {@link Interceptor} forwards received process requests to an appropriate <code>process(NextInterceptor,
 * <em>ConcreteInvocation</em>)</code> methods.  Users can override any <code>process(..)</code> methods that
 * corresponds to {@link Invocation} types that he or she wants to filter.
 * <p/>
 * For example, if user wants to filter {@link Add} invocation:
 * <pre>
 * public class MyInterceptor extends BaseInterceptor
 * {
 *     protected void process( NextInterceptor nextInterceptor, Add invocation )
 *     {
 *         nextInterceptor.process( invocation );
 *         System.out.println( "Item added!" );
 *     }
 * }
 * </pre>
 * <code>BaseInterceptor</code> handles all long and tedious if-elseif blocks behind the scene.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public abstract class BaseInterceptor implements Interceptor
{
    /**
     * Gets the call's current context's Principal.
     *
     * @return the principal making the call
     */
    public static LdapPrincipal getPrincipal( Invocation call )
    {
        ServerContext ctx = ( ServerContext ) call.getContextStack().peek();
        return ctx.getPrincipal();
    }


    protected BaseInterceptor()
    {
    }

    // ------------------------------------------------------------------------
    // Interceptor's Invoke Method
    // ------------------------------------------------------------------------

    /**
     * Uses a switch on the invocation method type to call the respective member
     * analog method that does the work of the Interceptor for that Invocation method.
     */
    public void process( NextInterceptor nextInterceptor, Invocation call )
            throws NamingException
    {
        if ( call instanceof Add )
        {
            process( nextInterceptor, ( Add ) call );
        }
        else if ( call instanceof Delete )
        {
            process( nextInterceptor, ( Delete ) call );
        }
        else if ( call instanceof GetMatchedDN )
        {
            process( nextInterceptor, ( GetMatchedDN ) call );
        }
        else if ( call instanceof GetSuffix )
        {
            process( nextInterceptor, ( GetSuffix ) call );
        }
        else if ( call instanceof HasEntry )
        {
            process( nextInterceptor, ( HasEntry ) call );
        }
        else if ( call instanceof IsSuffix )
        {
            process( nextInterceptor, ( IsSuffix ) call );
        }
        else if ( call instanceof List )
        {
            process( nextInterceptor, ( List ) call );
        }
        else if ( call instanceof ListSuffixes )
        {
            process( nextInterceptor, ( ListSuffixes ) call );
        }
        else if ( call instanceof Lookup )
        {
            process( nextInterceptor, ( Lookup ) call );
        }
        else if ( call instanceof LookupWithAttrIds )
        {
            process( nextInterceptor, ( LookupWithAttrIds ) call );
        }
        else if ( call instanceof Modify )
        {
            process( nextInterceptor, ( Modify ) call );
        }
        else if ( call instanceof ModifyMany )
        {
            process( nextInterceptor, ( ModifyMany ) call );
        }
        else if ( call instanceof ModifyRN )
        {
            process( nextInterceptor, ( ModifyRN ) call );
        }
        else if ( call instanceof Move )
        {
            process( nextInterceptor, ( Move ) call );
        }
        else if ( call instanceof MoveAndModifyRN )
        {
            process( nextInterceptor, ( MoveAndModifyRN ) call );
        }
        else if ( call instanceof Search )
        {
            process( nextInterceptor, ( Search ) call );
        }
        else
        {
            throw new IllegalArgumentException( "Unknown call type: " + call.getClass() );
        }
    }

    // ------------------------------------------------------------------------
    // Invocation Analogs
    // ------------------------------------------------------------------------

    protected void process( NextInterceptor nextInterceptor, Add call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, Delete call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, GetMatchedDN call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, GetSuffix call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, HasEntry call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, IsSuffix call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, List call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, ListSuffixes call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, Lookup call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, LookupWithAttrIds call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, Modify call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, ModifyMany call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, ModifyRN call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, Move call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, MoveAndModifyRN call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }


    protected void process( NextInterceptor nextInterceptor, Search call )
            throws NamingException
    {
        nextInterceptor.process( call );
    }
}
