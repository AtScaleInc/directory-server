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

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.Control;

import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.util.StringTools;


/**
 * A entry to be populated by an ldif parser.
 * 
 * We will have different kind of entries : 
 * - added entries 
 * - deleted entries 
 * - modified entries 
 * - RDN modified entries 
 * - DN modified entries
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class LdifEntry implements Cloneable, Serializable
{
    private static final long serialVersionUID = 2L;
    
    /** Used in toArray() */
    public static final ModificationItemImpl[] EMPTY_MODS = new ModificationItemImpl[0];

    /** the change type */
    private ChangeType changeType;

    /** the modification item list */
    private List<ModificationItemImpl> modificationList;

    private Map<String, ModificationItemImpl> modificationItems;

    /** the dn of the ldif entry */
    private String dn;

    /** The new superior */
    private String newSuperior;

    /** The new rdn */
    private String newRdn;

    /** The delete old rdn flag */
    private boolean deleteOldRdn;

    /** attributes of the entry */
    private Attributes attributes;

    
    /** The control */
    private Control control;

    /**
     * Creates a new Entry object.
     */
    public LdifEntry()
    {
        changeType = ChangeType.Add; // Default LDIF content
        modificationList = new LinkedList<ModificationItemImpl>();
        modificationItems = new HashMap<String, ModificationItemImpl>();
        dn = null;
        attributes = new AttributesImpl( true );
        control = null;
    }

    /**
     * Set the Distinguished Name
     * 
     * @param dn
     *            The Distinguished Name
     */
    public void setDn( String dn )
    {
        this.dn = dn;
    }

    /**
     * Set the modification type
     * 
     * @param changeType
     *            The change type
     * 
     */
    public void setChangeType( ChangeType changeType )
    {
        this.changeType = changeType;
    }

    /**
     * Set the change type
     * 
     * @param changeType
     *            The change type
     */
    public void setChangeType( String changeType )
    {
        if ( "add".equals( changeType ) )
        {
            this.changeType = ChangeType.Add;
        }
        else if ( "modify".equals( changeType ) )
        {
            this.changeType = ChangeType.Modify;
        }
        else if ( "moddn".equals( changeType ) )
        {
            this.changeType = ChangeType.ModDn;
        }
        else if ( "modrdn".equals( changeType ) )
        {
            this.changeType = ChangeType.ModRdn;
        }
        else if ( "delete".equals( changeType ) )
        {
            this.changeType = ChangeType.Delete;
        }
    }

    /**
     * Add a modification item (used by modify operations)
     * 
     * @param modification The modification to be added
     */
    public void addModificationItem( ModificationItemImpl modification )
    {
        if ( changeType == ChangeType.Modify )
        {
            modificationList.add( modification );
            modificationItems.put( modification.getAttribute().getID(), modification );
        }
    }

    /**
     * Add a modification item (used by modify operations)
     * 
     * @param modOp The operation. One of : DirContext.ADD_ATTRIBUTE
     *            DirContext.REMOVE_ATTRIBUTE DirContext.REPLACE_ATTRIBUTE
     * 
     * @param attr The attribute to be added
     */
    public void addModificationItem( int modOp, Attribute attr )
    {
        if ( changeType == ChangeType.Modify )
        {
            ModificationItemImpl item = new ModificationItemImpl( modOp, attr );
            modificationList.add( item );
            modificationItems.put( attr.getID(), item );
        }
    }

    /**
     * Add a modification item
     * 
     * @param modOp
     *            The operation. One of : DirContext.ADD_ATTRIBUTE
     *            DirContext.REMOVE_ATTRIBUTE DirContext.REPLACE_ATTRIBUTE
     * 
     * @param modOp The modification operation value
     * @param id The attribute's ID
     * @param value The attribute's value
     */
    public void addModificationItem( int modOp, String id, Object value )
    {
        if ( changeType == ChangeType.Modify )
        {
            Attribute attr = new AttributeImpl( id, value );

            ModificationItemImpl item = new ModificationItemImpl( modOp, attr );
            modificationList.add( item );
            modificationItems.put( id, item );
        }
    }

    /**
     * Add an attribute to the entry
     * 
     * @param attr
     *            The attribute to be added
     */
    public void addAttribute( Attribute attr )
    {
        attributes.put( attr );
    }

    /**
     * Add an attribute to the entry
     * 
     * @param id
     *            The attribute ID
     * 
     * @param value
     *            The attribute value
     * 
     */
    public void addAttribute( String id, Object value )
    {
        Attribute attr = get( id );

        if ( attr != null )
        {
            attr.add( value );
        }
        else
        {
            attributes.put( id, value );
        }
    }

    /**
     * Add an attribute value to an existing attribute
     * 
     * @param id
     *            The attribute ID
     * 
     * @param value
     *            The attribute value
     * 
     */
    public void putAttribute( String id, Object value )
    {
        Attribute attribute = attributes.get( id );

        if ( attribute != null )
        {
            attribute.add( value );
        }
        else
        {
            attributes.put( id, value );
        }
    }

    /**
     * Get the change type
     * 
     * @return The change type. One of : ADD = 0; MODIFY = 1; MODDN = 2; MODRDN =
     *         3; DELETE = 4;
     */
    public ChangeType getChangeType()
    {
        return changeType;
    }

    /**
     * @return The list of modification items
     */
    public List<ModificationItemImpl> getModificationItems()
    {
        return modificationList;
    }


    /**
     * Gets the modification items as an array.
     *
     * @return modification items as an array.
     */
    public ModificationItemImpl[] getModificationItemsArray()
    {
        return modificationList.toArray( EMPTY_MODS );
    }


    /**
     * @return The entry Distinguished name
     */
    public String getDn()
    {
        return dn;
    }

    /**
     * @return The number of entry modifications
     */
    public int size()
    {
        return modificationList.size();
    }

    /**
     * Returns a attribute given it's id
     * 
     * @param attributeId
     *            The attribute Id
     * @return The attribute if it exists
     */
    public Attribute get( String attributeId )
    {
        if ( "dn".equalsIgnoreCase( attributeId ) )
        {
            return new AttributeImpl( "dn", dn );
        }

        return attributes.get( attributeId );
    }

    /**
     * Get the entry's attributes
     * 
     * @return An Attributes
     */
    public Attributes getAttributes()
    {
        if ( isEntry() )
        {
            return attributes;
        }
        else
        {
            return null;
        }
    }

    /**
     * @return True, if the old RDN should be deleted.
     */
    public boolean isDeleteOldRdn()
    {
        return deleteOldRdn;
    }

    /**
     * Set the flage deleteOldRdn
     * 
     * @param deleteOldRdn
     *            True if the old RDN should be deleted
     */
    public void setDeleteOldRdn( boolean deleteOldRdn )
    {
        this.deleteOldRdn = deleteOldRdn;
    }

    /**
     * @return The new RDN
     */
    public String getNewRdn()
    {
        return newRdn;
    }

    /**
     * Set the new RDN
     * 
     * @param newRdn
     *            The new RDN
     */
    public void setNewRdn( String newRdn )
    {
        this.newRdn = newRdn;
    }

    /**
     * @return The new superior
     */
    public String getNewSuperior()
    {
        return newSuperior;
    }

    /**
     * Set the new superior
     * 
     * @param newSuperior
     *            The new Superior
     */
    public void setNewSuperior( String newSuperior )
    {
        this.newSuperior = newSuperior;
    }

    /**
     * @return True if the entry is an ADD entry
     */
    public boolean isChangeAdd()
    {
        return changeType == ChangeType.Add;
    }

    /**
     * @return True if the entry is a DELETE entry
     */
    public boolean isChangeDelete()
    {
        return changeType == ChangeType.Delete;
    }

    /**
     * @return True if the entry is a MODDN entry
     */
    public boolean isChangeModDn()
    {
        return changeType == ChangeType.ModDn;
    }

    /**
     * @return True if the entry is a MODRDN entry
     */
    public boolean isChangeModRdn()
    {
        return changeType == ChangeType.ModRdn;
    }

    /**
     * @return True if the entry is a MODIFY entry
     */
    public boolean isChangeModify()
    {
        return changeType == ChangeType.Modify;
    }

    /**
     * Tells if the current entry is a added one
     *
     * @return <code>true</code> if the entry is added
     */
    public boolean isEntry()
    {
        return changeType == ChangeType.Add;
    }

    /**
     * @return The associated control, if any
     */
    public Control getControl()
    {
        return control;
    }

    /**
     * Add a control to the entry
     * 
     * @param control
     *            The control
     */
    public void setControl( Control control )
    {
        this.control = control;
    }

    /**
     * Clone method
     * @return a clone of the current instance
     * @exception CloneNotSupportedException If there is some problem while cloning the instance
     */
    public LdifEntry clone() throws CloneNotSupportedException
    {
        LdifEntry clone = (LdifEntry) super.clone();

        if ( modificationList != null )
        {
            for ( ModificationItemImpl modif:modificationList )
            {
                ModificationItemImpl modifClone = new ModificationItemImpl( modif.getModificationOp(), 
                    (Attribute) modif.getAttribute().clone() );
                clone.modificationList.add( modifClone );
            }
        }

        if ( modificationItems != null )
        {
            for ( String key:modificationItems.keySet() )
            {
                ModificationItemImpl modif = modificationItems.get( key );
                ModificationItemImpl modifClone = new ModificationItemImpl( modif.getModificationOp(), 
                    (Attribute) modif.getAttribute().clone() );
                clone.modificationItems.put( key, modifClone );
            }

        }

        if ( attributes != null )
        {
            clone.attributes = (Attributes)attributes.clone();
        }

        return clone;
    }
    
    /**
     * Dumps the attributes
     * @return A String representing the attributes
     */
    private String dumpAttributes()
    {
        StringBuffer sb = new StringBuffer();
        Attribute attribute = null;
        
        try
        {
            for ( NamingEnumeration<? extends Attribute> attrs = attributes.getAll(); 
                  attrs.hasMoreElements(); 
                  attribute = attrs.nextElement())
            {
                if ( attribute == null )
                {
                    sb.append( "        Null attribute\n" );
                    continue;
                }
                
                sb.append( "        ").append( attribute.getID() ).append( ":\n" );
                Object value = null;
                
                for ( NamingEnumeration<?> values = attribute.getAll(); 
                      values.hasMoreElements(); 
                      value = values.nextElement())
                {
                    if ( value instanceof String )
                    {
                        sb.append(  "            " ).append( (String)value ).append('\n' );
                    }
                    else
                    {
                        sb.append(  "            " ).append( StringTools.dumpBytes( (byte[]) value ) ).append('\n' );
                    }
                }
            }
        }
        catch ( NamingException ne )
        {
            return "";
        }
        
        return sb.toString();
    }
    
    /**
     * Dumps the modifications
     * @return A String representing the modifications
     */
    private String dumpModificationItems()
    {
        StringBuffer sb = new StringBuffer();
        
        for ( ModificationItemImpl modif:modificationList )
        {
            sb.append( "            Operation: " );
            
            switch ( modif.getModificationOp() )
            {
                case DirContext.ADD_ATTRIBUTE :
                    sb.append( "ADD\n" );
                    break;
                    
                case DirContext.REMOVE_ATTRIBUTE :
                    sb.append( "REMOVE\n" );
                    break;
                    
                case DirContext.REPLACE_ATTRIBUTE :
                    sb.append( "REPLACE \n" );
                    break;
                    
                default :
                    break; // Do nothing
            }
            
            Attribute attribute = modif.getAttribute();
            
            sb.append( "                Attribute: " ).append( attribute.getID() ).append( '\n' );
            
            if ( attribute.size() != 0 )
            {
                try
                {
                    Object value = null;
                    for ( NamingEnumeration<?> values = attribute.getAll(); 
                          values.hasMoreElements(); 
                          value = values.nextElement() )
                    {
                        if ( value instanceof String )
                        {
                            sb.append(  "                " ).append( (String)value ).append('\n' );
                        }
                        else
                        {
                            sb.append(  "                " ).append( StringTools.dumpBytes( (byte[]) value ) ).append('\n' );
                        }
                    }
                }
                catch ( NamingException ne )
                {
                    return "";
                }
            }
        }
        
        return sb.toString();
    }

    
    /**
     * @return a String representing the Entry
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append( "Entry : " ).append( dn ).append( '\n' );

        if ( control != null )
        {
            sb.append( "    Control : " ).append(  control ).append( '\n' );
        }
        
        switch ( changeType )
        {
            case Add :
                sb.append( "    Change type is ADD\n" );
                sb.append( "        Attributes : \n" );
                sb.append( dumpAttributes() );
                break;
                
            case Modify :
                sb.append( "    Change type is MODIFY\n" );
                sb.append( "        Modifications : \n" );
                sb.append( dumpModificationItems() );
                break;
                
            case Delete :
                sb.append( "    Change type is DELETE\n" );
                break;
                
            case ModDn :
            case ModRdn :
                sb.append( "    Change type is ").append( changeType == ChangeType.ModDn ? "MODDN\n" : "MODRDN\n" );
                sb.append( "    Delete old RDN : " ).append( deleteOldRdn ? "true\n" : "false\n" );
                sb.append( "    New RDN : " ).append( newRdn ).append( '\n' );
                
                if ( !StringTools.isEmpty( newSuperior ) )
                {
                    sb.append( "    New superior : " ).append( newSuperior ).append( '\n' );
                }

                break;
                
            default :
                break; // Do nothing
        }
        
        return sb.toString();
    }
    
    
    /**
     * @see Object#hashCode()
     * 
     * @return the instance's hash code
     */
    public int hashCode()
    {
        int result = 37;

        if ( dn != null )
        {
            result = result*17 + dn.hashCode();
        }
        
        if ( changeType != null )
        {
            result = result*17 + changeType.hashCode();
            
            // Check each different cases
            switch ( changeType )
            {
                case Add :
                    // Checks the attributes
                    if ( attributes != null )
                    {
                        result = result * 17 + attributes.hashCode();
                    }
                    
                    break;

                case Delete :
                    // Nothing to compute
                    break;
                    
                case Modify :
                    if ( modificationList != null )
                    {
                        result = result * 17 + modificationList.hashCode();
                        
                        for ( ModificationItem modification:modificationList )
                        {
                            result = result * 17 + modification.hashCode();
                        }
                    }
                    
                    break;
                    
                case ModDn :
                case ModRdn :
                    result = result * 17 + ( deleteOldRdn ? 1 : -1 ); 
                    
                    if ( newRdn != null )
                    {
                        result = result*17 + newRdn.hashCode();
                    }
                    
                    if ( newSuperior != null )
                    {
                        result = result*17 + newSuperior.hashCode();
                    }
                    
                    break;
                    
                default :
                    break; // do nothing
            }
        }

        if ( control != null )
        {
            result = result * 17 + control.hashCode();
        }

        return result;
    }
    
    /**
     * @see Object#equals(Object)
     * @return <code>true</code> if both values are equal
     */
    public boolean equals( Object o )
    {
        // Basic equals checks
        if ( this == o )
        {
            return true;
        }
        
        if ( o == null )
        {
           return false;
        }
        
        if ( ! (o instanceof LdifEntry ) )
        {
            return false;
        }
        
        LdifEntry entry = (LdifEntry)o;
        
        // Check the DN
        try
        {
            LdapDN thisDn = new LdapDN( dn );
            LdapDN dnEntry = new LdapDN( entry.dn );
            
            if ( !thisDn.equals( dnEntry ) )
            {
                return false;
            }
        }
        catch ( InvalidNameException ine )
        {
            return false;
        }
        
        // Check the changeType
        if ( changeType != entry.changeType )
        {
            return false;
        }
        
        // Check each different cases
        switch ( changeType )
        {
            case Add :
                // Checks the attributes
                if ( attributes == null )
                {
                    if ( entry.attributes != null )
                    {
                        return false;
                    }
                    else
                    {
                        break;
                    }
                }
                
                if ( entry.attributes == null )
                {
                    return false;
                }
                
                if ( attributes.size() != entry.attributes.size() )
                {
                    return false;
                }
                
                if ( !attributes.equals( entry.attributes ) )
                {
                    return false;
                }
                
                break;

            case Delete :
                // Nothing to do, is the DNs are equals
                break;
                
            case Modify :
                // Check the modificationItems list

                // First, deal with special cases
                if ( modificationList == null )
                {
                    if ( entry.modificationList != null )
                    {
                        return false;
                    }
                    else
                    {
                        break;
                    }
                }
                
                if ( entry.modificationList == null )
                {
                    return false;
                }
                
                if ( modificationList.size() != entry.modificationList.size() )
                {
                    return false;
                }
                
                // Now, compares the contents
                int i = 0;
                
                for ( ModificationItemImpl modification:modificationList )
                {
                    if ( ! modification.equals( entry.modificationList.get( i ) ) )
                    {
                        return false;
                    }
                    
                    i++;
                }
                
                break;
                
            case ModDn :
            case ModRdn :
                // Check the deleteOldRdn flag
                if ( deleteOldRdn != entry.deleteOldRdn )
                {
                    return false;
                }
                
                // Check the newRdn value
                try
                {
                    Rdn thisNewRdn = new Rdn( newRdn );
                    Rdn entryNewRdn = new Rdn( entry.newRdn );

                    if ( !thisNewRdn.equals( entryNewRdn ) )
                    {
                        return false;
                    }
                }
                catch ( InvalidNameException ine )
                {
                    return false;
                }
                
                // Check the newSuperior value
                try
                {
                    LdapDN thisNewSuperior = new LdapDN( newSuperior );
                    LdapDN entryNewSuperior = new LdapDN( entry.newSuperior );
                    
                    if ( ! thisNewSuperior.equals(  entryNewSuperior ) )
                    {
                        return false;
                    }
                }
                catch ( InvalidNameException ine )
                {
                    return false;
                }
                
                break;
                
            default :
                break; // do nothing
        }
        
        if ( control != null )
        {
            return control.equals(  entry.control );
        }
        else 
        {
            return entry.control == null;
        }
    }
}
