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
package org.apache.directory.shared.ldap.name;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class which serialize and deserialize a RDN
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class RdnSerializer
{
    /** The LoggerFactory used by this class */
    protected static final Logger LOG = LoggerFactory.getLogger( RdnSerializer.class );

    /**
     * Serialize a RDN instance
     * 
     * A RDN is composed of on to many ATAVs (AttributeType And Value).
     * We should write all those ATAVs sequencially, following the 
     * structure :
     * 
     * <li>nbAtavs</li> The number of ATAVs to write. Can't be 0.
     * <li>upName</li> The User provided RDN
     * <li>normName</li> The normalized RDN. It can be empty if the normalized
     * name equals the upName.
     * <li>atavs</li>
     * <p>
     * For each ATAV :<p>
     * <li>start</li> The position of this ATAV in the upName string
     * <li>length</li> The ATAV user provided length
     * <li>Call the ATAV write method</li> The ATAV itself
     *  
     */
    public static void serialize( Rdn rdn, ObjectOutput out ) throws IOException
    {
        out.writeInt( rdn.getNbAtavs() );
        out.writeUTF( rdn.getUpName() );
        out.writeUTF( rdn.getNormName() );
        out.writeInt( rdn.getStart() );
        out.writeInt( rdn.getLength() );
        
        switch ( rdn.getNbAtavs() )
        {
            case 0 :
                break;

            case 1 :
                AtavSerializer.serialize( rdn.getAtav(), out );
                break;
                
            default :
                for ( AttributeTypeAndValue atav:rdn )
                {
                    AtavSerializer.serialize( atav, out );
                }
            
                break;
        }
        
        out.flush();
    }
    
    
    /**
     * Deserialize a RDN instance
     * 
     * We read back the data to create a new RDB. The structure 
     * read is exposed in the {@link Rdn#writeExternal(ObjectOutput)} 
     * method<p>
     */
    public static Rdn deserialize( ObjectInput in ) throws IOException
    {
        // Read the ATAV number
        int nbAtavs = in.readInt();
        
        // Read the UPName
        String upName = in.readUTF();
        
        // Read the normName
        String normName = in.readUTF();
        
        if ( StringTools.isEmpty( normName ) )
        {
            normName = upName;
        }
        
        // Read the RDN's position and length
        int start = in.readInt();
        int length = in.readInt();
        
        // Now creates the RDN
        Rdn rdn = new Rdn( length, start, upName, normName );

        // Read through the Atavs
        switch ( nbAtavs )
        {
            case 0 :
                return rdn;
                
            case 1 :
                AttributeTypeAndValue atav = AtavSerializer.deserialize( in );
                
                rdn.addAttributeTypeAndValue( atav );

                return rdn;
                
            default :
                for ( int i = 0; i < nbAtavs; i++  )
                {
                    atav = AtavSerializer.deserialize( in );
                    rdn.addAttributeTypeAndValue( atav );
                }
            
                return rdn;
        }
    }
}
