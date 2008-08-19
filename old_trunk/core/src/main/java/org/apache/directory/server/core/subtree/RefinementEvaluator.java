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
package org.apache.directory.server.core.subtree;


import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;

import javax.naming.NamingException;


/**
 * The top level evaluation node for a refinement.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class RefinementEvaluator
{
    /** Leaf Evaluator flyweight use for leaf filter assertions */
    private RefinementLeafEvaluator leafEvaluator;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    public RefinementEvaluator(RefinementLeafEvaluator leafEvaluator)
    {
        this.leafEvaluator = leafEvaluator;
    }


    public boolean evaluate( ExprNode node, EntryAttribute objectClasses ) throws NamingException
    {
        if ( node == null )
        {
            throw new IllegalArgumentException( "node cannot be null" );
        }
        
        if ( objectClasses == null )
        {
            throw new IllegalArgumentException( "objectClasses cannot be null" );
        }
        
        if ( !((ServerAttribute)objectClasses).instanceOf( SchemaConstants.OBJECT_CLASS_AT ) )
        {
            throw new IllegalArgumentException( "Attribute objectClasses should be of id 'objectClass'" );
        }
        
        if ( node.isLeaf() )
        {
            return leafEvaluator.evaluate( ( SimpleNode ) node, objectClasses );
        }

        BranchNode bnode = ( BranchNode ) node;

        if ( node instanceof OrNode )
        {
            for ( ExprNode child:bnode.getChildren() )
            {
                if ( evaluate( child, objectClasses ) )
                {
                    return true;
                }
            }

            return false;
        }
        else if ( node instanceof AndNode )
        {
            for ( ExprNode child:bnode.getChildren() )
            {
                if ( !evaluate( child, objectClasses ) )
                {
                    return false;
                }
            }

            return true;
            
        }
        else if ( node instanceof NotNode )
        {
            if ( null != bnode.getFirstChild() )
            {
                return !evaluate( bnode.getFirstChild(), objectClasses );
            }

            throw new IllegalArgumentException( "Negation has no child: " + node );
            
        }
        else
        {
            throw new IllegalArgumentException( "Unrecognized branch node operator: " + bnode );
        }
    }
}
