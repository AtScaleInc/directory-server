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


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.InvalidNameException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.apache.commons.collections.MultiHashMap;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class store the name-component part or the following BNF grammar (as of
 * RFC2253, par. 3, and RFC1779, fig. 1) : <br> - &lt;name-component&gt; ::=
 * &lt;attributeType&gt; &lt;spaces&gt; '=' &lt;spaces&gt;
 * &lt;attributeValue&gt; &lt;attributeTypeAndValues&gt; <br> -
 * &lt;attributeTypeAndValues&gt; ::= &lt;spaces&gt; '+' &lt;spaces&gt;
 * &lt;attributeType&gt; &lt;spaces&gt; '=' &lt;spaces&gt;
 * &lt;attributeValue&gt; &lt;attributeTypeAndValues&gt; | e <br> -
 * &lt;attributeType&gt; ::= [a-zA-Z] &lt;keychars&gt; | &lt;oidPrefix&gt; [0-9]
 * &lt;digits&gt; &lt;oids&gt; | [0-9] &lt;digits&gt; &lt;oids&gt; <br> -
 * &lt;keychars&gt; ::= [a-zA-Z] &lt;keychars&gt; | [0-9] &lt;keychars&gt; | '-'
 * &lt;keychars&gt; | e <br> - &lt;oidPrefix&gt; ::= 'OID.' | 'oid.' | e <br> -
 * &lt;oids&gt; ::= '.' [0-9] &lt;digits&gt; &lt;oids&gt; | e <br> -
 * &lt;attributeValue&gt; ::= &lt;pairs-or-strings&gt; | '#' &lt;hexstring&gt;
 * |'"' &lt;quotechar-or-pairs&gt; '"' <br> - &lt;pairs-or-strings&gt; ::= '\'
 * &lt;pairchar&gt; &lt;pairs-or-strings&gt; | &lt;stringchar&gt;
 * &lt;pairs-or-strings&gt; | e <br> - &lt;quotechar-or-pairs&gt; ::=
 * &lt;quotechar&gt; &lt;quotechar-or-pairs&gt; | '\' &lt;pairchar&gt;
 * &lt;quotechar-or-pairs&gt; | e <br> - &lt;pairchar&gt; ::= ',' | '=' | '+' |
 * '&lt;' | '&gt;' | '#' | ';' | '\' | '"' | [0-9a-fA-F] [0-9a-fA-F] <br> -
 * &lt;hexstring&gt; ::= [0-9a-fA-F] [0-9a-fA-F] &lt;hexpairs&gt; <br> -
 * &lt;hexpairs&gt; ::= [0-9a-fA-F] [0-9a-fA-F] &lt;hexpairs&gt; | e <br> -
 * &lt;digits&gt; ::= [0-9] &lt;digits&gt; | e <br> - &lt;stringchar&gt; ::=
 * [0x00-0xFF] - [,=+&lt;&gt;#;\"\n\r] <br> - &lt;quotechar&gt; ::= [0x00-0xFF] -
 * [\"] <br> - &lt;separator&gt; ::= ',' | ';' <br> - &lt;spaces&gt; ::= ' '
 * &lt;spaces&gt; | e <br>
 * <br>
 * A RDN is a part of a DN. It can be composed of many types, as in the RDN
 * following RDN :<br>
 * ou=value + cn=other value<br>
 * <br>
 * or <br>
 * ou=value + ou=another value<br>
 * <br>
 * In this case, we have to store an 'ou' and a 'cn' in the RDN.<br>
 * <br>
 * The types are case insensitive. <br>
 * Spaces before and after types and values are not stored.<br>
 * Spaces before and after '+' are not stored.<br>
 * <br>
 * Thus, we can consider that the following RDNs are equals :<br>
 * <br>
 * 'ou=test 1'<br> ' ou=test 1'<br>
 * 'ou =test 1'<br>
 * 'ou= test 1'<br>
 * 'ou=test 1 '<br> ' ou = test 1 '<br>
 * <br>
 * So are the following :<br>
 * <br>
 * 'ou=test 1+cn=test 2'<br>
 * 'ou = test 1 + cn = test 2'<br> ' ou =test 1+ cn =test 2 ' <br>
 * 'cn = test 2 +ou = test 1'<br>
 * <br>
 * but the following are not equal :<br>
 * 'ou=test 1' <br>
 * 'ou=test 1'<br>
 * because we have more than one spaces inside the value.<br>
 * <br>
 * The Rdn is composed of one or more AttributeTypeAndValue (atav) Those atavs
 * are ordered in the alphabetical natural order : a < b < c ... < z As the type
 * are not case sensitive, we can say that a = A
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class Rdn implements Cloneable, Comparable, Serializable, Iterable<AttributeTypeAndValue>
{
    /** The LoggerFactory used by this class */
    protected static final Logger LOG = LoggerFactory.getLogger( Rdn.class );

    /**
    * Declares the Serial Version Uid.
    *
    * @see <a
    *      href="http://c2.com/cgi/wiki?AlwaysDeclareSerialVersionUid">Always
    *      Declare Serial Version Uid</a>
    */
   private static final long serialVersionUID = 1L;

   /** The User Provided RDN */
   private String upName = null;

   /** The normalized RDN */
   private String normName = null;

   /** The starting position of this RDN in the given string from which
    * we have extracted the upName */
   private int start;

   /** The length of this RDN upName */
   private int length;

   /**
    * Stores all couple type = value. We may have more than one type, if the
    * '+' character appears in the AttributeTypeAndValue. This is a TreeSet,
    * because we want the ATAVs to be sorted. An atav may contain more than one
    * value. In this case, the values are String stored in a List.
    */
   private Set<AttributeTypeAndValue> atavs = null;

   /**
    * We also keep a set of types, in order to use manipulations. A type is
    * connected with the atav it represents.
    * 
    * Note : there is no Generic available classes in commons-collection...
    */
   @SuppressWarnings({"unchecked"})
   private Map<String, AttributeTypeAndValue> atavTypes = new MultiHashMap();

   /**
    * We keep the type for a single valued RDN, to avoid the creation of an HashMap
    */
   private String atavType = null;

   /**
    * A simple AttributeTypeAndValue is used to store the Rdn for the simple
    * case where we only have a single type=value. This will be 99.99% the
    * case. This avoids the creation of a HashMap.
    */
   protected AttributeTypeAndValue atav = null;

   /**
    * The number of atavs. We store this number here to avoid complex
    * manipulation of atav and atavs
    */
   private int nbAtavs = 0;

   /** CompareTo() results */
   public static final int UNDEFINED = Integer.MAX_VALUE;

   /** Constant used in comparisons */
   public static final int SUPERIOR = 1;

   /** Constant used in comparisons */
   public static final int INFERIOR = -1;

   /** Constant used in comparisons */
   public static final int EQUALS = 0;


   /**
    * A empty constructor.
    */
   public Rdn()
   {
       // Don't waste space... This is not so often we have multiple
       // name-components in a RDN... So we won't initialize the Map and the
       // treeSet.
       upName = "";
       normName = "";
   }


   /**
    * A constructor that parse a String representing a RDN.
    *
    * @param rdn The String containing the RDN to parse
    * @throws InvalidNameException If the RDN is invalid
    */
   public Rdn( String rdn ) throws InvalidNameException
   {
       start = 0;

       if ( StringTools.isNotEmpty( rdn ) )
       {
           // Parse the string. The Rdn will be updated.
           RdnParser.parse( rdn, this );

           // create the internal normalized form
           // and store the user provided form
           normalize();
           upName = rdn;
           length = rdn.length();
       }
       else
       {
           upName = "";
           normName = "";
           length = 0;
       }
   }


   /**
    * A constructor that constructs a RDN from a type and a value. Constructs
    * an Rdn from the given attribute type and value. The string attribute
    * values are not interpreted as RFC 2253 formatted RDN strings. That is,
    * the values are used literally (not parsed) and assumed to be un-escaped.
    *
    * @param upType The user provided type of the RDN
    * @param upValue The user provided value of the RDN
    * @param normType The normalized provided type of the RDN
    * @param normValue The normalized provided value of the RDN
    * @throws InvalidNameException If the RDN is invalid
    */
   public Rdn( String upType, String normType, String upValue, String normValue ) throws InvalidNameException
   {
       addAttributeTypeAndValue( upType, normType, upValue, normValue );

       upName = upType + '=' + upValue;
       start = 0;
       length = upName.length();
       // create the internal normalized form
       normalize();
   }


   /**
    * A constructor that constructs a RDN from a type and a value. Constructs
    * an Rdn from the given attribute type and value. The string attribute
    * values are not interpreted as RFC 2253 formatted RDN strings. That is,
    * the values are used literally (not parsed) and assumed to be unescaped.
    *
    * @param start 
    *            The type of the RDN
    * @param value
    *            The value of the RDN
    * @throws InvalidNameException
    *             If the RDN is invalid
    */
   /* No protection */ Rdn( int start, int length, String upName, String normName )
   {
       this.start = 0;
       this.length = length;
       this.upName = upName;
       this.normName = normName;
   }

   
   /**
    * Constructs an Rdn from the given rdn. The contents of the rdn are simply
    * copied into the newly created
    *
    * @param rdn
    *            The non-null Rdn to be copied.
    */
   @SuppressWarnings({"unchecked"})
   public Rdn( Rdn rdn )
   {
       nbAtavs = rdn.getNbAtavs();
       this.normName = rdn.normName;
       this.upName = rdn.getUpName();
       this.start = rdn.start;
       this.length = rdn.length;

       switch ( rdn.getNbAtavs() )
       {
           case 0:
               return;

           case 1:
               this.atav = ( AttributeTypeAndValue ) rdn.atav.clone();
               return;

           default:
               // We must duplicate the treeSet and the hashMap
               atavs = new TreeSet<AttributeTypeAndValue>();
               atavTypes = new MultiHashMap();

               for ( AttributeTypeAndValue currentAtav:rdn.atavs )
               {
                   atavs.add( (AttributeTypeAndValue)currentAtav.clone() );
                   atavTypes.put( currentAtav.getUpType(), currentAtav );
               }
               
               return;
       }
   }


   /**
    * Transform the external representation of the current RDN to an internal
    * normalized form where : - types are trimmed and lowercased - values are
    * trimmed and lowercased
    */
   // WARNING : The protection level is left unspecified intentionnaly.
   // We need this method to be visible from the DnParser class, but not
   // from outside this package.
   /* Unspecified protection */void normalize()
   {
       switch ( nbAtavs )
       {
           case 0:
               // An empty RDN
               normName = "";
               break;

           case 1:
               // We have a single AttributeTypeAndValue
               // We will trim and lowercase type and value.
               if ( atav.getNormValue() instanceof String )
               {
                   normName = atav.getNormalizedValue();
               }
               else
               {
                   normName = atav.getNormType() + "=#" + StringTools.dumpHexPairs( (byte[])atav.getNormValue() );
               }

               break;

           default:
               // We have more than one AttributeTypeAndValue
               StringBuffer sb = new StringBuffer();

               boolean isFirst = true;

               for ( AttributeTypeAndValue ata:atavs )
               {
                   if ( isFirst )
                   {
                       isFirst = false;
                   }
                   else
                   {
                       sb.append( '+' );
                   }

                   sb.append( ata.normalize() );
               }

               normName = sb.toString();
               break;
       }
   }


   /**
    * Add a AttributeTypeAndValue to the current RDN
    *
    * @param upType The user provided type of the added RDN.
    * @param type The normalized provided type of the added RDN.
    * @param upValue The user provided value of the added RDN
    * @param value The normalized provided value of the added RDN
    * @throws InvalidNameException
    *             If the RDN is invalid
    */
   // WARNING : The protection level is left unspecified intentionally.
   // We need this method to be visible from the DnParser class, but not
   // from outside this package.
   @SuppressWarnings({"unchecked"})
   /* Unspecified protection */void addAttributeTypeAndValue( String upType, String type, Object upValue, Object value ) 
       throws InvalidNameException
   {
       // First, let's normalize the type
       String normalizedType = StringTools.lowerCaseAscii(type);
       Object normalizedValue = value;

       switch ( nbAtavs )
       {
           case 0:
               // This is the first AttributeTypeAndValue. Just stores it.
               atav = new AttributeTypeAndValue( upType, type, upValue, normalizedValue );
               nbAtavs = 1;
               atavType = normalizedType;
               return;

           case 1:
               // We already have an atav. We have to put it in the HashMap
               // before adding a new one.
               // First, create the HashMap,
               atavs = new TreeSet<AttributeTypeAndValue>();

               // and store the existing AttributeTypeAndValue into it.
               atavs.add( atav );
               atavTypes = new MultiHashMap();
               atavTypes.put( atavType, atav );

               atav = null;

           // Now, fall down to the commmon case
           // NO BREAK !!!

           default:
               // add a new AttributeTypeAndValue
               AttributeTypeAndValue newAtav = new AttributeTypeAndValue( upType, type, upValue, normalizedValue );
               atavs.add( newAtav );
               atavTypes.put( normalizedType, newAtav );

               nbAtavs++;
               break;

       }
   }


   /**
    * Add a AttributeTypeAndValue to the current RDN
    *
    * @param type
    *            The type of the added RDN.
    * @param value
    *            The value of the added RDN
    * @throws InvalidNameException
    *             If the RDN is invalid
    */
   // WARNING : The protection level is left unspecified intentionnaly.
   // We need this method to be visible from the DnParser class, but not
   // from outside this package.
   @SuppressWarnings({"unchecked"})
   /* Unspecified protection */void addAttributeTypeAndValue( AttributeTypeAndValue atav )
   {
       String normalizedType = atav.getNormType();

       switch ( nbAtavs )
       {
           case 0:
               // This is the first AttributeTypeAndValue. Just stores it.
               this.atav = atav;
               nbAtavs = 1;
               atavType = normalizedType;
               return;

           case 1:
               // We already have an atav. We have to put it in the HashMap
               // before adding a new one.
               // First, create the HashMap,
               atavs = new TreeSet<AttributeTypeAndValue>();

               // and store the existing AttributeTypeAndValue into it.
               atavs.add( this.atav );
               atavTypes = new MultiHashMap();
               atavTypes.put( atavType, this.atav );

               this.atav = null;

           // Now, fall down to the commmon case
           // NO BREAK !!!

           default:
               // add a new AttributeTypeAndValue
               atavs.add( atav );
               atavTypes.put( normalizedType, atav );

               nbAtavs++;
               break;

       }
   }

   
   /**
    * Clear the RDN, removing all the AttributeTypeAndValues.
    */
   public void clear()
   {
       atav = null;
       atavs = null;
       atavType = null;
       atavTypes.clear();
       nbAtavs = 0;
       normName = "";
       upName = "";
       start = -1;
       length = 0;
   }


   /**
    * Get the Value of the AttributeTypeAndValue which type is given as an
    * argument.
    *
    * @param type
    *            The type of the NameArgument
    * @return The Value to be returned, or null if none found.
    */
   public Object getValue( String type ) throws InvalidNameException
   {
       // First, let's normalize the type
       String normalizedType = StringTools.lowerCaseAscii( StringTools.trim( type ) );

       switch ( nbAtavs )
       {
           case 0:
               return "";

           case 1:
               if ( StringTools.equals( atav.getNormType(), normalizedType ) )
               {
                   return atav.getNormValue();
               }
               else
               {
                   return "";
               }

           default:
               if ( atavTypes.containsKey( normalizedType ) )
               {
                   Object obj = atavTypes.get( normalizedType );

                   if ( obj instanceof AttributeTypeAndValue )
                   {
                       return ( ( AttributeTypeAndValue ) obj ).getNormValue();
                   }
                   else if ( obj instanceof List )
                   {
                       StringBuffer sb = new StringBuffer();
                       boolean isFirst = true;

                       for ( int i = 0; i < ( ( List ) obj ).size(); i++ )
                       {
                           AttributeTypeAndValue elem = ( AttributeTypeAndValue ) ( ( List ) obj ).get( i );

                           if ( isFirst )
                           {
                               isFirst = false;
                           }
                           else
                           {
                               sb.append( ',' );
                           }

                           sb.append( elem.getNormValue() );
                       }

                       return sb.toString();
                   }
                   else
                   {
                       throw new InvalidNameException( "Bad object stored in the RDN" );
                   }
               }
               else
               {
                   return "";
               }
       }
   }

   
   /** 
    * Get the start position
    *
    * @return The start position in the DN
    */
   public int getStart()
   {
       return start;
   }
   

   /**
    * Get the Rdn length
    *
    * @return The Rdn length
    */
   public int getLength()
   {
       return length;
   }
   
   /**
    * Get the AttributeTypeAndValue which type is given as an argument. If we
    * have more than one value associated with the type, we will return only
    * the first one.
    *
    * @param type
    *            The type of the NameArgument to be returned
    * @return The AttributeTypeAndValue, of null if none is found.
    */
   public AttributeTypeAndValue getAttributeTypeAndValue( String type )
   {
       // First, let's normalize the type
       String normalizedType = StringTools.lowerCaseAscii( StringTools.trim( type ) );

       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               if ( atav.getNormType().equals( normalizedType ) )
               {
                   return atav;
               }
               else
               {
                   return null;
               }

           default:
               if ( atavTypes.containsKey( normalizedType ) )
               {
                   return atavTypes.get( normalizedType );
               }
               else
               {
                   return null;
               }
       }
   }


   /**
    * Retrieves the components of this RDN as an iterator of AttributeTypeAndValue. 
    * The effect on the iterator of updates to this RDN is undefined. If the
    * RDN has zero components, an empty (non-null) iterator is returned.
    *
    * @return an iterator of the components of this RDN, each an AttributeTypeAndValue
    */
   public Iterator<AttributeTypeAndValue> iterator()
   {
       if ( nbAtavs == 1 || nbAtavs == 0 )
       {
           return new Iterator<AttributeTypeAndValue>()
           {
               private boolean hasMoreElement = nbAtavs == 1;


               public boolean hasNext()
               {
                   return hasMoreElement;
               }


               public AttributeTypeAndValue next()
               {
                   AttributeTypeAndValue obj = atav;
                   hasMoreElement = false;
                   return obj;
               }


               public void remove()
               {
                   // nothing to do
               }
           };
       }
       else
       {
           return atavs.iterator();
       }
   }


   /**
    * Clone the Rdn
    */
   @SuppressWarnings({"unchecked"})
   public Object clone()
   {
       try
       {
           Rdn rdn = ( Rdn ) super.clone();

           // The AttributeTypeAndValue is immutable. We won't clone it

           switch ( rdn.getNbAtavs() )
           {
               case 0:
                   break;

               case 1:
                   rdn.atav = ( AttributeTypeAndValue ) this.atav.clone();
                   rdn.atavTypes = atavTypes;
                   break;

               default:
                   // We must duplicate the treeSet and the hashMap
                   rdn.atavTypes = new MultiHashMap();
                   rdn.atavs = new TreeSet<AttributeTypeAndValue>();

                   for ( AttributeTypeAndValue currentAtav:this.atavs )
                   {
                       rdn.atavs.add( (AttributeTypeAndValue)currentAtav.clone() );
                       rdn.atavTypes.put( currentAtav.getUpType(), currentAtav );
                   }

                   break;
           }

           return rdn;
       }
       catch ( CloneNotSupportedException cnse )
       {
           throw new Error( "Assertion failure" );
       }
   }


   /**
    * Compares two RDNs. They are equals if : 
    * <li>their have the same number of NC (AttributeTypeAndValue) 
    * <li>each ATAVs are equals 
    * <li>comparison of type are done case insensitive 
    * <li>each value is equal, case sensitive 
    * <li>Order of ATAV is not important If the RDNs are not equals, a positive number is
    * returned if the first RDN is greater, negative otherwise
    *
    * @param object
    * @return 0 if both rdn are equals. -1 if the current RDN is inferior, 1 if
    *         the current Rdn is superior, UNDEFINED otherwise.
    */
   public int compareTo( Object object )
   {
       if ( object == null )
       {
           return SUPERIOR;
       }

       if ( object instanceof Rdn )
       {
           Rdn rdn = ( Rdn ) object;

           if ( rdn.nbAtavs != nbAtavs )
           {
               // We don't have the same number of ATAVs. The Rdn which
               // has the higher number of Atav is the one which is
               // superior
               return nbAtavs - rdn.nbAtavs;
           }

           switch ( nbAtavs )
           {
               case 0:
                   return EQUALS;

               case 1:
                   return atav.compareTo( rdn.atav );

               default:
                   // We have more than one value. We will
                   // go through all of them.

                   for ( AttributeTypeAndValue current:atavs )
                   {
                       String type = current.getNormType();

                       if ( rdn.atavTypes.containsKey( type ) )
                       {
                           List<AttributeTypeAndValue> atavLocalList = ( List<AttributeTypeAndValue> ) atavTypes.get( type );
                           List<AttributeTypeAndValue> atavParamList = ( List<AttributeTypeAndValue> ) rdn.atavTypes.get( type );

                           // We have to verify that each value of the
                           // first list are present in
                           // the second list
                           for ( AttributeTypeAndValue atavLocal:atavLocalList )
                           {
                               boolean found = false;

                               for ( AttributeTypeAndValue atavParam:atavParamList )
                               {
                                   if ( atavLocal.compareTo( atavParam ) == EQUALS )
                                   {
                                       found = true;
                                       break;
                                   }
                               }

                               if ( !found )
                               {
                                   // The ATAV does not exist in the second RDN
                                   return SUPERIOR;
                               }
                           }
                       }
                       else
                       {
                           // We can't find an atav in the rdn : the current
                           // one is superior
                           return SUPERIOR;
                       }
                   }

                   return EQUALS;
           }
       }
       else
       {
           return UNDEFINED;
       }
   }


   /**
    * Returns a String representation of the RDN
    */
   public String toString()
   {
       return normName == null ? "" : normName;
   }


   /**
    * Returns a String representation of the RDN
    */
   public String getUpName()
   {
       return upName;
   }


   /**
    * Returns The normalized name
    */
   public String getNormName()
   {
       return normName == null ? "" : normName;
   }


   /**
    * Set the User Provided Name
    */
   public void setUpName( String upName )
   {
       this.upName = upName;
   }


   /**
    * @return Returns the nbAtavs.
    */
   public int getNbAtavs()
   {
       return nbAtavs;
   }


   /**
    * Return the unique AttributeTypeAndValue, or the first one of we have more
    * than one
    *
    * @return The first AttributeTypeAndValue of this RDN
    */
   public AttributeTypeAndValue getAtav()
   {
       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               return atav;

           default:
               return (AttributeTypeAndValue)((TreeSet)atavs).first();
       }
   }


   /**
    * Return the user provided type, or the first one of we have more than one (the lowest)
    *
    * @return The first user provided type of this RDN
    */
   public String getUpType()
   {
       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               return atav.getUpType();

           default:
               return ( ( AttributeTypeAndValue )((TreeSet)atavs).first() ).getUpType();
       }
   }

   /**
    * Return the normalized type, or the first one of we have more than one (the lowest)
    *
    * @return The first normalized type of this RDN
    */
   public String getNormType()
   {
       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               return atav.getNormType();

           default:
               return ( ( AttributeTypeAndValue )((TreeSet)atavs).first() ).getNormType();
       }
   }

   /**
    * Return the value, or the first one of we have more than one (the lowest)
    *
    * @return The first value of this RDN
    */
   public Object getValue()
   {
       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               return atav.getNormValue();

           default:
               return ( ( AttributeTypeAndValue )((TreeSet)atavs).first() ).getNormValue();
       }
   }


   /**
    * Return the User Provided value
    * 
    * @return The first User provided value of this RDN
    */
   public Object getUpValue()
   {
       switch ( nbAtavs )
       {
           case 0:
               return null;

           case 1:
               return atav.getUpValue();

           default:
               return ( ( AttributeTypeAndValue )((TreeSet)atavs).first() ).getUpValue();
       }
   }
   
   /**
    * Compares the specified Object with this Rdn for equality. Returns true if
    * the given object is also a Rdn and the two Rdns represent the same
    * attribute type and value mappings. The order of components in
    * multi-valued Rdns is not significant.
    *
    * @param rdn
    *            Rdn to be compared for equality with this Rdn
    * @return true if the specified object is equal to this Rdn
    */
   public boolean equals( Object rdn )
   {
       if ( this == rdn )
       {
           return true;
       }

       if ( !( rdn instanceof Rdn ) )
       {
           return false;
       }

       return compareTo( rdn ) == EQUALS;
   }


   /**
    * Get the number of Attribute type and value of this Rdn
    *
    * @return The number of ATAVs in this Rdn
    */
   public int size()
   {
       return nbAtavs;
   }


   /**
    * Transform the Rdn into an javax.naming.directory.Attributes
    *
    * @return An attributes structure containing all the ATAVs
    */
   public Attributes toAttributes()
   {
       Attributes attributes = new AttributesImpl( true );
       Attribute attribute = null;

       switch ( nbAtavs  )
       {
           case 0 :
               break;

           case 1 :
               attribute = new AttributeImpl( atavType, true );
               attribute.add( atav.getNormValue() );
               attributes.put( attribute );
               break;

           default :
               Iterator types = atavTypes.keySet().iterator();

               while ( types.hasNext() )
               {
                   String type = ( String ) types.next();
                   List values = ( List ) atavTypes.get( type );

                   attribute = new AttributeImpl( type );

                   Iterator<AttributeTypeAndValue> iterValues = values.iterator();

                   while ( iterValues.hasNext() )
                   {
                       AttributeTypeAndValue value = iterValues.next();

                       attribute.add( value.getNormValue() );
                   }

                   attributes.put( attribute );
               }

               break;
       }

       return attributes;
   }


   /**
    * Unescape the given string according to RFC 2253 If in <string> form, a
    * LDAP string representation asserted value can be obtained by replacing
    * (left-to-right, non-recursively) each <pair> appearing in the <string> as
    * follows: replace <ESC><ESC> with <ESC>; replace <ESC><special> with
    * <special>; replace <ESC><hexpair> with the octet indicated by the
    * <hexpair> If in <hexstring> form, a BER representation can be obtained
    * from converting each <hexpair> of the <hexstring> to the octet indicated
    * by the <hexpair>
    *
    * @param value
    *            The value to be unescaped
    * @return Returns a string value as a String, and a binary value as a byte
    *         array.
    * @throws IllegalArgumentException -
    *             When an Illegal value is provided.
    */
   public static Object unescapeValue( String value ) throws IllegalArgumentException
   {
       if ( StringTools.isEmpty( value ) )
       {
           return "";
       }

       char[] chars = value.toCharArray();

       if ( chars[0] == '#' )
       {
           if ( chars.length == 1 )
           {
               // The value is only containing a #
               return StringTools.EMPTY_BYTES;
           }

           if ( ( chars.length % 2 ) != 1 )
           {
               throw new IllegalArgumentException( "This value is not in hex form, we have an odd number of hex chars" );
           }

           // HexString form
           byte[] hexValue = new byte[( chars.length - 1 ) / 2];
           int pos = 0;

           for ( int i = 1; i < chars.length; i += 2 )
           {
               if ( StringTools.isHex( chars, i ) && StringTools.isHex( chars, i + 1 ) )
               {
                   hexValue[pos++] = StringTools.getHexValue( chars[i], chars[i + 1] );
               }
               else
               {
                   throw new IllegalArgumentException( "This value is not in hex form" );
               }
           }

           return hexValue;
       }
       else
       {
           boolean escaped = false;
           boolean isHex = false;
           byte pair = -1;
           int pos = 0;

           byte[] bytes = new byte[chars.length * 6];

           for ( int i = 0; i < chars.length; i++ )
           {
               if ( escaped )
               {
                   escaped = false;

                   switch ( chars[i] )
                   {
                       case '\\':
                       case '"':
                       case '+':
                       case ',':
                       case ';':
                       case '<':
                       case '>':
                       case '#':
                       case '=':
                       case ' ':
                           bytes[pos++] = ( byte ) chars[i];
                           break;

                       default:
                           if ( StringTools.isHex( chars, i ) )
                           {
                               isHex = true;
                               pair = ( (byte)( StringTools.getHexValue( chars[i] ) << 4 ) );
                           }
                   }
               }
               else
               {
                   if ( isHex )
                   {
                       if ( StringTools.isHex( chars, i ) )
                       {
                           pair += StringTools.getHexValue( chars[i] );
                           bytes[pos++] = pair;
                       }
                   }
                   else
                   {
                       switch ( chars[i] )
                       {
                           case '\\':
                               escaped = true;
                               break;

                           // We must not have a special char
                           // Specials are : '"', '+', ',', ';', '<', '>', ' ',
                           // '#' and '='
                           case '"':
                           case '+':
                           case ',':
                           case ';':
                           case '<':
                           case '>':
                           case '#':
                           case '=':
                           case ' ':
                               throw new IllegalArgumentException( "Unescaped special characters are not allowed" );

                           default:
                               byte[] result = StringTools.charToBytes( chars[i] );

                               for ( int j = 0; j < result.length; j++ )
                               {
                                   bytes[pos++] = result[j];
                               }
                       }
                   }
               }
           }

           return StringTools.utf8ToString( bytes, pos );
       }
   }


   /**
    * Transform a value in a String, accordingly to RFC 2253
    *
    * @param value The attribute value to be escaped
    * @return The escaped string value.
    */
   public static String escapeValue( String value )
   {
       if ( StringTools.isEmpty( value ) )
       {
           return "";
       }
       
       char[] chars = value.toCharArray();
       char[] newChars = new char[chars.length * 3];
       int pos = 0;

       for ( int i = 0; i < chars.length; i++ )
       {
           switch ( chars[i] )
           {
               case ' ':
                   if ( ( i > 0 ) && ( i < chars.length - 1 ) )
                   {
                       newChars[pos++] = chars[i];
                   }
                   else
                   {
                       newChars[pos++] = '\\';
                       newChars[pos++] = chars[i];
                   }
                   
                   break;
                   
               case '#':
                   if ( i != 0 )
                   {
                       newChars[pos++] = chars[i];
                   }
                   else
                   {
                       newChars[pos++] = '\\';
                       newChars[pos++] = chars[i];
                   }
                   
                   break;
                   
               case '"':
               case '+':
               case ',':
               case ';':
               case '=':
               case '<':
               case '>':
               case '\\':
                   newChars[pos++] = '\\';
                   newChars[pos++] = chars[i];
                   break;

               case 0x7F:
                   newChars[pos++] = '\\';
                   newChars[pos++] = '7';
                   newChars[pos++] = 'F';
                   break;

               case 0x00:
               case 0x01:
               case 0x02:
               case 0x03:
               case 0x04:
               case 0x05:
               case 0x06:
               case 0x07:
               case 0x08:
               case 0x09:
               case 0x0A:
               case 0x0B:
               case 0x0C:
               case 0x0D:
               case 0x0E:
               case 0x0F:
                   newChars[pos++] = '\\';
                   newChars[pos++] = '0';
                   newChars[pos++] = StringTools.dumpHex( ( byte ) ( chars[i] & 0x0F ) );
                   break;

               case 0x10:
               case 0x11:
               case 0x12:
               case 0x13:
               case 0x14:
               case 0x15:
               case 0x16:
               case 0x17:
               case 0x18:
               case 0x19:
               case 0x1A:
               case 0x1B:
               case 0x1C:
               case 0x1D:
               case 0x1E:
               case 0x1F:
                   newChars[pos++] = '\\';
                   newChars[pos++] = '1';
                   newChars[pos++] = StringTools.dumpHex( ( byte ) ( chars[i] & 0x0F ) );
                   break;

               default:
                   newChars[pos++] = chars[i];

           }
       }

       return new String( newChars, 0, pos );
   }
   
   /**
    * Transform a value in a String, accordingly to RFC 2253
    *
    * @param attrValue
    *            The attribute value to be escaped
    * @return The escaped string value.
    */
   public static String escapeValue( byte[] attrValue )
   {
       if ( StringTools.isEmpty( attrValue ) )
       {
           return "";
       }

       String value = StringTools.utf8ToString( attrValue );
       
       return escapeValue( value );
   }

   /**
    * Gets the hashcode of this rdn.
    *
    * @see java.lang.Object#hashCode()
    */
   public int hashCode()
   {
       int result = 37;

       switch ( nbAtavs )
       {
           case 0:
               // An empty RDN
               break;

           case 1:
               // We have a single AttributeTypeAndValue
               result = result * 17 + atav.hashCode();
               break;

           default:
               // We have more than one AttributeTypeAndValue

               for ( AttributeTypeAndValue ata:atavs )
               {
                   result = result * 17 + ata.hashCode();
               }
       }

       return result;
   }


   /**
    * @see Externalizable#readExternal(ObjectInput)<p>
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
   public void writeExternal( ObjectOutput out ) throws IOException
   {
       out.writeInt( nbAtavs );
       out.writeUTF( upName );
       
       if ( upName.equals( normName ) )
       {
           out.writeUTF( "" );
       }
       else
       {
           out.writeUTF( normName );
       }

       out.writeInt( start );
       out.writeInt( length );

       switch ( nbAtavs )
       {
           case 0 :
               break;

           case 1 :
               out.writeObject( atav );
               break;
               
           default :
               for ( AttributeTypeAndValue atav:atavs )
               {
                   out.writeObject( atav );
               }
           
               break;
       }
       
       out.flush();
   }


   /**
    * @see Externalizable#readExternal(ObjectInput)
    * 
    * We read back the data to create a new RDB. The structure 
    * read is exposed in the {@link Rdn#writeExternal(ObjectOutput)} 
    * method<p>
    */
   public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
   {
       // Read the ATAV number
       nbAtavs = in.readInt();
       
       // Read the UPName
       upName = in.readUTF();
       
       // Read the normName
       normName = in.readUTF();
       
       if ( StringTools.isEmpty( normName ) )
       {
           normName = upName;
       }
       
       start = in.readInt();
       length = in.readInt();

       switch ( nbAtavs )
       {
           case 0 :
               atav = null;
               break;
               
           case 1 :
               atav = (AttributeTypeAndValue)in.readObject();
               atavType = atav.getNormType();
               
               break;
               
           default :
               atavs = new TreeSet<AttributeTypeAndValue>();

               atavTypes = new MultiHashMap();

               for ( int i = 0; i < nbAtavs; i++  )
               {
                   AttributeTypeAndValue atav = (AttributeTypeAndValue)in.readObject();
                   atavs.add( atav );
                   atavTypes.put( atav.getNormType(), atav );
               }
           
               atav = null;
               atavType = null;

               break;
       }
   }
}
