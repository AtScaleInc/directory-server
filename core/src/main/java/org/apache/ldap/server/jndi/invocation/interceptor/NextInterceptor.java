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

import javax.naming.NamingException;

import org.apache.ldap.server.jndi.invocation.Invocation;

/**
 * Represents the next {@link Interceptor} in the interceptor chain.
 * {@link Interceptor}s should usually pass the control of current invocation
 * to the next {@link Interceptor} by calling
 * <code>nextInterceptor.process(invocation)</code>.
 * This method returns when the next interceptor's
 * {@link Interceptor#process(NextInterceptor, Invocation)} returns.
 * You can therefore implement pre-, post-, around- invocation
 * handler by how you place the statement. 
 * 
 * @author The Apache Directory Project (dev@directory.apache.org)
 * @author Trustin Lee (trustin@apache.org)
 * @version $Rev$, $Date$
 * 
 * @see Interceptor
 * @see InterceptorChain
 */
public interface NextInterceptor {
    /**
     * Passes the control of current invocation to the next
     * {@link Interceptor} in the {@link InterceptorChain}.
     * 
     * @param call
     * @throws NamingException
     */
    void process( Invocation call ) throws NamingException;
}
