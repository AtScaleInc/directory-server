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


import org.apache.ldap.common.exception.LdapException;
import org.apache.ldap.common.exception.LdapNamingException;
import org.apache.ldap.common.message.ResultCodeEnum;
import org.apache.ldap.server.jndi.invocation.Invocation;


/**
 * A {@link LdapNamingException} that wraps uncaught runtime exceptions thrown from {@link Interceptor}s.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class InterceptorException extends LdapNamingException
{
    private static final long serialVersionUID = 3258690996517746233L;

    /**
     * The Invocation the Interceptor failed on
     */
    private final Invocation invocation;

    /**
     * The Interceptor causing the failure
     */
    private final Interceptor interceptor;


    /**
     * Creates an InterceptorException without a message.
     *
     * @param interceptor the Interceptor causing the failure
     * @param invocation  the Invocation the Interceptor failed on
     */
    public InterceptorException( Interceptor interceptor, Invocation invocation )
    {
        super( ResultCodeEnum.OTHER );
        this.invocation = invocation;
        this.interceptor = interceptor;
    }


    /**
     * Creates an InterceptorException with a custom message.
     *
     * @param interceptor the Interceptor causing the failure
     * @param invocation  the Invocation the Interceptor failed on
     * @param explanation String explanation of why the Interceptor failed
     */
    public InterceptorException( Interceptor interceptor,
                                 Invocation invocation, String explanation )
    {
        super( explanation, ResultCodeEnum.OTHER );
        this.invocation = invocation;
        this.interceptor = interceptor;
    }


    /**
     * Creates an InterceptorException without a message.
     *
     * @param interceptor the Interceptor causing the failure
     * @param invocation  the Invocation the Interceptor failed on
     * @param rootCause   the root cause of this exception
     */
    public InterceptorException( Interceptor interceptor,
                                 Invocation invocation, Throwable rootCause )
    {
        this( interceptor, invocation );
        super.setRootCause( rootCause );
    }


    /**
     * Creates an InterceptorException without a message.
     *
     * @param interceptor the Interceptor causing the failure
     * @param invocation  the Invocation the Interceptor failed on
     * @param explanation String explanation of why the Interceptor failed
     * @param rootCause   the root cause of this exception
     */
    public InterceptorException( Interceptor interceptor,
                                 Invocation invocation,
                                 String explanation,
                                 Throwable rootCause )
    {
        this( interceptor, invocation, explanation );
        super.setRootCause( rootCause );
    }


    /**
     * Gets the invovation object this exception is associated with.
     *
     * @return the invovation object this exception is associated with
     */
    public Invocation getInvocation()
    {
        return invocation;
    }


    /**
     * Gets the interceptor this exception is associated with.
     *
     * @return the interceptor this exception is associated with
     */
    public Interceptor getInterceptor()
    {
        return interceptor;
    }


    /**
     * Will return the resultCode of the root cause if the root cause implements LdapException.
     *
     * @see org.apache.ldap.common.exception.LdapException#getResultCode()
     */
    public ResultCodeEnum getResultCode()
    {
        if ( getRootCause() != null && ( getRootCause() instanceof LdapException ) )
        {
            return ( ( LdapException ) getRootCause() ).getResultCode();
        }

        return super.getResultCode();
    }
}
