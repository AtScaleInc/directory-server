/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.schema.bootstrap;


import org.apache.ldap.common.schema.*;
import org.apache.ldap.server.jndi.ServerDirObjectFactory;
import org.apache.ldap.server.jndi.ServerDirStateFactory;
import org.apache.ldap.server.schema.*;

import javax.naming.NamingException;
import java.util.*;


/**
 * Class which handles bootstrap schema class file loading.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class BootstrapSchemaLoader
{
    /** stores schemas of producers for callback access */
    private ThreadLocal schemas;
    /** stores registries associated with producers for callback access */
    private ThreadLocal registries;
    /** the callback that just calls register() */
    private final ProducerCallback cb = new ProducerCallback()
    {
        public void schemaObjectProduced( BootstrapProducer producer,
                                          String registryKey,
                                          Object schemaObject )
            throws NamingException
        {
            register( producer.getType(), registryKey, schemaObject );
        }
    };


    /**
     * Creates a BootstrapSchema loader.
     */
    public BootstrapSchemaLoader()
    {
        schemas = new ThreadLocal();
        registries = new ThreadLocal();
    }



    /**
     * Loads a set of schemas by loading and running all producers for each
     * dependent schema first.
     *
     * @param schemaClasses the full qualified class names of the schema classes
     * @param registries the registries to fill with producer created objects
     * @throws NamingException if there are any failures during this process
     */
    public final void load( String[] schemaClasses, BootstrapRegistries registries )
        throws NamingException
    {
        BootstrapSchema[] schemas = new BootstrapSchema[schemaClasses.length];
        HashMap loaded = new HashMap();
        HashMap notLoaded = new HashMap();


        for ( int ii = 0; ii < schemas.length; ii++ )
        {
            try
            {
                Class schemaClass = Class.forName( schemaClasses[ii] );
                schemas[ii] = ( BootstrapSchema ) schemaClass.newInstance();
                notLoaded.put( schemas[ii].getSchemaName(), schemas[ii] );
            }
            catch ( Exception e )
            {
                String msg = "problem loading/creating " + schemaClasses[ii];
                NamingException ne = new NamingException( msg );
                ne.setRootCause( e );
                throw ne;
            }
        }

        // kick it off by loading system which will never depend on anything
        BootstrapSchema schema = ( BootstrapSchema ) notLoaded.get( "system" );
        load( schema, registries );
        notLoaded.remove( "system" );
        loaded.put( schema.getSchemaName(), schema );

        Iterator list = notLoaded.values().iterator();
        while ( list.hasNext() )
        {
            schema = ( BootstrapSchema ) list.next();
            loadDepsFirst( new Stack(), notLoaded, schema, registries );
            list = notLoaded.values().iterator();
        }
    }


    /**
     * Recursive method which loads schema's with their dependent schemas first
     * and tracks what schemas it has seen so the recursion does not go out of
     * control with depenency cycle detection.
     *
     * @param beenthere stack of schema names we have visited and have yet to load
     * @param notLoaded hash of schemas keyed by name which have yet to be loaded
     * @param schema the current schema we are attempting to load
     * @param registries the set of registries to use while loading
     * @throws NamingException if there is a cycle detected and/or another
     * failure results while loading, producing and or registering schema objects
     */
    public final void loadDepsFirst( Stack beenthere, HashMap notLoaded,
                                     BootstrapSchema schema,
                                     BootstrapRegistries registries )
        throws NamingException
    {
        beenthere.push( schema.getSchemaName() );
        String[] deps = schema.getDependencies();

        // if no deps then load this guy and return
        if ( deps == null || deps.length == 0 )
        {
            load( schema, registries );
            notLoaded.remove( schema.getSchemaName() );
            beenthere.pop();
            return;
        }

        /*
         * We got deps and need to load them before this schema.  We go through
         * all deps loading them with their deps first if they have not been
         * loaded.
         */
        for ( int ii = 0; ii < deps.length; ii++ )
        {
            if ( ! notLoaded.containsKey( deps[ii] ) )
            {
                continue;
            }

            BootstrapSchema dep = ( BootstrapSchema ) notLoaded.get( deps[ii] );

            if ( beenthere.contains( dep.getSchemaName() ) )
            {
                // push again so we show the cycle in output
                beenthere.push( dep.getSchemaName() );
                throw new NamingException( "schema dependency cycle detected: "
                    + beenthere );
            }

            loadDepsFirst( beenthere, notLoaded, dep, registries );
        }

        // We have loaded all our deps so we can load this schema
        load( schema, registries );
        notLoaded.remove( schema.getSchemaName() );
        beenthere.pop();
    }


    /**
     * Loads a schema by loading and running all producers for te schema.
     *
     * @param schema the schema to load
     * @param registries the registries to fill with producer created objects
     * @throws NamingException if there are any failures during this process
     */
    public final void load( BootstrapSchema schema, BootstrapRegistries registries )
        throws NamingException
    {
        this.registries.set( registries );
        this.schemas.set( schema );

        List producers = ProducerTypeEnum.list();
        for ( int ii = 0; ii < producers.size(); ii++ )
        {
            ProducerTypeEnum producerType = ( ProducerTypeEnum ) producers.get( ii );
            BootstrapProducer producer = getProducer( schema, producerType.getName() );
            producer.produce( registries, cb );
        }
    }


    // ------------------------------------------------------------------------
    // Utility Methods
    // ------------------------------------------------------------------------


    /**
     * Registers objects
     *
     * @param type the type of the producer which determines the type of object produced
     * @param id the primary key identifying the created object in a registry
     * @param schemaObject the object being registered
     * @throws NamingException if there are problems when registering the object
     * in any of the registries
     */
    private void register( ProducerTypeEnum type, String id,
                           Object schemaObject ) throws NamingException
    {
        BootstrapSchema schema = ( BootstrapSchema ) this.schemas.get();
        BootstrapRegistries registries = ( BootstrapRegistries ) this.registries.get();

        switch( type.getValue() )
        {
            case( ProducerTypeEnum.NORMALIZER_PRODUCER_VAL ):
                Normalizer normalizer = ( Normalizer ) schemaObject;
                NormalizerRegistry normalizerRegistry;
                normalizerRegistry = registries.getNormalizerRegistry();
                normalizerRegistry.register( schema.getSchemaName(), id, normalizer );
                break;
            case( ProducerTypeEnum.COMPARATOR_PRODUCER_VAL ):
                Comparator comparator = ( Comparator ) schemaObject;
                ComparatorRegistry comparatorRegistry;
                comparatorRegistry = registries.getComparatorRegistry();
                comparatorRegistry.register( schema.getSchemaName(), id, comparator );
                break;
            case( ProducerTypeEnum.SYNTAX_CHECKER_PRODUCER_VAL ):
                SyntaxChecker syntaxChecker = ( SyntaxChecker ) schemaObject;
                SyntaxCheckerRegistry syntaxCheckerRegistry;
                syntaxCheckerRegistry = registries.getSyntaxCheckerRegistry();
                syntaxCheckerRegistry.register( schema.getSchemaName(), id, syntaxChecker );
                break;
            case( ProducerTypeEnum.SYNTAX_PRODUCER_VAL ):
                Syntax syntax = ( Syntax ) schemaObject;
                SyntaxRegistry syntaxRegistry = registries.getSyntaxRegistry();
                syntaxRegistry.register( schema.getSchemaName(), syntax );
                break;
            case( ProducerTypeEnum.MATCHING_RULE_PRODUCER_VAL ):
                MatchingRule matchingRule = ( MatchingRule ) schemaObject;
                MatchingRuleRegistry matchingRuleRegistry;
                matchingRuleRegistry = registries.getMatchingRuleRegistry();
                matchingRuleRegistry.register( schema.getSchemaName(), matchingRule );
                break;
            case( ProducerTypeEnum.ATTRIBUTE_TYPE_PRODUCER_VAL ):
                AttributeType attributeType = ( AttributeType ) schemaObject;
                AttributeTypeRegistry attributeTypeRegistry;
                attributeTypeRegistry = registries.getAttributeTypeRegistry();
                attributeTypeRegistry.register( schema.getSchemaName(), attributeType );
                break;
            case( ProducerTypeEnum.OBJECT_CLASS_PRODUCER_VAL ):
                ObjectClass objectClass = ( ObjectClass ) schemaObject;
                ObjectClassRegistry objectClassRegistry;
                objectClassRegistry = registries.getObjectClassRegistry();
                objectClassRegistry.register( schema.getSchemaName(), objectClass );
                break;
            case( ProducerTypeEnum.MATCHING_RULE_USE_PRODUCER_VAL ):
                MatchingRuleUse matchingRuleUse = ( MatchingRuleUse ) schemaObject;
                MatchingRuleUseRegistry matchingRuleUseRegistry;
                matchingRuleUseRegistry = registries.getMatchingRuleUseRegistry();
                matchingRuleUseRegistry.register( schema.getSchemaName(), matchingRuleUse );
                break;
            case( ProducerTypeEnum.DIT_CONTENT_RULE_PRODUCER_VAL ):
                DITContentRule ditContentRule = ( DITContentRule ) schemaObject;
                DITContentRuleRegistry ditContentRuleRegistry;
                ditContentRuleRegistry = registries.getDitContentRuleRegistry();
                ditContentRuleRegistry.register( schema.getSchemaName(), ditContentRule );
                break;
            case( ProducerTypeEnum.NAME_FORM_PRODUCER_VAL ):
                NameForm nameForm = ( NameForm ) schemaObject;
                NameFormRegistry nameFormRegistry;
                nameFormRegistry = registries.getNameFormRegistry();
                nameFormRegistry.register( schema.getSchemaName(), nameForm );
                break;
            case( ProducerTypeEnum.DIT_STRUCTURE_RULE_PRODUCER_VAL ):
                DITStructureRule ditStructureRule = ( DITStructureRule ) schemaObject;
                DITStructureRuleRegistry ditStructureRuleRegistry;
                ditStructureRuleRegistry = registries.getDitStructureRuleRegistry();
                ditStructureRuleRegistry.register( schema.getSchemaName(), ditStructureRule );
                break;
            case( ProducerTypeEnum.STATE_FACTORY_PRODUCER_VAL ):
                ServerDirStateFactory stateFactory = ( ServerDirStateFactory ) schemaObject;
                StateFactoryRegistry stateFactoryRegistry;
                stateFactoryRegistry = registries.getStateFactoryRegistry();
                stateFactoryRegistry.register( stateFactory );
                break;
            case( ProducerTypeEnum.OBJECT_FACTORY_PRODUCER_VAL ):
                ServerDirObjectFactory objectFactory = ( ServerDirObjectFactory ) schemaObject;
                ObjectFactoryRegistry objectFactoryRegistry;
                objectFactoryRegistry = registries.getObjectFactoryRegistry();
                objectFactoryRegistry.register( objectFactory );
                break;
            default:
                throw new IllegalStateException( "ProducerTypeEnum is broke!" );
        }
    }


    /**
     * Attempts first to try to load the target class for the Producer,
     * then tries for the default if the target load fails.
     *
     * @param schema the bootstrap schema
     * @param producerBase the producer's base name
     * @throws NamingException if there are failures loading classes
     */
    private BootstrapProducer getProducer( BootstrapSchema schema, String producerBase )
        throws NamingException
    {
        Class clazz = null;
        boolean failedTargetLoad = false;
        String defaultClassName;
        String targetClassName = schema.getBaseClassName() + producerBase;

        try
        {
            clazz = Class.forName( targetClassName );
        }
        catch ( ClassNotFoundException e )
        {
            failedTargetLoad = true;
            // @todo instead of trace report target class load failure to monitor
            e.printStackTrace();
        }

        if ( failedTargetLoad )
        {
            defaultClassName = schema.getDefaultBaseClassName() + producerBase;

            try
            {
                clazz = Class.forName( defaultClassName );
            }
            catch ( ClassNotFoundException e )
            {
                NamingException ne = new NamingException( "Failed to load " +
                    producerBase + " for " + schema.getSchemaName()
                    + " schema using following classes: "  + targetClassName
                    + ", " + defaultClassName );
                ne.setRootCause( e );
                throw ne;
            }
        }

        try
        {
            return ( BootstrapProducer ) clazz.newInstance();
        }
        catch ( IllegalAccessException e )
        {
            NamingException ne = new NamingException( "Failed to create " + clazz );
            ne.setRootCause( e );
            throw ne;
        }
        catch ( InstantiationException e )
        {
            NamingException ne = new NamingException( "Failed to create " + clazz );
            ne.setRootCause( e );
            throw ne;
        }
    }
}
