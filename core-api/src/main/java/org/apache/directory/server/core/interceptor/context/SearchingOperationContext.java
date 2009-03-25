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
package org.apache.directory.server.core.interceptor.context;


import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.NoSuchAttributeException;
import javax.naming.directory.SearchControls;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;
import org.apache.directory.shared.ldap.schema.SchemaUtils;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.directory.shared.ldap.filter.SearchScope.ONELEVEL;


/**
 * A context used for search related operations and used by all 
 * the Interceptors.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public abstract class SearchingOperationContext extends AbstractOperationContext
{
    /** The LoggerFactory used by this Interceptor */
    private static Logger LOG = LoggerFactory.getLogger( SearchingOperationContext.class );
    
    private static String[] EMPTY_STR_ARRAY = new String[0];

    /** A flag describing the way alias should be handled */
    protected AliasDerefMode aliasDerefMode = AliasDerefMode.DEREF_ALWAYS;

    /** The sizeLimit for this search operation */
    protected long sizeLimit = 0;
    
    /** The timeLimit for this search operation */
    protected int timeLimit = 0;
    
    /** The scope for this search : default to One Level */
    protected SearchScope scope = ONELEVEL;

    /** A flag set if the returned attributes set contains '+' */
    protected boolean allOperationalAttributes = false;
    
    /** A flag set if the returned attributes set contains '*' */
    protected boolean allUserAttributes = false;
    
    /** A flag set if the returned attributes set contains '1.1' */
    protected boolean noAttributes = false;
    
    /** A set containing the returning attributeTypesOptions */
    protected Set<AttributeTypeOptions> returningAttributes; 
    
    /** A flag if the search operation is abandoned */
    protected boolean abandoned = false;
    
    
    /**
     * Creates a new instance of ListOperationContext.
     */
    public SearchingOperationContext( CoreSession session )
    {
        super( session );
    }


    /**
     * Creates a new instance of ListOperationContext.
     *
     * @param dn The DN to get the suffix from
     */
    public SearchingOperationContext( CoreSession session, LdapDN dn )
    {
        super( session, dn );
    }


    /**
     * Creates a new instance of ListOperationContext.
     *
     * @param dn The DN to get the suffix from
     * @param aliasDerefMode the alias dereferencing mode to use
     */
    public SearchingOperationContext( CoreSession session, LdapDN dn, AliasDerefMode aliasDerefMode )
    {
        super( session, dn );
        this.aliasDerefMode = aliasDerefMode;
    }

    
    protected void setReturningAttributes( Collection<String> attributesIds ) 
        throws Exception
    {
        setReturningAttributes( attributesIds.toArray( EMPTY_STR_ARRAY ) );
    }
    
    
    protected void setReturningAttributes( String[] attributesIds ) throws Exception
    {
        if ( attributesIds != null && attributesIds.length != 0 )
        {
            returningAttributes = new HashSet<AttributeTypeOptions>();
            
            for ( String returnAttribute : attributesIds )
            {
                if ( returnAttribute.equals( SchemaConstants.NO_ATTRIBUTE ) )
                {
                    noAttributes = true;
                    continue;
                }
                
                if ( returnAttribute.equals( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
                {
                    allOperationalAttributes = true;
                    continue;
                }
                
                if ( returnAttribute.equals( SchemaConstants.ALL_USER_ATTRIBUTES ) )
                {
                    allUserAttributes = true;
                    continue;
                }
                
                try
                {
                    String id = SchemaUtils.stripOptions( returnAttribute );
                    Set<String> options = SchemaUtils.getOptions( returnAttribute );
                    
                    AttributeType attributeType = session.getDirectoryService()
                        .getRegistries().getAttributeTypeRegistry().lookup( id );
                    AttributeTypeOptions attrOptions = new AttributeTypeOptions( attributeType, options );
                    
                    returningAttributes.add( attrOptions );
                }
                catch ( NoSuchAttributeException nsae )
                {
                    LOG.warn( "Requested attribute {} does not exist in the schema, it will be ignored", returnAttribute );
                    // Unknown attributes should be silently ignored, as RFC 2251 states
                }
            }
        }
    }
    
    
    /**
     * Creates a new instance of ListOperationContext.
     *
     * @param dn The DN to get the suffix from
     * @param aliasDerefMode the alias dereferencing mode to use
     * @throws NamingException 
     */
    public SearchingOperationContext( CoreSession session, LdapDN dn, AliasDerefMode aliasDerefMode, 
        SearchControls searchControls ) throws Exception
    {
        super( session, dn );
        this.aliasDerefMode = aliasDerefMode;
        this.scope = SearchScope.getSearchScope( searchControls );
        this.timeLimit = searchControls.getTimeLimit();
        this.sizeLimit = searchControls.getCountLimit();
        
        if ( searchControls.getReturningAttributes() != null )
        {
            setReturningAttributes( searchControls.getReturningAttributes() );
        }
    }

    
    /**
     * Creates a new instance of a SearchingOperationContext using one level 
     * scope, with attributes to return.
     *
     * @param dn The DN to get the suffix from
     * @param aliasDerefMode the alias dereferencing mode to use
     * @throws NamingException 
     */
    public SearchingOperationContext( CoreSession session, LdapDN dn, AliasDerefMode aliasDerefMode,
        Set<AttributeTypeOptions> returningAttributes )
    {
        super( session, dn );
        this.aliasDerefMode = aliasDerefMode;
        this.returningAttributes = returningAttributes;
    }


    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "ListOperationContext with DN '" + getDn().getUpName() + "'";
    }

    
    public AliasDerefMode getAliasDerefMode()
    {
        return aliasDerefMode;
    }


    /**
     * @param sizeLimit the sizeLimit to set
     */
    public void setSizeLimit( long sizeLimit )
    {
        this.sizeLimit = sizeLimit;
    }


    /**
     * @return the sizeLimit
     */
    public long getSizeLimit()
    {
        return sizeLimit;
    }


    /**
     * @param timeLimit the timeLimit to set
     */
    public void setTimeLimit( int timeLimit )
    {
        this.timeLimit = timeLimit;
    }


    /**
     * @return the timeLimit
     */
    public int getTimeLimit()
    {
        return timeLimit;
    }


    /**
     * @param scope the scope to set
     */
    public void setScope( SearchScope scope )
    {
        this.scope = scope;
    }


    /**
     * @return the scope
     */
    public SearchScope getScope()
    {
        return scope;
    }


    /**
     * @param allOperationalAttributes the allOperationalAttributes to set
     */
    public void setAllOperationalAttributes( boolean allOperationalAttribute )
    {
        this.allOperationalAttributes = allOperationalAttribute;
    }


    /**
     * @return the allOperationalAttributes
     */
    public boolean isAllOperationalAttributes()
    {
        return allOperationalAttributes;
    }


    /**
     * @param allUserAttributes the allUserAttributes to set
     */
    public void setAllUserAttributes( boolean allUserAttributes )
    {
        this.allUserAttributes = allUserAttributes;
    }


    /**
     * @return the allUserAttributes
     */
    public boolean isAllUserAttributes()
    {
        return allUserAttributes;
    }


    /**
     * @param noAttributes the noAttributes to set
     */
    public void setNoAttributes( boolean noAttributes )
    {
        this.noAttributes = noAttributes;
    }


    /**
     * @return the noAttributes
     */
    public boolean isNoAttributes()
    {
        return noAttributes;
    }


    /**
     * @param returningAttributes the returningAttributes to set
     */
    public void setReturningAttributes( Set<AttributeTypeOptions> returningAttributes )
    {
        this.returningAttributes = returningAttributes;
    }


    /**
     * @return the returningAttributes
     */
    public Set<AttributeTypeOptions> getReturningAttributes()
    {
        return returningAttributes;
    }

    
    /**
     * Creates a new SearchControls object populated with the parameters 
     * contained in this SearchOperationContext in normalized form.
     *
     * @return a new SearchControls object
     */
    public SearchControls getSearchControls()
    {
        return getSearchControls( false );
    }
    
    
    /**
     * Creates a new SearchControls object populated with the parameters 
     * contained in this SearchOperationContext.
     *
     * @param denormalized true if attribute values are <b>not</b> normalized
     * @return a new SearchControls object
     */
    public SearchControls getSearchControls( boolean denormalized )
    {
        SearchControls controls = new SearchControls();
        controls.setCountLimit( sizeLimit );
        controls.setSearchScope( scope.getJndiScope() );
        controls.setTimeLimit( timeLimit );

        Set<String> allReturningAttributes = new HashSet<String>();
        
        if ( noAttributes )
        {
            allReturningAttributes.add( SchemaConstants.NO_ATTRIBUTE );
        }
        
        if ( allUserAttributes )
        {
            allReturningAttributes.add( SchemaConstants.ALL_USER_ATTRIBUTES );
        }
        
        if ( allOperationalAttributes )
        {
            allReturningAttributes.add( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES );
        }
        
        if ( returningAttributes != null )
        {
            for ( AttributeTypeOptions at : returningAttributes )
            {
                if ( denormalized )
                {
                    allReturningAttributes.add( at.getAttributeType().getName() );
                }
                else
                {
                    allReturningAttributes.add( at.getAttributeType().getOid() );
                }
            }
        }
        
        if ( allReturningAttributes.size() > 0 )
        {
            controls.setReturningAttributes( allReturningAttributes.toArray( ArrayUtils.EMPTY_STRING_ARRAY ) );
        }
        
        return controls;
    }


    /**
     * @param abandoned the abandoned to set
     */
    public void setAbandoned( boolean abandoned )
    {
        this.abandoned = abandoned;
    }


    /**
     * @return the abandoned
     */
    public boolean isAbandoned()
    {
        return abandoned;
    }
}
