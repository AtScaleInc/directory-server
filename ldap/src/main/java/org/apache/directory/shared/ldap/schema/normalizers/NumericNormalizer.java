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
package org.apache.directory.shared.ldap.schema.normalizers;


import java.io.IOException;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.PrepareString;


/**
 * Normalize Numeric Strings
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class NumericNormalizer implements Normalizer
{
    // The serial UID
   static final long serialVersionUID = 1L;

   
   /**
    * {@inheritDoc}
    */
   public Value<?> normalize( Value<?> value ) throws NamingException
   {
       try
       {
           String normalized = PrepareString.normalize( value.getString(),
               PrepareString.StringType.NUMERIC_STRING );
           
           return new ClientStringValue( normalized );
       }
       catch ( IOException ioe )
       {
           throw new NamingException( "Invalid value : " + value );
       }
   }


   /**
    * {@inheritDoc}
    */
   public String normalize( String value ) throws NamingException
   {
       try
       {
           return PrepareString.normalize( value,
               PrepareString.StringType.NUMERIC_STRING );
       }
       catch ( IOException ioe )
       {
           throw new NamingException( "Invalid value : " + value );
       }
   }
}