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
package org.apache.directory.server.core.schema;


import java.text.ParseException;
import java.util.List;

import javax.naming.NamingException;

import org.apache.directory.server.constants.MetaSchemaConstants;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapOperationNotSupportedException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.DITContentRule;
import org.apache.directory.shared.ldap.schema.DITStructureRule;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.MatchingRuleUse;
import org.apache.directory.shared.ldap.schema.MutableSchemaObject;
import org.apache.directory.shared.ldap.schema.NameForm;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.Syntax;
import org.apache.directory.shared.ldap.schema.syntax.AbstractSchemaDescription;
import org.apache.directory.shared.ldap.schema.syntax.AttributeTypeDescription;
import org.apache.directory.shared.ldap.schema.syntax.ComparatorDescription;
import org.apache.directory.shared.ldap.schema.syntax.DITContentRuleDescription;
import org.apache.directory.shared.ldap.schema.syntax.DITStructureRuleDescription;
import org.apache.directory.shared.ldap.schema.syntax.LdapSyntaxDescription;
import org.apache.directory.shared.ldap.schema.syntax.MatchingRuleDescription;
import org.apache.directory.shared.ldap.schema.syntax.MatchingRuleUseDescription;
import org.apache.directory.shared.ldap.schema.syntax.NameFormDescription;
import org.apache.directory.shared.ldap.schema.syntax.NormalizerDescription;
import org.apache.directory.shared.ldap.schema.syntax.ObjectClassDescription;
import org.apache.directory.shared.ldap.schema.syntax.SyntaxCheckerDescription;
import org.apache.directory.shared.ldap.schema.syntax.parser.AttributeTypeDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.ComparatorDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.DITContentRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.DITStructureRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.LdapSyntaxDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.MatchingRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.MatchingRuleUseDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.NameFormDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.NormalizerDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.ObjectClassDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntax.parser.SyntaxCheckerDescriptionSchemaParser;


