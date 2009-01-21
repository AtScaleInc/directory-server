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

package org.apache.directory.shared.ldap.schema.syntaxes;


/**
 * An ApacheDS specific schema description. 
 * It contains a full qualified class name and optional 
 * the BASE64 encoded bytecode of the class.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public abstract class AbstractAdsSchemaDescription extends AbstractSchemaDescription
{
    /** The Full Qualified Class Name */
    private String fqcn;

    /** The base64 encoded bytecode for this schema */
    private String bytecode;


    protected AbstractAdsSchemaDescription()
    {
        fqcn = "";
        bytecode = null;
    }


    public String getBytecode()
    {
        return bytecode;
    }


    public void setBytecode( String bytecode )
    {
        this.bytecode = bytecode;
    }


    public String getFqcn()
    {
        return fqcn;
    }


    public void setFqcn( String fqcn )
    {
        this.fqcn = fqcn;
    }

}
