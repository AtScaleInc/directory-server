/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.config.beans;


/**
 * A class used to store the HttpServer configuration.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class HttpServerBean extends AdsServerBean
{
    /** The server identifier */
    private String serverId;
    
    /** The port */
    private int systemPort;
    
    /** The configuration file */
    private String httpConfFile;

    /**
     * Create a new HttpServerBean instance
     */
    public HttpServerBean()
    {
        super();
        
        // Enabled by default
        setEnabled( true );
    }

    
    /**
     * @return the serverId
     */
    public String getServerId()
    {
        return serverId;
    }

    
    /**
     * @param serverId the serverId to set
     */
    public void setServerId( String serverId )
    {
        this.serverId = serverId;
    }

    
    /**
     * @return the systemPort
     */
    public int getSystemPort()
    {
        return systemPort;
    }

    
    /**
     * @param systemPort the systemPort to set
     */
    public void setSystemPort( int systemPort )
    {
        this.systemPort = systemPort;
    }

    
    /**
     * @return the httpConfFile
     */
    public String getHttpConfFile()
    {
        return httpConfFile;
    }

    
    /**
     * @param httpConfFile the httpConfFile to set
     */
    public void setHttpConfFile( String httpConfFile )
    {
        this.httpConfFile = httpConfFile;
    }
}
