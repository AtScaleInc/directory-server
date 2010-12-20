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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.directory.junit.tools.Concurrent;
import org.apache.directory.junit.tools.ConcurrentJunitRunner;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.loader.ldif.JarLdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.util.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Unit test cases for testing the evaluator for refinements.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(ConcurrentJunitRunner.class)
@Concurrent()
public class RefinementEvaluatorTest
{
    /** the SchemaManager instance */
    private static SchemaManager schemaManager;
    
    /** the refinement evaluator to test */
    private static RefinementEvaluator evaluator;

    /** The ObjectClass AttributeType */
    private static AttributeType OBJECT_CLASS_AT;

    /** The CN_AT AttributeType */
    private static AttributeType CN_AT;
    
    
    /**
     * Initializes the global registries.
     * @throws javax.naming.NamingException if there is a failure loading the schema
     */
    @BeforeClass 
    public static void init() throws Exception
    {
        JarLdifSchemaLoader loader = new JarLdifSchemaLoader();

        schemaManager = new DefaultSchemaManager( loader );

        boolean loaded = schemaManager.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + ExceptionUtils.printErrors(schemaManager.getErrors()) );
        }

        RefinementLeafEvaluator leafEvaluator = new RefinementLeafEvaluator( schemaManager );
        evaluator = new RefinementEvaluator( leafEvaluator );
        
        OBJECT_CLASS_AT = schemaManager.getAttributeType( "objectClass" );
        CN_AT = schemaManager.getAttributeTypeRegistry().lookup( "cn" );
    }


    /**
     * Sets evaluator and registries to null.
     */
    @AfterClass 
    public static void tearDown()
    {
        evaluator = null;
    }


    /**
     * Test cases for various bad combinations of arguments
     * @throws Exception if something goes wrong
     */
    @Test 
    public void testForBadArguments() throws Exception
    {
        try
        {
            assertFalse( evaluator.evaluate( null, new DefaultEntryAttribute( OBJECT_CLASS_AT ) ) );
            fail( "should never get here due to an IAE" );
        }
        catch ( IllegalArgumentException iae )
        {
        }

        try
        {
            assertFalse( evaluator.evaluate( new EqualityNode( (String)null, new StringValue( "" ) ), null ) );
            fail( "should never get here due to an IAE" );
        }
        catch ( IllegalArgumentException iae )
        {
        }

        try
        {
            assertFalse( evaluator.evaluate( new EqualityNode( (String)null, new StringValue( "" ) ), 
                new DefaultEntryAttribute( "cn", CN_AT ) ) );
            fail( "should never get here due to an IAE" );
        }
        catch ( IllegalArgumentException iae )
        {
        }
    }


    @Test 
    public void testMatchByName() throws Exception
    {
        EntryAttribute objectClasses = null;

        // positive test
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        assertTrue( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "person" ) ), objectClasses ) );

        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person", "blah" );
        assertTrue( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "person" ) ), objectClasses ) );

        // negative tests
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        assertFalse( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "blah" ) ), objectClasses ) );

        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "blah" );
        assertFalse( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "person" ) ), objectClasses ) );
    }


    @Test 
    public void testMatchByOID() throws Exception
    {
        EntryAttribute objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        
        // positive test
        assertTrue( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "2.5.6.6" ) ), objectClasses ) );

        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person", "blah" );
        assertTrue( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "2.5.6.6" ) ), objectClasses ) );

        // negative tests
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        assertFalse( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "2.5.6.5" ) ), objectClasses ) );

        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "blah" );
        assertFalse( evaluator.evaluate( new EqualityNode( OBJECT_CLASS_AT, new StringValue( "2.5.6.5" ) ), objectClasses ) );
    }


    @Test 
    public void testComplexOrRefinement() throws Exception
    {
        ExprNode refinement = null;
        EntryAttribute objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        String refStr = "(|(objectClass=person)(objectClass=organizationalUnit))";
        
        refinement = FilterParser.parse( schemaManager, refStr );

        assertTrue( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "organizationalUnit" );
        assertTrue( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "domain" );
        assertFalse( evaluator.evaluate( refinement, objectClasses ) );
    }


    @Test 
    public void testComplexAndRefinement() throws Exception
    {
        ExprNode refinement = null;
        EntryAttribute objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        objectClasses.add( "organizationalUnit" );
        String refStr = "(&(objectClass=person)(objectClass=organizationalUnit))";
        
        refinement = FilterParser.parse( schemaManager,refStr );

        assertTrue( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "organizationalUnit" );
        assertFalse( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        assertFalse( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "domain" );
        assertFalse( evaluator.evaluate( refinement, objectClasses ) );
    }


    @Test 
    public void testComplexNotRefinement() throws Exception
    {
        ExprNode refinement = null;
        EntryAttribute objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "person" );
        String refStr = "(!(objectClass=person))";

        refinement = FilterParser.parse( schemaManager, refStr );

        assertFalse( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "organizationalUnit" );
        assertTrue( evaluator.evaluate( refinement, objectClasses ) );
        
        objectClasses = new DefaultEntryAttribute( OBJECT_CLASS_AT, "domain" );
        assertTrue( evaluator.evaluate( refinement, objectClasses ) );

        try
        {
            assertFalse( evaluator.evaluate( new NotNode(), new DefaultEntryAttribute( OBJECT_CLASS_AT ) ) );
            fail( "should never get here due to an IAE" );
        }
        catch ( IllegalArgumentException iae )
        {
        }
    }
}
