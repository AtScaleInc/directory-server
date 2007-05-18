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
package org.apache.directory.server.ntp;


import org.apache.directory.server.protocol.shared.ServiceConfiguration;


/**
 * Contains the configuration parameters for the NTP protocol provider.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NtpConfiguration extends ServiceConfiguration
{
    private static final long serialVersionUID = 2961795205765175775L;

    /** The default IP port. */
    private static final int IP_PORT_DEFAULT = 123;

    /** The default service pid. */
    private static final String SERVICE_PID_DEFAULT = "org.apache.directory.server.ntp";

    /** The default service name. */
    private static final String SERVICE_NAME_DEFAULT = "ApacheDS NTP Service";


    /**
     * Creates a new instance of NtpConfiguration.
     */
    public NtpConfiguration()
    {
        super.setIpPort( IP_PORT_DEFAULT );
        super.setServicePid( SERVICE_PID_DEFAULT );
        super.setServiceName( SERVICE_NAME_DEFAULT );
    }
}
