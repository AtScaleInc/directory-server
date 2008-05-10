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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.directory.shared.ldap.entry.AbstractEntry;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;


/**
 * A default implementation of a ServerEntry which should suite most
 * use cases.
 * 
 * This class is final, it should not be extended.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public final class DefaultClientEntry extends AbstractEntry<String> implements ClientEntry, Externalizable
{
    /** Used for serialization */
    public static final long serialVersionUID = 2L;
    
    /** The logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultClientEntry.class );

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------
    /**
     * Creates a new instance of DefaultClientEntry. 
     * <p>
     * This entry <b>must</b> be initialized before being used !
     */
    public DefaultClientEntry()
    {
    }


    /**
     * Creates a new instance of DefaultServerEntry, with a 
     * DN. 
     * 
     * @param dn The DN for this serverEntry. Can be null.
     */
    public DefaultClientEntry( LdapDN dn )
    {
        this.dn = dn;
    }


    /**
     * Creates a new instance of DefaultServerEntry, with a 
     * DN and a list of IDs. 
     * 
     * @param dn The DN for this serverEntry. Can be null.
     * @param upIds The list of attributes to create.
     */
    public DefaultClientEntry( LdapDN dn, String... upIds )
    {
        this.dn = dn;

        for ( String upId:upIds )
        {
            // Add a new AttributeType without value
            set( upId );
        }
    }

    
    /**
     * <p>
     * Creates a new instance of DefaultClientEntry, with a 
     * DN and a list of EntryAttributes.
     * </p> 
     * 
     * @param dn The DN for this serverEntry. Can be null
     * @param attributes The list of attributes to create
     */
    public DefaultClientEntry( LdapDN dn, EntryAttribute... attributes )
    {
        this.dn = dn;

        for ( EntryAttribute attribute:attributes )
        {
            if ( attribute == null )
            {
                continue;
            }
            
            // Store a new ClientAttribute
            this.attributes.put( attribute.getId(), attribute );
        }
    }

    
    //-------------------------------------------------------------------------
    // Helper methods
    //-------------------------------------------------------------------------
    private String getId( String upId ) throws IllegalArgumentException
    {
        String id = StringTools.trim( StringTools.toLowerCase( upId ) );
        
        // If empty, throw an error
        if ( ( id == null ) || ( id.length() == 0 ) ) 
        {
            String message = "The attributeType ID should not be null or empty";
            LOG.error( message );
            throw new IllegalArgumentException( message );
        }
        
        return id;
    }

    
    //-------------------------------------------------------------------------
    // Entry methods
    //-------------------------------------------------------------------------
    /**
     * Add some Attributes to the current Entry.
     *
     * @param attributes The attributes to add
     * @throws NamingException If we can't add any of the attributes
     */
    public void add( EntryAttribute... attributes ) throws NamingException
    {
        // Loop on all the added attributes
        for ( EntryAttribute attribute:attributes )
        {
            // If the attribute already exist, we will add the new values.
            if ( contains( attribute ) )
            {
                EntryAttribute existingAttr = get( attribute.getId() );
                
                // Loop on all the values, and add them to the existing attribute
                for ( Value<?> value:attribute )
                {
                    existingAttr.add( value );
                }
            }
            else
            {
                // Stores the attribute into the entry
                this.attributes.put( attribute.getId(), (ClientAttribute)attribute );
            }
        }
    }

    
    /**
     * Add an attribute (represented by its ID and binary values) into an entry. 
     *
     * @param upId The attribute ID
     * @param values The list of binary values to inject. It can be empty
     * @throws NamingException If the attribute does not exist
     */
    public void add( String upId, byte[]... values ) throws NamingException
    {
        // First, transform the upID to a valid ID
        String id = getId( upId );
        
        // Now, check to see if we already have such an attribute
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute != null )
        {
            // This Attribute already exist, we add the values 
            // into it. (If the values already exists, they will
            // not be added, but this is done in the add() method)
            attribute.add( values );
            attribute.setUpId( upId );
        }
        else
        {
            // We have to create a new Attribute and set the values
            // and the upId
            attributes.put( id, new DefaultClientAttribute( upId, values ) );
        }
    }


    /**
     * Add some String values to the current Entry.
     *
     * @param upId The user provided ID of the attribute we want to add 
     * some values to
     * @param values The list of String values to add
     * @throws NamingException If we can't add any of the values
     */
    public void add( String upId, String... values ) throws NamingException
    {
        // First, transform the upID to a valid ID
        String id = getId( upId );

        // Now, check to see if we already have such an attribute
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute != null )
        {
            // This Attribute already exist, we add the values 
            // into it. (If the values already exists, they will
            // not be added, but this is done in the add() method)
            attribute.add( values );
            attribute.setUpId( upId );
        }
        else
        {
            // We have to create a new Attribute and set the values
            // and the upId
            attributes.put( id, new DefaultClientAttribute( upId, values ) );
        }
    }


    /**
     * Add an attribute (represented by its ID and Value values) into an entry. 
     *
     * @param upId The attribute ID
     * @param values The list of Value values to inject. It can be empty
     * @throws NamingException If the attribute does not exist
     */
    public void add( String upId, Value<?>... values ) throws NamingException
    {
        // First, transform the upID to a valid ID
        String id = getId( upId );

        // Now, check to see if we already have such an attribute
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute != null )
        {
            // This Attribute already exist, we add the values 
            // into it. (If the values already exists, they will
            // not be added, but this is done in the add() method)
            attribute.add( values );
            attribute.setUpId( upId );
        }
        else
        {
            // We have to create a new Attribute and set the values
            // and the upId
            attributes.put( id, new DefaultClientAttribute( upId, values ) );
        }
    }


    /**
     * Clone an entry. All the element are duplicated, so a modification on
     * the original object won't affect the cloned object, as a modification
     * on the cloned object has no impact on the original object
     */
    public Entry clone()
    {
        // First, clone the structure
        DefaultClientEntry clone = (DefaultClientEntry)super.clone();
        
        // Just in case ... Should *never* happen
        if ( clone == null )
        {
            return null;
        }
        
        // An Entry has a DN and many attributes.
        // First, clone the DN, if not null.
        if ( dn != null )
        {
            clone.setDn( (LdapDN)dn.clone() );
        }
        
        // then clone the ClientAttribute Map.
        clone.attributes = (Map<String, EntryAttribute>)(((HashMap<String, EntryAttribute>)attributes).clone());
        
        // now clone all the attributes
        clone.attributes.clear();
        
        for ( EntryAttribute attribute:attributes.values() )
        {
            clone.attributes.put( attribute.getId(), attribute.clone() );
        }
        
        // We are done !
        return clone;
    }
    

    /**
     * <p>
     * Checks if an entry contains a list of attributes.
     * </p>
     * <p>
     * If the list is null or empty, this method will return <code>true</code>
     * if the entry has no attribute, <code>false</code> otherwise.
     * </p>
     *
     * @param attributes The Attributes to look for
     * @return <code>true</code> if all the attributes are found within 
     * the entry, <code>false</code> if at least one of them is not present.
     * @throws NamingException If the attribute does not exist
     */
    public boolean contains( EntryAttribute... attributes ) throws NamingException
    {
        for ( EntryAttribute attribute:attributes )
        {
            if ( attribute == null )
            {
                return this.attributes.size() == 0;
            }
            
            if ( !this.attributes.containsKey( attribute.getId() ) )
            {
                return false;
            }
        }
        
        return true;
    }
    
    
    /**
     * Checks if an entry contains a specific attribute
     *
     * @param attributes The Attributes to look for
     * @return <code>true</code> if the attributes are found within the entry
     * @throws NamingException If the attribute does not exist
     */
    public boolean contains( String upId ) throws NamingException
    {
        String id = getId( upId );
        
        return attributes.containsKey( id );
    }

    
    /**
     * Checks if an entry contains an attribute with some binary values.
     *
     * @param id The Attribute we are looking for.
     * @param values The searched values.
     * @return <code>true</code> if all the values are found within the attribute,
     * false if at least one value is not present or if the ID is not valid. 
     */
    public boolean contains( String upId, byte[]... values )
    {
        String id = getId( upId );
        
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute == null )
        {
            return false;
        }
        
        return attribute.contains( values );
    }
    
    
    /**
     * Checks if an entry contains an attribute with some String values.
     *
     * @param id The Attribute we are looking for.
     * @param values The searched values.
     * @return <code>true</code> if all the values are found within the attribute,
     * false if at least one value is not present or if the ID is not valid. 
     */
    public boolean contains( String upId, String... values )
    {
        String id = getId( upId );
        
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute == null )
        {
            return false;
        }
        
        return attribute.contains( values );
    }
    
    
    /**
     * Checks if an entry contains an attribute with some values.
     *
     * @param id The Attribute we are looking for.
     * @param values The searched values.
     * @return <code>true</code> if all the values are found within the attribute,
     * false if at least one value is not present or if the ID is not valid. 
     */
    public boolean contains( String upId, Value<?>... values )
    {
        String id = getId( upId );
        
        EntryAttribute attribute = attributes.get( id );
        
        if ( attribute == null )
        {
            return false;
        }
        
        return attribute.contains( values );
    }
    
    
    /**
     * Checks if an entry contains some specific attributes.
     *
     * @param attributes The Attributes to look for.
     * @return <code>true</code> if the attributes are all found within the entry.
     */
    public boolean containsAttribute( String... attributes )
    {
        for ( String attribute:attributes )
        {
            String id = getId( attribute );
    
            if ( !this.attributes.containsKey( id ) )
            {
                return false;
            }
        }
        
        return true;
    }

    
    /**
     * <p>
     * Returns the attribute with the specified alias. The return value
     * is <code>null</code> if no match is found.  
     * </p>
     * <p>An Attribute with an id different from the supplied alias may 
     * be returned: for example a call with 'cn' may in some implementations 
     * return an Attribute whose getId() field returns 'commonName'.
     * </p>
     *
     * @param alias an aliased name of the attribute identifier
     * @return the attribute associated with the alias
     */
    public EntryAttribute get( String alias )
    {
        try
        {
            String id = getId( alias );
            
            return attributes.get( id );
        }
        catch( IllegalArgumentException iea )
        {
            LOG.error( "An exception has been raised while looking for attribute id {}''", alias );
            return null;
        }
    }


    /**
     * <p>
     * Put an attribute (represented by its ID and some binary values) into an entry. 
     * </p>
     * <p> 
     * If the attribute already exists, the previous attribute will be 
     * replaced and returned.
     * </p>
     *
     * @param upId The attribute ID
     * @param values The list of binary values to put. It can be empty.
     * @return The replaced attribute
     */
    public EntryAttribute put( String upId, byte[]... values )
    {
        // Get the normalized form of the ID
        String id = getId( upId );
        
        // Create a new attribute
        ClientAttribute clientAttribute = new DefaultClientAttribute( upId, values );

        // Replace the previous one, and return it back
        return attributes.put( id, clientAttribute );
    }


    /**
     * <p>
     * Put an attribute (represented by its ID and some String values) into an entry. 
     * </p>
     * <p> 
     * If the attribute already exists, the previous attribute will be 
     * replaced and returned.
     * </p>
     *
     * @param upId The attribute ID
     * @param values The list of String values to put. It can be empty.
     * @return The replaced attribute
     */
    public EntryAttribute put( String upId, String... values )
    {
        // Get the normalized form of the ID
        String id = getId( upId );
        
        // Create a new attribute
        ClientAttribute clientAttribute = new DefaultClientAttribute( upId, values );

        // Replace the previous one, and return it back
        return attributes.put( id, clientAttribute );
    }


    /**
     * <p>
     * Put an attribute (represented by its ID and some values) into an entry. 
     * </p>
     * <p> 
     * If the attribute already exists, the previous attribute will be 
     * replaced and returned.
     * </p>
     *
     * @param upId The attribute ID
     * @param values The list of values to put. It can be empty.
     * @return The replaced attribute
     */
    public EntryAttribute put( String upId, Value<?>... values )
    {
        // Get the normalized form of the ID
        String id = getId( upId );
        
        // Create a new attribute
        ClientAttribute clientAttribute = new DefaultClientAttribute( upId, values );

        // Replace the previous one, and return it back
        return attributes.put( id, clientAttribute );
    }


    /**
     * <p>
     * Put some new ClientAttribute using the User Provided ID. 
     * No value is inserted. 
     * </p>
     * <p>
     * If an existing Attribute is found, it will be replaced by an
     * empty attribute, and returned to the caller.
     * </p>
     * 
     * @param upIds The user provided IDs of the AttributeTypes to add.
     * @return A list of replaced Attributes.
     */
    public List<EntryAttribute> set( String... upIds )
    {
        if ( upIds == null )
        {
            String message = "The AttributeType list should not be null";
            LOG.error( message );
            throw new IllegalArgumentException( message );
        }
        
        List<EntryAttribute> returnedClientAttributes = new ArrayList<EntryAttribute>();
        
        // Now, loop on all the attributeType to add
        for ( String upId:upIds )
        {
            String id = StringTools.trim( StringTools.toLowerCase( upId ) );
            
            if ( id == null )
            {
                String message = "The AttributeType list should not contain null values";
                LOG.error( message );
                throw new IllegalArgumentException( message );
            }
            
            if ( attributes.containsKey( id ) )
            {
                // Add the removed serverAttribute to the list
                returnedClientAttributes.add( attributes.remove( id ) );
            }

            ClientAttribute newAttribute = new DefaultClientAttribute( upId );
            attributes.put( id, newAttribute );
        }
        
        return returnedClientAttributes;
    }

    
    /**
     * <p>
     * Places attributes in the attribute collection. 
     * </p>
     * <p>If there is already an attribute with the same ID as any of the 
     * new attributes, the old ones are removed from the collection and 
     * are returned by this method. If there was no attribute with the 
     * same ID the return value is <code>null</code>.
     *</p>
     *
     * @param attributes the attributes to be put
     * @return the old attributes with the same OID, if exist; otherwise
     *         <code>null</code>
     * @exception NamingException if the operation fails
     */
    public List<EntryAttribute> put( EntryAttribute... attributes ) throws NamingException
    {
        // First, get the existing attributes
        List<EntryAttribute> previous = new ArrayList<EntryAttribute>();
        
        for ( EntryAttribute attribute:attributes )
        {
            String id = attribute.getId();
            
            if ( contains( id ) )
            {
                // Store the attribute and remove it from the list
                previous.add( get( id ) );
                this.attributes.remove( id );
            }
            
            // add the new one
            this.attributes.put( id, (ClientAttribute)attribute );            
        }
        
        // return the previous attributes
        return previous;
    }


    public List<EntryAttribute> remove( EntryAttribute... attributes ) throws NamingException
    {
        List<EntryAttribute> removedAttributes = new ArrayList<EntryAttribute>();
        
        for ( EntryAttribute attribute:attributes )
        {
            if ( contains( attribute.getId() ) )
            {
                this.attributes.remove( attribute.getId() );
                removedAttributes.add( attribute );
            }
        }
        
        return removedAttributes;
    }


    /**
     * <p>
     * Removes the attribute with the specified alias. 
     * </p>
     * <p>
     * The removed attribute are returned by this method. 
     * </p>
     * <p>
     * If there is no attribute with the specified alias,
     * the return value is <code>null</code>.
     * </p>
     *
     * @param attributes an aliased name of the attribute to be removed
     * @return the removed attributes, if any, as a list; otherwise <code>null</code>
     */
    public List<EntryAttribute> removeAttributes( String... attributes )
    {
        if ( attributes.length == 0 )
        {
            return null;
        }
        
        List<EntryAttribute> removed = new ArrayList<EntryAttribute>( attributes.length );
        
        for ( String attribute:attributes )
        {
            EntryAttribute attr = get( attribute );
            
            if ( attr != null )
            {
                removed.add( this.attributes.remove( attr.getId() ) );
            }
            else
            {
                String message = "The attribute '" + attribute + "' does not exist in the entry";
                LOG.warn( message );
                continue;
            }
        }
        
        if ( removed.size() == 0 )
        {
            return null;
        }
        else
        {
            return removed;
        }
    }


    /**
     * <p>
     * Removes the specified binary values from an attribute.
     * </p>
     * <p>
     * If at least one value is removed, this method returns <code>true</code>.
     * </p>
     * <p>
     * If there is no more value after having removed the values, the attribute
     * will be removed too.
     * </p>
     * <p>
     * If the attribute does not exist, nothing is done and the method returns 
     * <code>false</code>
     * </p> 
     *
     * @param upId The attribute ID  
     * @param values the values to be removed
     * @return <code>true</code> if at least a value is removed, <code>false</code>
     * if not all the values have been removed or if the attribute does not exist. 
     */
    public boolean remove( String upId, byte[]... values ) throws NamingException
    {
        try
        {
            String id = getId( upId );
            
            EntryAttribute attribute = get( id );
            
            if ( attribute == null )
            {
                // Can't remove values from a not existing attribute !
                return false;
            }
            
            int nbOldValues = attribute.size();
            
            // Remove the values
            attribute.remove( values );
            
            if ( attribute.size() == 0 )
            {
                // No mare values, remove the attribute
                attributes.remove( id );
                
                return true;
            }
            
            if ( nbOldValues != attribute.size() )
            {
                // At least one value have been removed, return true.
                return true;
            }
            else
            {
                // No values have been removed, return false.
                return false;
            }
        }
        catch ( IllegalArgumentException iae )
        {
            LOG.error( "The removal of values for the missing '{}' attribute is not possible", upId );
            return false;
        }
    }


    /**
     * <p>
     * Removes the specified String values from an attribute.
     * </p>
     * <p>
     * If at least one value is removed, this method returns <code>true</code>.
     * </p>
     * <p>
     * If there is no more value after having removed the values, the attribute
     * will be removed too.
     * </p>
     * <p>
     * If the attribute does not exist, nothing is done and the method returns 
     * <code>false</code>
     * </p> 
     *
     * @param upId The attribute ID  
     * @param attributes the attributes to be removed
     * @return <code>true</code> if at least a value is removed, <code>false</code>
     * if not all the values have been removed or if the attribute does not exist. 
     */
    public boolean remove( String upId, String... values ) throws NamingException
    {
        try
        {
            String id = getId( upId );
            
            EntryAttribute attribute = get( id );
            
            if ( attribute == null )
            {
                // Can't remove values from a not existing attribute !
                return false;
            }
            
            int nbOldValues = attribute.size();
            
            // Remove the values
            attribute.remove( values );
            
            if ( attribute.size() == 0 )
            {
                // No mare values, remove the attribute
                attributes.remove( id );
                
                return true;
            }
            
            if ( nbOldValues != attribute.size() )
            {
                // At least one value have been removed, return true.
                return true;
            }
            else
            {
                // No values have been removed, return false.
                return false;
            }
        }
        catch ( IllegalArgumentException iae )
        {
            LOG.error( "The removal of values for the missing '{}' attribute is not possible", upId );
            return false;
        }
    }


    /**
     * <p>
     * Removes the specified values from an attribute.
     * </p>
     * <p>
     * If at least one value is removed, this method returns <code>true</code>.
     * </p>
     * <p>
     * If there is no more value after having removed the values, the attribute
     * will be removed too.
     * </p>
     * <p>
     * If the attribute does not exist, nothing is done and the method returns 
     * <code>false</code>
     * </p> 
     *
     * @param upId The attribute ID  
     * @param attributes the attributes to be removed
     * @return <code>true</code> if at least a value is removed, <code>false</code>
     * if not all the values have been removed or if the attribute does not exist. 
     */
    public boolean remove( String upId, Value<?>... values ) throws NamingException
    {
        try
        {
            String id = getId( upId );
            
            EntryAttribute attribute = get( id );
            
            if ( attribute == null )
            {
                // Can't remove values from a not existing attribute !
                return false;
            }
            
            int nbOldValues = attribute.size();
            
            // Remove the values
            attribute.remove( values );
            
            if ( attribute.size() == 0 )
            {
                // No mare values, remove the attribute
                attributes.remove( id );
                
                return true;
            }
            
            if ( nbOldValues != attribute.size() )
            {
                // At least one value have been removed, return true.
                return true;
            }
            else
            {
                // No values have been removed, return false.
                return false;
            }
        }
        catch ( IllegalArgumentException iae )
        {
            LOG.error( "The removal of values for the missing '{}' attribute is not possible", upId );
            return false;
        }
    }


    public Iterator<EntryAttribute> iterator()
    {
        return Collections.unmodifiableMap( attributes ).values().iterator();
    }


    /**
     * @see Externalizable#writeExternal(ObjectOutput)<p>
     * 
     * This is the place where we serialize entries, and all theirs
     * elements. the reason why we don't call the underlying methods
     * (<code>ClientAttribute.write(), Value.write()</code>) is that we need
     * access to the registries to read back the values.
     * <p>
     * The structure used to store the entry is the following :
     * <li><b>[DN length]</b> : can be -1 if we don't have a DN, 0 if the 
     * DN is empty, otherwise contains the DN's length.<p> 
     * <b>NOTE :</b>This should be unnecessary, as the DN should always exists
     * <p>
     * </li>
     * <li>
     * <b>DN</b> : The entry's DN. Can be empty (rootDSE=<p>
     * </li>
     * We have to store the UPid, and all the values, if any.
     */
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        // First, the DN
        if ( dn == null )
        {
            // Write an empty DN
            LdapDN.EMPTY_LDAPDN.writeExternal( out );
        }
        else
        {
            // Write the DN
            out.writeObject( dn );
        }
        
        // Then the attributes. 
        if ( attributes == null )
        {
            // A negative number denotes no attributes
            out.writeInt( -1 );
        }
        else
        {
            // Store the attributes' nulber first
            out.writeInt( attributes.size() );
            
            // Iterate through the keys. We store the Attribute
            // here, to be able to restore it in the readExternal :
            // we need access to the registries, which are not available
            // in the ClientAttribute class.
            for ( EntryAttribute attribute:attributes.values() )
            {
                // Store the UP id
                out.writeUTF( attribute.getUpId() );
                
                // The number of values
                int nbValues = attribute.size();
                
                if ( nbValues == 0 ) 
                {
                    out.writeInt( 0 );
                }
                else 
                {
                    out.writeInt( nbValues );

                    for ( Value<?> value:attribute )
                    {
                        out.writeObject( value );
                    }
                }
            }
        }
        
        out.flush();
    }

    
    /**
     * @see Externalizable#readExternal(ObjectInput)
     */
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        // Read the DN
        LdapDN dn = (LdapDN)in.readObject();
        
        // Read the number of attributes
        int nbAttributes = in.readInt();
        
        attributes = new HashMap<String, EntryAttribute>();

        // Read the attributes
        for ( int i = 0; i < nbAttributes; i++ )
        {
            String upId = in.readUTF();
            
            EntryAttribute attribute = new DefaultClientAttribute( upId );
            
            // Read the number of values
            int nbValues = in.readInt();
            
            for ( int j = 0; j < nbValues; j++ )
            {
                Value<?> value = (Value<?>)in.readObject();
                attribute.add( value );
            }
            
            attributes.put( attribute.getId(), attribute );
        }
    }
    
    
    /**
    * Get the hashcode of this ClientEntry.
    *
    * @see java.lang.Object#hashCode()
     */
    public int hashCode()
    {
        int result = 37;
        
        result = result*17 + dn.hashCode();
        
        SortedMap<String, EntryAttribute> sortedMap = new TreeMap<String, EntryAttribute>();
        
        for ( String id:attributes.keySet() )
        {
            sortedMap.put( id, attributes.get( id ) );
        }
        
        for ( String id:sortedMap.keySet() )
        {
            result = result*17 + sortedMap.get( id ).hashCode();
        }
        
        return result;
    }

    
    /**
     * Tells if an entry has a specific ObjectClass value
     * 
     * @param objectClass The ObjectClass we want to check
     * @return <code>true</code> if the ObjectClass value is present 
     * in the ObjectClass attribute
     */
    public boolean hasObjectClass( String objectClass )
    {
        return contains( "objectclass", objectClass );
    }


    /**
     * @see Object#equals(Object)
     */
    public boolean equals( Object o )
    {
        // Short circuit

        if ( this == o )
        {
            return true;
        }
        
        if ( ! ( o instanceof DefaultClientEntry ) )
        {
            return false;
        }
        
        DefaultClientEntry other = (DefaultClientEntry)o;
        
        // Both DN must be equal
        if ( dn == null )
        {
            if ( other.getDn() != null )
            {
                return false;
            }
        }
        else
        {
            if ( !dn.equals( other.getDn() ) )
            {
                return false;
            }
        }
        
        // They must have the same number of attributes
        if ( size() != other.size() )
        {
            return false;
        }
        
        // Each attribute must be equal
        for ( EntryAttribute attribute:other )
        {
            if ( !attribute.equals( this.get( attribute.getId() ) ) )
            {
                return false;
            }
        }
        
        return true;
    }
        

    /**
     * @see Object#toString()
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append( "ClientEntry\n" );
        sb.append( "    dn: " ).append( dn ).append( '\n' );
        
        // First dump the ObjectClass attribute
        if ( containsAttribute( "objectClass" ) )
        {
            EntryAttribute objectClass = get( "objectclass" );
            
            sb.append( objectClass );
        }
        
        if ( attributes.size() != 0 )
        {
            for ( EntryAttribute attribute:attributes.values() )
            {
                if ( !attribute.getId().equals( "objectclass" ) )
                {
                    sb.append( attribute );
                }
            }
        }
        
        return sb.toString();
    }
}
