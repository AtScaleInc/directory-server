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


import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.util.StringTools;


/**
 * 
 * Normalizer for boolean values.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class BooleanNormalizer implements Normalizer
{
    private static final long serialVersionUID = 1L;


    public Object normalize( Object value ) throws NamingException
    {
        if ( value == null )
        {
            return null;
        }

        String strValue;

        if ( value instanceof byte[] )
        {
            strValue = StringTools.utf8ToString( ( byte[] ) value );
        }
        else
        {
            strValue = ( String ) value;
        }

        return strValue.trim().toUpperCase();
    }

}
