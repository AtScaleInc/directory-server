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

package org.apache.directory.server.kerberos.shared.store.operations;


import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.spi.DirObjectFactory;
import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.KerberosAttribute;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntryModifier;
import org.apache.directory.shared.ldap.constants.SchemaConstants;


/**
 * An ObjectFactory that resusitates objects from directory attributes.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class PrincipalObjectFactory implements DirObjectFactory
{
    public Object getObjectInstance( Object obj, Name name, Context nameCtx, Hashtable environment, Attributes attrs )
        throws Exception
    {
        if ( attrs == null || attrs.get( SchemaConstants.OBJECT_CLASS_AT ) == null
            || !attrs.get( SchemaConstants.OBJECT_CLASS_AT ).contains( "krb5KDCEntry" ) )
        {
            return null;
        }

        PrincipalStoreEntryModifier modifier = new PrincipalStoreEntryModifier();

        modifier.setUserId( ( String ) attrs.get( SchemaConstants.UID_AT ).get() );
        modifier.setCommonName( ( String ) attrs.get( SchemaConstants.CN_AT ).get() );

        KerberosPrincipal principal = new KerberosPrincipal( ( String ) attrs.get( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT ).get() );
        modifier.setPrincipal( principal );

        if ( attrs.get( KerberosAttribute.KRB5_KEY_AT ) != null )
        {
            Attribute krb5key = attrs.get( KerberosAttribute.KRB5_KEY_AT );
            try
            {
                Map<EncryptionType, EncryptionKey> keyMap = modifier.reconstituteKeyMap( krb5key );
                modifier.setKeyMap( keyMap );
            }
            catch ( IOException ioe )
            {
                throw new InvalidAttributeValueException( "Account Kerberos key attribute '" + KerberosAttribute.KRB5_KEY_AT
                    + "' contained an invalid value for krb5key." );
            }
        }

        modifier.setKeyVersionNumber( Integer.parseInt( ( String ) attrs.get( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT ).get() ) );

        return modifier.getEntry();
    }


    public Object getObjectInstance( Object obj, Name name, Context nameCtx, Hashtable environment ) throws Exception
    {
        throw new UnsupportedOperationException( "Attributes are required to add an entry." );
    }
}
