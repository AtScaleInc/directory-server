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
package org.apache.ldap.server.protocol;


import org.apache.apseda.listener.ClientKey;
import org.apache.apseda.protocol.AbstractSingleReplyHandler;

import org.apache.ldap.common.NotImplementedException;
import org.apache.apseda.listener.ClientKey;


/**
 * A single reply handler for {@link org.apache.ldap.common.message.ExtendedRequest}s.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ExtendedHandler extends AbstractSingleReplyHandler
{
    /**
     * @see org.apache.apseda.protocol.SingleReplyHandler#handle(ClientKey,Object)
     */
    public Object handle( ClientKey key, Object request )
    {
        throw new NotImplementedException( "handle in org.apache.ldap.server.protocol.ExtendedHandler not implemented!" );
    }
}
