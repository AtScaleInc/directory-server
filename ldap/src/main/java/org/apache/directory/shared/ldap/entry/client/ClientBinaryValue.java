/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.shared.ldap.entry.client;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.entry.AbstractValue;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.schema.ByteArrayComparator;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A server side schema aware wrapper around a binary attribute value.
 * This value wrapper uses schema information to syntax check values,
 * and to compare them for equality and ordering.  It caches results
 * and invalidates them when the wrapped value changes.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ClientBinaryValue extends AbstractValue<byte[]>
{
    /** Used for serialization */
    private static final long serialVersionUID = 2L;
    
    /** logger for reporting errors that might not be handled properly upstream */
    private static final Logger LOG = LoggerFactory.getLogger( ClientBinaryValue.class );


    /**
     * Creates a ServerBinaryValue without an initial wrapped value.
     *
     * @param attributeType the schema type associated with this ServerBinaryValue
     */
    public ClientBinaryValue()
    {
        wrapped = null;
        normalized = false;
        valid = null;
        normalizedValue = null;
    }


    /**
     * Creates a ServerBinaryValue with an initial wrapped binary value.
     *
     * @param attributeType the schema type associated with this ServerBinaryValue
     * @param wrapped the binary value to wrap which may be null, or a zero length byte array
     */
    public ClientBinaryValue( byte[] wrapped )
    {
        if ( wrapped != null )
        {
            this.wrapped = new byte[ wrapped.length ];
            System.arraycopy( wrapped, 0, this.wrapped, 0, wrapped.length );
        }
        else
        {
            this.wrapped = null;
        }
        
        normalized = false;
        valid = null;
        normalizedValue = null;
    }


    // -----------------------------------------------------------------------
    // Value<String> Methods
    // -----------------------------------------------------------------------
    /**
     * Reset the value
     */
    public void clear()
    {
        wrapped = null;
        normalized = false;
        normalizedValue = null;
        valid = null;
    }




    /*
     * Sets the wrapped binary value.  Has the side effect of setting the
     * normalizedValue and the valid flags to null if the wrapped value is
     * different than what is already set.  These cached values must be
     * recomputed to be correct with different values.
     *
     * @see ServerValue#set(Object)
     */
    public final void set( byte[] wrapped )
    {
        // Why should we invalidate the normalized value if it's we're setting the
        // wrapper to it's current value?
        byte[] value = getReference();
        
        if ( value != null )
        {
            if ( Arrays.equals( wrapped, value ) )
            {
                return;
            }
        }

        normalizedValue = null;
        normalized = false;
        valid = null;
        
        if ( wrapped == null )
        {
            this.wrapped = null;
        }
        else
        {
            this.wrapped = new byte[ wrapped.length ];
            System.arraycopy( wrapped, 0, this.wrapped, 0, wrapped.length );
        }
    }


    // -----------------------------------------------------------------------
    // ServerValue<String> Methods
    // -----------------------------------------------------------------------
    /**
     * Gets a direct reference to the normalized representation for the
     * wrapped value of this ServerValue wrapper. Implementations will most
     * likely leverage the attributeType this value is associated with to
     * determine how to properly normalize the wrapped value.
     *
     * @return the normalized version of the wrapped value
     * @throws NamingException if schema entity resolution fails or normalization fails
     */
    public byte[] getNormalizedValueCopy()
    {
        if ( normalizedValue == null )
        {
            return null;
        }

        byte[] copy = new byte[ normalizedValue.length ];
        System.arraycopy( normalizedValue, 0, copy, 0, normalizedValue.length );
        return copy;
    }


    /**
     * Normalize the value. For a client String value, applies the given normalizer.
     * 
     * It supposes that the client has access to the schema in order to select the
     * appropriate normalizer.
     * 
     * @param Normalizer The normalizer to apply to the value
     * @exception NamingException If the value cannot be normalized
     */
    public final void normalize( Normalizer normalizer ) throws NamingException
    {
        if ( normalizer != null )
        {
            if ( wrapped == null )
            {
                normalized = true;
            }
            else
            {
                normalizedValue = (byte[])normalizer.normalize( wrapped );
                normalized = true;
            }
        }
    }

    
    /**
     *
     * @see ServerValue#compareTo(ServerValue)
     * @throws IllegalStateException on failures to extract the comparator, or the
     * normalizers needed to perform the required comparisons based on the schema
     */
    public int compareTo( Value<byte[]> value )
    {
        if ( isNull() )
        {
            if ( ( value == null ) || value.isNull() )
            {
                return 0;
            }
            else
            {
                return -1;
            }
        }
        else
        {
            if ( ( value == null ) || value.isNull() ) 
            {
                return 1;
            }
        }

        if ( value instanceof ClientBinaryValue )
        {
            ClientBinaryValue binaryValue = ( ClientBinaryValue ) value;

            return ByteArrayComparator.INSTANCE.compare( getNormalizedValue(), binaryValue.getNormalizedValue() );
        }
        
        String message = "I don't really know how to compare anything other " +
            "than ServerBinaryValues at this point in time."; 
        LOG.error( message );
        throw new NotImplementedException( message );
    }


    // -----------------------------------------------------------------------
    // Object Methods
    // -----------------------------------------------------------------------


    /**
     * @see Object#hashCode()
     * @throws IllegalStateException on failures to extract the comparator, or the
     * normalizers needed to perform the required comparisons based on the schema
     */
    public int hashCode()
    {
        // return zero if the value is null so only one null value can be
        // stored in an attribute - the string version does the same
        if ( isNull() )
        {
            return 0;
        }

        return Arrays.hashCode( getNormalizedValueReference() );
    }


    /**
     * Checks to see if this ServerBinaryValue equals the supplied object.
     *
     * This equals implementation overrides the BinaryValue implementation which
     * is not schema aware.
     * @throws IllegalStateException on failures to extract the comparator, or the
     * normalizers needed to perform the required comparisons based on the schema
     */
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        
        if ( ! ( obj instanceof ClientBinaryValue ) )
        {
            return false;
        }

        ClientBinaryValue other = ( ClientBinaryValue ) obj;
        
        if ( isNull() )
        {
            return other.isNull();
        }

        // now unlike regular values we have to compare the normalized values
        return Arrays.equals( getNormalizedValueReference(), other.getNormalizedValueReference() );
    }


    // -----------------------------------------------------------------------
    // Private Helper Methods (might be put into abstract base class)
    // -----------------------------------------------------------------------
    /**
     * @return a copy of the current value
     */
    public ClientBinaryValue clone()
    {
        ClientBinaryValue clone = (ClientBinaryValue)super.clone();
        
        if ( normalizedValue != null )
        {
            clone.normalizedValue = new byte[ normalizedValue.length ];
            System.arraycopy( normalizedValue, 0, clone.normalizedValue, 0, normalizedValue.length );
        }
        
        if ( wrapped != null )
        {
            clone.wrapped = new byte[ wrapped.length ];
            System.arraycopy( wrapped, 0, clone.wrapped, 0, wrapped.length );
        }
        
        return clone;
    }


    /**
     * Gets a copy of the binary value.
     *
     * @return a copy of the binary value
     */
    public byte[] getCopy()
    {
        if ( wrapped == null )
        {
            return null;
        }

        
        final byte[] copy = new byte[ wrapped.length ];
        System.arraycopy( wrapped, 0, copy, 0, wrapped.length );
        return copy;
    }
    
    
    /**
     * Tells if the current value is Binary or String
     * 
     * @return <code>true</code> if the value is Binary, <code>false</code> otherwise
     */
    public boolean isBinary()
    {
        return true;
    }


    /**
     * @see Externalizable#readExternal(ObjectInput)
     */
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        // TODO implement this method
        return;
    }

    
    /**
     * @see Externalizable#writeExternal(ObjectOutput)
     */
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        // TODO Implement this method
    }
    
    
    /**
     * Dumps binary in hex with label.
     *
     * @see Object#toString()
     */
    public String toString()
    {
        if ( wrapped == null )
        {
            return "null";
        }
        else if ( wrapped.length > 16 )
        {
            // Just dump the first 16 bytes...
            byte[] copy = new byte[16];
            
            System.arraycopy( wrapped, 0, copy, 0, 16 );
            
            return "'" + StringTools.dumpBytes( copy ) + "...'";
        }
        else
        {
            return "'" + StringTools.dumpBytes( wrapped ) + "'";
        }
    }
}