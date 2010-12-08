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
package org.apache.directory.server.config;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.directory.server.config.beans.AdsBaseBean;
import org.apache.directory.server.config.beans.ConfigBean;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * This class implements a writer for ApacheDS Configuration.
 * <p>
 * It can be used either:
 * <ul>
 *      <li>write the configuration to an LDIF</li>
 *      <li>get the list of LDIF entries from the configuration</li>
 * </ul>
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ConfigWriter
{
    /** The schema manager */
    private SchemaManager schemaManager;

    /** The configuration bean */
    private ConfigBean configBean;

    /** The list of entries */
    private List<LdifEntry> entries;


    /**
     * Creates a new instance of ConfigWriter.
     *
     * @param schemaManager
     *      the schema manager
     * @param configBean
     *      the configuration bean
     */
    public ConfigWriter( SchemaManager schemaManager, ConfigBean configBean )
    {
        this.schemaManager = schemaManager;
        this.configBean = configBean;
    }


    /**
     * Converts the configuration bean to a list of LDIF entries.
     */
    private void convertConfigurationBeanToLdifEntries() throws ConfigurationException
    {
        try
        {
            if ( entries == null )
            {
                entries = new ArrayList<LdifEntry>();

                // Building the default config root entry 'ou=config'
                LdifEntry configRootEntry = new LdifEntry();
                configRootEntry.setDn( new DN( SchemaConstants.OU_AT + "=" + "config" ) );
                addObjectClassAttribute( schemaManager, configRootEntry, "organizationalUnit" );
                addAttributeTypeValues( SchemaConstants.OU_AT, "config", configRootEntry );
                entries.add( configRootEntry );

                // Building entries from the directory service beans
                List<AdsBaseBean> directoryServiceBeans = configBean.getDirectoryServiceBeans();
                for ( AdsBaseBean adsBaseBean : directoryServiceBeans )
                {
                    addBean( configRootEntry.getDn(), schemaManager, adsBaseBean, entries );
                }
            }
        }
        catch ( Exception e )
        {
            throw new ConfigurationException( "Unable to convert the configuration bean to LDIF entries", e );
        }
    }


    /**
     * Writes the configuration bean as LDIF to the given file.
     *
     * @param path
     *      the output file path
     * @throws ConfigurationException
     *      if an error occurs during the conversion to LDIF
     * @throws IOException
     *      if an error occurs when writing the file
     */
    public void write( String path ) throws ConfigurationException, IOException
    {
        write( new File( path ) );
    }


    /**
     * Writes the configuration bean as LDIF to the given file.
     *
     * @param file
     *      the output file
     * @throws ConfigurationException
     *      if an error occurs during the conversion to LDIF
     * @throws IOException
     *      if an error occurs when writing the file
     */
    public void write( File file ) throws ConfigurationException, IOException
    {
        // Converting the configuration bean to a list of LDIF entries
        convertConfigurationBeanToLdifEntries();

        // Writing the file to disk
        FileWriter writer = new FileWriter( file );
        writer.append( "version: 1\n" );
        for ( LdifEntry entry : entries )
        {
            writer.append( entry.toString() );
        }
        writer.close();
    }
    
    /**
     * Gets the converted LDIF entries from the configuration bean.
     *
     * @return
     *      the list of converted LDIF entries
     * @throws ConfigurationException
     *      if an error occurs during the conversion to LDIF
     */
    public List<LdifEntry> getConvertedLdifEntries() throws ConfigurationException
    {
        // Converting the configuration bean to a list of LDIF entries
        convertConfigurationBeanToLdifEntries();
        
        // Returning the list of entries
        return entries;
    }


    /**
     * Adds the computed 'objectClass' attribute for the given entry and object class name.
     *
     * @param schemaManager
     *      the schema manager
     * @param entry
     *      the entry
     * @param objectClass
     *      the object class name
     * @throws LdapException
     */
    private void addObjectClassAttribute( SchemaManager schemaManager, LdifEntry entry, String objectClass )
        throws LdapException
    {
        ObjectClass objectClassObject = schemaManager.getObjectClassRegistry().lookup( objectClass );
        if ( objectClassObject != null )
        {
            // Building the list of 'objectClass' attribute values
            Set<String> objectClassAttributeValues = new HashSet<String>();
            computeObjectClassAttributeValues( schemaManager, objectClassAttributeValues, objectClassObject );

            // Adding values to the entry
            addAttributeTypeValues( SchemaConstants.OBJECT_CLASS_AT, objectClassAttributeValues, entry );
        }
        else
        {
            // TODO: throw an exception 
        }
    }


    /**
     * Recursively computes the 'objectClass' attribute values set.
     *
     * @param schemaManager
     *      the schema manager
     * @param objectClassAttributeValues
     *      the set containing the values
     * @param objectClass
     *      the current object class
     * @throws LdapException
     */
    private void computeObjectClassAttributeValues( SchemaManager schemaManager,
        Set<String> objectClassAttributeValues,
        ObjectClass objectClass ) throws LdapException
    {
        ObjectClass topObjectClass = schemaManager.getObjectClassRegistry().lookup( SchemaConstants.TOP_OC );
        if ( topObjectClass != null )
        {
            // TODO throw new exception (there should be a top object class 
        }

        if ( topObjectClass.equals( objectClass ) )
        {
            objectClassAttributeValues.add( objectClass.getName() );
        }
        else
        {
            objectClassAttributeValues.add( objectClass.getName() );

            List<ObjectClass> superiors = objectClass.getSuperiors();
            if ( ( superiors != null ) && ( superiors.size() > 0 ) )
            {
                for ( ObjectClass superior : superiors )
                {
                    computeObjectClassAttributeValues( schemaManager, objectClassAttributeValues, superior );
                }
            }
            else
            {
                objectClassAttributeValues.add( topObjectClass.getName() );
            }
        }
    }


    /**
     * Adds a configuration bean to the list of entries.
     *
     * @param rootDn
     *      the current root DN
     * @param schemaManager
     *      the schema manager
     * @param bean
     *      the configuration bean
     * @param entries
     *      the list of the entries
     * @throws Exception
     */
    private void addBean( DN rootDn, SchemaManager schemaManager, AdsBaseBean bean, List<LdifEntry> entries )
        throws Exception
    {
        addBean( rootDn, schemaManager, bean, entries, null, null );
    }


    /**
     * Adds a configuration bean to the list of entries.
     *
     * @param rootDn
     *      the current root DN
     * @param schemaManager
     *      the schema manager
     * @param bean
     *      the configuration bean
     * @param entries
     *      the list of the entries
     * @param parentEntry
     *      the parent entry
     * @param attributeTypeForParentEntry
     *      the attribute type to use when adding the value of 
     *      the RDN to the parent entry
     * @throws Exception
     */
    private void addBean( DN rootDn, SchemaManager schemaManager, AdsBaseBean bean, List<LdifEntry> entries,
        LdifEntry parentEntry, String attributeTypeForParentEntry )
        throws Exception
    {
        if ( bean != null )
        {
            // Getting the class of the bean
            Class<?> beanClass = bean.getClass();

            // Creating the entry to hold the bean and adding it to the list
            LdifEntry entry = new LdifEntry();
            entry.setDn( getDn( rootDn, bean ) );
            addObjectClassAttribute( schemaManager, entry, getObjectClassNameForBean( beanClass ) );
            entries.add( entry );

            // A flag to know when we reached the 'AdsBaseBean' class when 
            // looping on the class hierarchy of the bean
            boolean adsBaseBeanClassFound = false;

            // Looping until the 'AdsBaseBean' class has been found
            while ( !adsBaseBeanClassFound )
            {
                // Checking if we reached the 'AdsBaseBean' class
                if ( beanClass == AdsBaseBean.class )
                {
                    adsBaseBeanClassFound = true;
                }

                // Looping on all fields of the bean
                Field[] fields = beanClass.getDeclaredFields();
                for ( Field field : fields )
                {
                    // Making the field accessible (we get an exception if we don't do that)
                    field.setAccessible( true );

                    // Getting the class of the field
                    Class<?> fieldClass = field.getType();
                    Object fieldValue = field.get( bean );

                    // Looking for the @ConfigurationElement annotation
                    ConfigurationElement configurationElement = field.getAnnotation( ConfigurationElement.class );
                    if ( configurationElement != null )
                    {
                        // Checking if we're have a value  for the attribute type
                        String attributeType = configurationElement.attributeType();
                        if ( ( attributeType != null ) && ( !"".equals( attributeType ) ) )
                        {
                            // Checking if we're dealing with a container
                            String container = configurationElement.container();
                            if ( ( container != null ) && ( !"".equals( container ) ) )
                            {
                                // Creating the entry for the container and adding it to the list
                                LdifEntry containerEntry = new LdifEntry();
                                containerEntry.setDn( entry.getDn().add( new RDN( SchemaConstants.OU_AT, container ) ) );
                                addObjectClassAttribute( schemaManager, containerEntry,
                                    SchemaConstants.ORGANIZATIONAL_UNIT_OC );
                                entries.add( containerEntry );

                                if ( Collection.class.isAssignableFrom( fieldClass ) )
                                {
                                    // Looping on the Collection's objects
                                    Collection<Object> collection = ( Collection<Object> ) fieldValue;
                                    for ( Object object : collection )
                                    {
                                        if ( object instanceof AdsBaseBean )
                                        {
                                            // Adding the bean
                                            addBean( containerEntry.getDn(), schemaManager, ( AdsBaseBean ) object,
                                                entries, entry, attributeType );

                                            continue;
                                        }
                                        else
                                        {
                                            // TODO throw an error, if we have a container, the type must be a subtype of AdsBaseBean
                                            throw new Exception();
                                        }
                                    }
                                }
                                else
                                {
                                    // TODO throw an error, if we have a container, the type must be a subtype of Collection
                                    throw new Exception();
                                }
                            }
                            else
                            {
                                // Is it the field value used as RDN and do we need to insert a value in the parent entry?
                                if ( ( configurationElement.isRDN() ) && ( parentEntry != null )
                                    && ( attributeTypeForParentEntry != null ) )
                                {
                                    // Adding the field value to the parent entry
                                    addAttributeTypeValues( attributeTypeForParentEntry, fieldValue, parentEntry );
                                }

                                // Adding values to the entry
                                addAttributeTypeValues( configurationElement.attributeType(), fieldValue, entry );

                                continue;
                            }
                        }

                        // Checking if we're dealing with a AdsBaseBean subclass type
                        if ( AdsBaseBean.class.isAssignableFrom( fieldClass ) )
                        {
                            addBean( entry.getDn(), schemaManager, ( AdsBaseBean ) fieldValue, entries );
                            continue;
                        }
                    }
                }

                // Moving to the upper class in the class hierarchy
                beanClass = beanClass.getSuperclass();
            }
        }
    }


    /**
     * Gets the name of the object class to use for the given bean class.
     *
     * @param c
     *      the bean class
     * @return
     *      the name of the object class to use for the given bean class
     */
    private String getObjectClassNameForBean( Class<?> c )
    {
        String classNameWithPackage = getClassNameWithoutPackageName( c );
        return "ads-" + classNameWithPackage.substring( 0, classNameWithPackage.length() - 4 );
    }


    /**
     * Gets the class name of the given class stripped from its package name.
     *
     * @param c
     *      the class
     * @return
     *      the class name of the given class stripped from its package name
     */
    private String getClassNameWithoutPackageName( Class<?> c )
    {
        String className = c.getName();

        int firstChar = className.lastIndexOf( '.' ) + 1;
        if ( firstChar > 0 )
        {
            return className.substring( firstChar );
        }

        return className;
    }


    /**
     * Indicates the given type is multiple.
     *
     * @param clazz
     *      the class
     * @return
     *      <code>true</code> if the given is multiple,
     *      <code>false</code> if not.
     */
    private boolean isMultiple( Class<?> clazz )
    {
        return Collection.class.isAssignableFrom( clazz );
    }


    /**
     * Gets the DN associated with the configuration bean based on the given base DN.
     *
     * @param baseDN
     *      the base DN
     * @param bean
     *      the configuration bean
     * @return
     *      the DN associated with the configuration bean based on the given base DN.
     * @throws LdapInvalidDnException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    private DN getDn( DN baseDN, AdsBaseBean bean ) throws LdapInvalidDnException, IllegalArgumentException,
        IllegalAccessException
    {
        // Getting the class of the bean
        Class<?> beanClass = bean.getClass();

        // A flag to know when we reached the 'AdsBaseBean' class when 
        // looping on the class hierarchy of the bean
        boolean adsBaseBeanClassFound = false;

        // Looping until the 'AdsBaseBean' class has been found
        while ( !adsBaseBeanClassFound )
        {
            // Checking if we reached the 'AdsBaseBean' class
            if ( beanClass == AdsBaseBean.class )
            {
                adsBaseBeanClassFound = true;
            }

            // Looping on all fields of the bean
            Field[] fields = beanClass.getDeclaredFields();
            for ( Field field : fields )
            {
                // Making the field accessible (we get an exception if we don't do that)
                field.setAccessible( true );

                // Looking for the @ConfigurationElement annotation and
                // if the field is the RDN
                ConfigurationElement configurationElement = field.getAnnotation( ConfigurationElement.class );
                if ( ( configurationElement != null ) && ( configurationElement.isRDN() ) )
                {
                    return baseDN.add( new RDN( configurationElement.attributeType(), field.get( bean ).toString() ) );
                }
            }

            // Moving to the upper class in the class hierarchy
            beanClass = beanClass.getSuperclass();
        }

        return DN.EMPTY_DN; // TODO Throw an error when we reach that point
    }


    /**
     * Adds values for an attribute type to the given entry.
     *
     * @param attributeType
     *      the attribute type
     * @param value
     *      the value
     * @param entry
     *      the entry
     * @throws LdapException
     */
    private void addAttributeTypeValues( String attributeType, Object o, LdifEntry entry )
        throws LdapException
    {
        // We don't store a 'null' value
        if ( o != null )
        {
            // Getting the attribute from the entry
            EntryAttribute attribute = entry.get( attributeType );

            // If no attribute has been found, we need to create it and add it to the entry
            if ( attribute == null )
            {
                attribute = new DefaultEntryAttribute( attributeType );
                entry.addAttribute( attribute );
            }

            // Is the value multiple?
            if ( isMultiple( o.getClass() ) )
            {
                // Adding each single value separately
                Collection<?> values = ( Collection<?> ) o;
                if ( values != null )
                {
                    for ( Object value : values )
                    {
                        addAttributeTypeValue( attribute, value );
                    }
                }
            }
            else
            {
                // Adding the single value
                addAttributeTypeValue( attribute, o );
            }
        }
    }


    /**
     * Adds a value, either byte[] or another type (converted into a String 
     * via the Object.toString() method), to the attribute.
     *
     * @param attribute
     *      the attribute
     * @param value
     *      the value
     */
    private void addAttributeTypeValue( EntryAttribute attribute, Object value )
    {
        // We don't store a 'null' value
        if ( value != null )
        {
            // Storing the value to the attribute
            if ( value instanceof byte[] )
            {
                // Value is a byte[]
                attribute.add( ( byte[] ) value );
            }
            else
            {
                // Value is another type of object that we store as a String
                // (There will be an automatic translation for primary types like int, long, boolean, etc.)
                attribute.add( value.toString() );
            }
        }
    }
}
