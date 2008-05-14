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

package org.apache.directory.shared.ldap.ldif;

import javax.naming.ldap.Control;

import org.apache.directory.shared.asn1.primitives.OID;
import org.apache.directory.shared.ldap.util.StringTools;

/**
 * The LdifControl class stores a control defined for an entry found in a ldif
 * file.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class LdifControl implements Control
{
    private static final long serialVersionUID = 1L;

    /** The control OID */
    private OID oid;

    /** The control criticality */
    private boolean criticality;

    /** The control BER encoded value */
    private byte[] value;

    /**
     * Create a new Control
     * 
     * @param oid
     *            OID of the created control
     */
    public LdifControl( OID oid )
    {
        this.oid = oid;
        criticality = false;
        value = null;
    }

    /**
     * Returns the criticality of the current control
     * @return <code>true</code> if the control is critical
     */
    public boolean isCritical()
    {
        return criticality;
    }

    /**
     * Set the criticality
     * 
     * @param criticality
     *            True or false.
     */
    public void setCriticality( boolean criticality )
    {
        this.criticality = criticality;
    }

    /**
     * Return the control's OID as a String
     * @return The control's OID
     */
    public String getID()
    {
        return oid.toString();
    }

    /**
     * Set the control's OID
     * 
     * @param oid The control's OID
     */
    public void setOid( OID oid )
    {
        this.oid = oid;
    }

    /**
     * Returns the BER encoded value of the control
     * @return the BER encoded value
     */
    public byte[] getEncodedValue()
    {
        if ( value == null )
        {
            return null;
        }

        final byte[] copy = new byte[ value.length ];
        System.arraycopy( value, 0, copy, 0, value.length );
        return copy;
    }

    /**
     * Set the BER encoded value of the control
     * 
     * @param value
     *            BER encodec value
     */
    public void setValue( byte[] value )
    {
        if ( value != null )
        {
            this.value = new byte[ value.length ];
            System.arraycopy( value, 0, this.value, 0, value.length );
        } else {
            this.value = null;
        }
    }

    public String toString()
    {
        return "LdifControl : {" + oid.toString() + ", " + criticality + ", " + StringTools.dumpBytes( value ) + "}";
    }
}
