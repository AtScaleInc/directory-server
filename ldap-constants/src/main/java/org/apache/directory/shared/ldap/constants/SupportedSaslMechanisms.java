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
package org.apache.directory.shared.ldap.constants;

/**
 * Contains constants used for populating the supportedSASLMechanisms 
 * in the RootDSE.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface SupportedSaslMechanisms
{
    String CRAM_MD5 = "CRAM-MD5";
    String DIGEST_MD5 = "DIGEST-MD5";
    String GSSAPI = "GSSAPI";
    String SIMPLE = "SIMPLE";

    /** Not a SASL JDK supported mechanism */
    String NTLM = "NTLM";
    /** Not a SASL JDK supported mechanism */
    String GSS_SPNEGO = "GSS-SPNEGO";
}
