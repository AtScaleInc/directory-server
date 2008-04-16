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
package org.apache.directory.shared.ldap.filter;


/**
 * A assertion value node for Equality.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Revision: 519266 $
 */
public class EqualityNode extends SimpleNode
{
    /**
     * Creates a new Equality object.
     * 
     * @param attribute the attribute name
     * @param value the value to test for
     */
    public EqualityNode( String attribute, byte[] value )
    {
        super( attribute, value, AssertionType.EQUALITY );
    }


    /**
     * Creates a new Equality object.
     * 
     * @param attribute the attribute name
     * @param value the value to test for
     */
    public EqualityNode( String attribute, String value )
    {
        super( attribute, value, AssertionType.EQUALITY );
    }


    /**
     * Creates a new Equality object.
     * 
     * @param attribute the attribute name
     * @param value the value to test for
     */
    protected EqualityNode( String attribute, byte[] value, AssertionType assertionType )
    {
        super( attribute, value, assertionType );
    }


    /**
     * Creates a new Equality object.
     * 
     * @param attribute the attribute name
     * @param value the value to test for
     */
    protected EqualityNode( String attribute, String value, AssertionType assertionType )
    {
        super( attribute, value, assertionType );
    }


    /**
     * @see Object#hashCode()
     */
    public int hashCode()
    {
        return super.hashCode();
    }

    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
    	StringBuilder buf = new StringBuilder();
    	
        buf.append( '(' ).append( getAttribute() ).append( "=" ).append( value );

        buf.append( super.toString() );
        
        buf.append( ')' );
        
    	return buf.toString();
    }
}