/**
 * Parses descriptions using a number of different parsers for schema descriptions.
 * Also checks to make sure some things are valid as it's parsing paramters of
 * certain entity types.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DescriptionParsers
{
    private static final String OTHER_SCHEMA = "other";
    private static final String[] EMPTY = new String[0];
    private static final Integer[] EMPTY_INT_ARRAY = new Integer[0];

    private static final ComparatorDescription[] EMPTY_COMPARATORS = new ComparatorDescription[0];
    private static final NormalizerDescription[] EMPTY_NORMALIZERS = new NormalizerDescription[0];
    private static final SyntaxCheckerDescription[] EMPTY_SYNTAX_CHECKERS = new SyntaxCheckerDescription[0];
    private static final Syntax[] EMPTY_SYNTAXES = new Syntax[0];
    private static final MatchingRule[] EMPTY_MATCHING_RULES = new MatchingRule[0];
    private static final AttributeType[] EMPTY_ATTRIBUTE_TYPES = new AttributeType[0];
    private static final ObjectClass[] EMPTY_OBJECT_CLASSES = new ObjectClass[0];
    private static final MatchingRuleUse[] EMPTY_MATCHING_RULE_USES = new MatchingRuleUse[0];
    private static final DITStructureRule[] EMPTY_DIT_STRUCTURE_RULES = new DITStructureRule[0];
    private static final DITContentRule[] EMPTY_DIT_CONTENT_RULES = new DITContentRule[0];
    private static final NameForm[] EMPTY_NAME_FORMS = new NameForm[0];

    private final Registries globalRegistries;
    
    private final ComparatorDescriptionSchemaParser comparatorParser =
        new ComparatorDescriptionSchemaParser();
    private final NormalizerDescriptionSchemaParser normalizerParser =
        new NormalizerDescriptionSchemaParser();
    private final SyntaxCheckerDescriptionSchemaParser syntaxCheckerParser =
        new SyntaxCheckerDescriptionSchemaParser();
    private final LdapSyntaxDescriptionSchemaParser syntaxParser =
        new LdapSyntaxDescriptionSchemaParser();
    private final MatchingRuleDescriptionSchemaParser matchingRuleParser =
        new MatchingRuleDescriptionSchemaParser();
    private final AttributeTypeDescriptionSchemaParser attributeTypeParser = 
        new AttributeTypeDescriptionSchemaParser();
    private final ObjectClassDescriptionSchemaParser objectClassParser = 
        new ObjectClassDescriptionSchemaParser();
    private final MatchingRuleUseDescriptionSchemaParser matchingRuleUseParser = 
        new MatchingRuleUseDescriptionSchemaParser();
    private final DITStructureRuleDescriptionSchemaParser ditStructureRuleParser =
        new DITStructureRuleDescriptionSchemaParser();
    private final DITContentRuleDescriptionSchemaParser ditContentRuleParser =
        new DITContentRuleDescriptionSchemaParser();
    private final NameFormDescriptionSchemaParser nameFormParser =
        new NameFormDescriptionSchemaParser();
    
    private final SchemaPartitionDao dao;
    
    
    /**
     * Creates a description parser.
     * 
     * @param globalRegistries the registries to use while creating new schema entities
     */
    public DescriptionParsers( Registries globalRegistries, SchemaPartitionDao dao )
    {
        this.globalRegistries = globalRegistries;
        this.dao = dao;
    }

    
    public SyntaxCheckerDescription[] parseSyntaxCheckers( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_SYNTAX_CHECKERS;
        }
        
        SyntaxCheckerDescription[] syntaxCheckerDescriptions = new SyntaxCheckerDescription[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            try
            {
                syntaxCheckerDescriptions[pos++] = 
                    syntaxCheckerParser.parseSyntaxCheckerDescription( (String)value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the syntaxCheckerDescription syntax: " + value, 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
        }
        
        return syntaxCheckerDescriptions;
    }
    
    
    public NormalizerDescription[] parseNormalizers( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_NORMALIZERS;
        }
        
        NormalizerDescription[] normalizerDescriptions = new NormalizerDescription[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            try
            {
                normalizerDescriptions[pos++] = normalizerParser.parseNormalizerDescription( (String)value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the normalizerDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
        }
        
        return normalizerDescriptions;
    }
    

    public ComparatorDescription[] parseComparators( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_COMPARATORS;
        }
        
        ComparatorDescription[] comparatorDescriptions = new ComparatorDescription[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            try
            {
                comparatorDescriptions[pos++] = comparatorParser.parseComparatorDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the comparatorDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
        }
        
        return comparatorDescriptions;
    }
    

    /**
     * Parses a set of attributeTypeDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing attributeTypeDescriptions
     * @return the set of attributeType objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public AttributeType[] parseAttributeTypes( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_ATTRIBUTE_TYPES;
        }
        
        AttributeType[] attributeTypes = new AttributeType[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            AttributeTypeDescription desc = null;
            
            try
            {
                desc = attributeTypeParser.parseAttributeTypeDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the attributeTypeDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }

            // if the supertype is provided make sure it exists in some schema
            if ( desc.getSuperType() != null && ! dao.hasAttributeType( desc.getSuperType() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an invalid super type: " 
                        + desc.getSuperType(), 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            // if the syntax is provided by the description make sure it exists in some schema
            if ( desc.getSyntax() != null && ! dao.hasSyntax( desc.getSyntax() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an invalid syntax: " + desc.getSyntax(), 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
            
            // if the matchingRule is provided make sure it exists in some schema
            if ( desc.getEqualityMatchingRule() != null && ! dao.hasMatchingRule( desc.getEqualityMatchingRule() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an invalid EQUALITY matchingRule: " 
                        + desc.getEqualityMatchingRule(), 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            // if the matchingRule is provided make sure it exists in some schema
            if ( desc.getOrderingMatchingRule() != null && ! dao.hasMatchingRule( desc.getOrderingMatchingRule() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an invalid ORDERING matchingRule: " 
                        + desc.getOrderingMatchingRule(), 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            // if the matchingRule is provided make sure it exists in some schema
            if ( desc.getSubstringsMatchingRule() != null && ! dao.hasMatchingRule( desc.getSubstringsMatchingRule() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an invalid SUBSTRINGS matchingRule: " 
                        + desc.getSubstringsMatchingRule(), 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            // if the equality matching rule is null and no super type is specified then there is
            // definitely no equality matchingRule that can be resolved.  We cannot use an attribute
            // without a matchingRule for search or for building indices not to mention lookups.
            if ( desc.getEqualityMatchingRule() == null && desc.getSuperType() == null )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an no EQUALITY matchingRule " +
                    "\nand no super type from which to derive an EQUALITY matchingRule.", 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
            else if ( desc.getEqualityMatchingRule() == null )
            {
                AttributeType superType = globalRegistries.getAttributeTypeRegistry().lookup( desc.getSuperType() );
                if ( superType.getEquality() == null )
                {
                    throw new LdapOperationNotSupportedException(
                        "Cannot permit the addition of an attributeType with which cannot resolve an " +
                        "EQUALITY matchingRule from it's super type.", 
                        ResultCodeEnum.UNWILLING_TO_PERFORM );
                }
            }
            
            // a syntax is manditory for an attributeType and if not provided by the description 
            // must be provided from some ancestor in the attributeType hierarchy; without either
            // of these the description definitely cannot resolve a syntax and cannot be allowed.
            // if a supertype exists then it must resolve a proper syntax somewhere in the hierarchy.
            if ( desc.getSyntax() == null && desc.getSuperType() == null )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of an attributeType with an no syntax " +
                    "\nand no super type from which to derive a syntax.", 
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
            

            AttributeTypeImpl at = new AttributeTypeImpl( desc.getNumericOid(), globalRegistries );
            at.setCanUserModify( desc.isUserModifiable() );
            at.setCollective( desc.isCollective() );
            at.setEqualityOid( desc.getEqualityMatchingRule() );
            at.setOrderingOid( desc.getOrderingMatchingRule() );
            at.setSingleValue( desc.isSingleValued() );
            at.setSubstrOid( desc.getSubstringsMatchingRule() );
            at.setSuperiorOid( desc.getSuperType() );
            at.setSyntaxOid( desc.getSyntax() );
            at.setUsage( desc.getUsage() );
            
            setSchemaObjectProperties( desc, at );

            attributeTypes[pos++] = at;
        }
        
        return attributeTypes;
    }
    
    
    /**
     * Parses a set of objectClassDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing objectClassDescriptions
     * @return the set of objectClass objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public ObjectClass[] parseObjectClasses( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_OBJECT_CLASSES;
        }
        
        ObjectClass[] objectClasses = new ObjectClass[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            ObjectClassDescription desc = null;
            
            try
            {
                desc = objectClassParser.parseObjectClassDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the objectClassDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            // if the super objectClasses are provided make sure it exists in some schema
            if ( desc.getSuperiorObjectClasses() != null && desc.getSuperiorObjectClasses().size() > 0 )
            {
                for ( String superior : desc.getSuperiorObjectClasses() )
                {
                    if ( superior.equals( SchemaConstants.TOP_OC_OID ) || 
                        superior.equalsIgnoreCase( SchemaConstants.TOP_OC ) )
                    {
                        continue;
                    }
                    
                    if ( ! dao.hasObjectClass( superior ) )
                    {
                        throw new LdapOperationNotSupportedException(
                            "Cannot permit the addition of an objectClass with an invalid superior objectClass: " 
                                + superior, 
                            ResultCodeEnum.UNWILLING_TO_PERFORM );
                    }
                }
            }
            
            // if the may list is provided make sure attributes exists in some schema
            if ( desc.getMayAttributeTypes() != null && desc.getMayAttributeTypes().size() > 0 )
            {
                for ( String mayAttr : desc.getMayAttributeTypes() )
                {
                    if ( ! dao.hasAttributeType( mayAttr ) )
                    {
                        throw new LdapOperationNotSupportedException(
                            "Cannot permit the addition of an objectClass with an invalid " +
                            "attributeType in the mayList: " + mayAttr, 
                            ResultCodeEnum.UNWILLING_TO_PERFORM );
                    }
                }
            }
            
            // if the must list is provided make sure attributes exists in some schema
            if ( desc.getMustAttributeTypes() != null && desc.getMustAttributeTypes().size() > 0 )
            {
                for ( String mustAttr : desc.getMustAttributeTypes() )
                {
                    if ( ! dao.hasAttributeType( mustAttr ) )
                    {
                        throw new LdapOperationNotSupportedException(
                            "Cannot permit the addition of an objectClass with an invalid " +
                            "attributeType in the mustList: " + mustAttr, 
                            ResultCodeEnum.UNWILLING_TO_PERFORM );
                    }
                }
            }
            
            ObjectClassImpl oc = new ObjectClassImpl( desc.getNumericOid(), globalRegistries );
            oc.setMayListOids( desc.getMayAttributeTypes().toArray( EMPTY) );
            oc.setMustListOids( desc.getMustAttributeTypes().toArray( EMPTY ) );
            oc.setSuperClassOids( desc.getSuperiorObjectClasses().toArray( EMPTY ) );
            oc.setType( desc.getKind() );
            setSchemaObjectProperties( desc, oc );
            
            objectClasses[pos++] = oc;
        }
        
        return objectClasses;
    }


    /**
     * Parses a set of matchingRuleUseDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing matchingRuleUseDescriptions
     * @return the set of matchingRuleUse objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public MatchingRuleUse[] parseMatchingRuleUses( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_MATCHING_RULE_USES;
        }
        
        MatchingRuleUse[] matchingRuleUses = new MatchingRuleUse[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            MatchingRuleUseDescription desc = null;
            
            try
            {
                desc = matchingRuleUseParser.parseMatchingRuleUseDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the matchingRuleUseDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            MatchingRuleUseImpl mru = new MatchingRuleUseImpl( desc.getNumericOid(), globalRegistries );
            mru.setApplicableAttributesOids( desc.getApplicableAttributes().toArray( EMPTY ) );
            setSchemaObjectProperties( desc, mru );
            
            matchingRuleUses[pos++] = mru;
        }

        return matchingRuleUses;
    }


    /**
     * Parses a set of ldapSyntaxDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing ldapSyntaxDescriptions
     * @return the set of Syntax objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public Syntax[] parseSyntaxes( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_SYNTAXES;
        }
        
        Syntax[] syntaxes = new Syntax[attr.size()];

        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            LdapSyntaxDescription desc = null;
            
            try
            {
                desc = syntaxParser.parseLdapSyntaxDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the ldapSyntaxDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            if ( ! dao.hasSyntaxChecker( desc.getNumericOid() ) )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot permit the addition of a syntax without the prior creation of a " +
                    "\nsyntaxChecker with the same object identifier of the syntax!",
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            SyntaxImpl syntax = new SyntaxImpl( desc.getNumericOid(), globalRegistries.getSyntaxCheckerRegistry() );
            setSchemaObjectProperties( desc, syntax );
            syntax.setHumanReadable( isHumanReadable( desc ) );
            syntaxes[pos++] = syntax;
        }
        
        return syntaxes;
    }


    /**
     * Parses a set of matchingRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing matchingRuleDescriptions
     * @return the set of matchingRule objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public MatchingRule[] parseMatchingRules( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_MATCHING_RULES;
        }
        
        MatchingRule[] matchingRules = new MatchingRule[attr.size()];

        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            MatchingRuleDescription desc = null;

            try
            {
                desc = matchingRuleParser.parseMatchingRuleDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the matchingRuleDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            if ( ! dao.hasSyntax( desc.getSyntax() )  )
            {
                throw new LdapOperationNotSupportedException(
                    "Cannot create a matchingRule that depends on non-existant syntax: " + desc.getSyntax(),
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }
            
            MatchingRuleImpl mr = new MatchingRuleImpl( desc.getNumericOid(), desc.getSyntax(), globalRegistries );
            setSchemaObjectProperties( desc, mr );
            
            matchingRules[pos++] = mr;
        }
        
        return matchingRules;
    }
    

    /**
     * Parses a set of dITStructureRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing dITStructureRuleDescriptions
     * @return the set of DITStructureRule objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public DITStructureRule[] parseDitStructureRules( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_DIT_STRUCTURE_RULES;
        }
        
        DITStructureRule[] ditStructureRules = new DITStructureRule[attr.size()];
        
        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            DITStructureRuleDescription desc = null;
     
            try
            {
                desc = ditStructureRuleParser.parseDITStructureRuleDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the ditStructureRuleDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            DitStructureRuleImpl dsr = new DitStructureRuleImpl( desc.getNumericOid(), 
                desc.getRuleId(), globalRegistries );
            dsr.setSuperClassRuleIds( desc.getSuperRules().toArray( EMPTY_INT_ARRAY ) );
            
            setSchemaObjectProperties( desc, dsr );

            ditStructureRules[pos++] = dsr;
        }
        
        return ditStructureRules;
    }

    
    /**
     * Parses a set of dITContentRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing dITContentRuleDescriptions
     * @return the set of DITContentRule objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public DITContentRule[] parseDitContentRules( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_DIT_CONTENT_RULES;
        }
        
        DITContentRule[] ditContentRules = new DITContentRule[attr.size()];

        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            DITContentRuleDescription desc = null;
     
            try
            {
                desc = ditContentRuleParser.parseDITContentRuleDescription( ( String ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the ditContentRuleDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            DitContentRuleImpl dcr = new DitContentRuleImpl( desc.getNumericOid(), globalRegistries );
            dcr.setAuxObjectClassOids( desc.getAuxiliaryObjectClasses().toArray( EMPTY ) );
            dcr.setMayNameOids( desc.getMayAttributeTypes().toArray( EMPTY ) );
            dcr.setMustNameOids( desc.getMustAttributeTypes().toArray( EMPTY ) );
            dcr.setNotNameOids( desc.getNotAttributeTypes().toArray( EMPTY ) );
            
            setSchemaObjectProperties( desc, dcr );

            ditContentRules[pos++] = dcr;
        }
        
        return ditContentRules;
    }

    
    /**
     * Parses a set of nameFormDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing nameFormDescriptions
     * @return the set of NameFormRule objects for the descriptions 
     * @throws NamingException if there are problems parsing the descriptions
     */
    public NameForm[] parseNameForms( EntryAttribute attr ) throws NamingException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_NAME_FORMS;
        }
        
        NameForm[] nameForms = new NameForm[attr.size()];

        int pos = 0;
        
        for ( Value<?> value:attr )
        {
            NameFormDescription desc = null;
            
            try
            {
                desc = nameFormParser.parseNameFormDescription( ( String  ) value.get() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( 
                    "The following does not conform to the nameFormDescription syntax: " + value.get(), 
                    ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
                iave.setRootCause( e );
                throw iave;
            }
            
            NameFormImpl nf = new NameFormImpl( desc.getNumericOid(), globalRegistries );
            nf.setMayUseOids( desc.getMayAttributeTypes().toArray( EMPTY ) );
            nf.setMustUseOids( desc.getMustAttributeTypes().toArray( EMPTY ) );
            nf.setObjectClassOid( desc.getStructuralObjectClass() );
            
            setSchemaObjectProperties( desc, nf );
            
            nameForms[pos++] = nf;
        }
        
        return nameForms;
    }
    
    
    /**
     * Called to populate the common schema object properties using an abstract 
     * description object.
     *   
     * @param desc the source description object to copy properties from
     * @param obj the mutable schema object to copy properites to
     */
    private void setSchemaObjectProperties( AbstractSchemaDescription desc, MutableSchemaObject obj )
    {
        obj.setDescription( desc.getDescription() );
        obj.setSchema( getSchema( desc ) );

        if ( ! ( desc instanceof LdapSyntaxDescription ) )
        {
            obj.setNames( desc.getNames().toArray( EMPTY ) );
            obj.setObsolete( desc.isObsolete() );
        }
    }
    
    
    /**
     * Checks to see if the syntax description is human readable by checking 
     * for the presence of the X-IS-HUMAN_READABLE schema extension.
     * 
     * @param desc the ldapSyntaxDescription 
     * @return true if the syntax is human readable, false otherwise
     */
    private boolean isHumanReadable( LdapSyntaxDescription desc )
    {
        List<String> values = desc.getExtensions().get( MetaSchemaConstants.X_IS_HUMAN_READABLE );
        
        if ( values == null || values.size() == 0 )
        {
            return false;
        }
        else
        {
            String value = values.get( 0 );
            if ( value.equals( "TRUE" ) )
            {
                return true;
            }
            else
            {
                return false;
            }
        }
    }
    
    
    /**
     * Gets the schema name for the schema description by looking up the value 
     * of the X-SCHEMA schema extension of the description. 
     * 
     * @param desc the schema description 
     * @return the schema name for the schema entity
     */
    private String getSchema( AbstractSchemaDescription desc ) 
    {
        List<String> values = desc.getExtensions().get( MetaSchemaConstants.X_SCHEMA );
        
        if ( values == null )
        {
            return OTHER_SCHEMA;
        }
        else 
        {
            return values.get( 0 );
        }
    }
}
