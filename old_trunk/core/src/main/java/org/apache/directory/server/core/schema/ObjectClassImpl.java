/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.schema;


import javax.naming.NamingException;

import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.schema.AbstractSchemaObject;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MutableSchemaObject;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.ObjectClassTypeEnum;


/**
 * An ObjectClass implementation used by the server.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
class ObjectClassImpl extends AbstractSchemaObject implements MutableSchemaObject, ObjectClass
{
    private static final long serialVersionUID = 1L;
    private final ObjectClass[] EMPTY_OC_ARRAY = new ObjectClass[0];
    private final String[] EMPTY_STR_ARRAY = new String[0];
    private final AttributeType[] EMPTY_AT_ARRAY = new AttributeType[0];
    
    private final Registries registries;

    private ObjectClassTypeEnum objectClassType;
    private ObjectClass[] superClasses;

    private String[] mayListOids;
    private AttributeType[] mayList = EMPTY_AT_ARRAY;
    private boolean mayListReloaded;
    
    private String[] mustListOids;
    private AttributeType[] mustList = EMPTY_AT_ARRAY;
    private boolean mustListReloaded;

    private String[] superClassOids;
    
    
    protected ObjectClassImpl( String oid, Registries registries )
    {
        super( oid );
        this.registries = registries;
    }


    public void setDescription( String description )
    {
        super.setDescription( description );
    }


    public void setNames( String[] names )
    {
        super.setNames( names );
    }


    public void setObsolete( boolean obsolete )
    {
        super.setObsolete( obsolete );
    }

    
    public void setSchema( String schema )
    {
        super.setSchema( schema );
    }
    
    
    public AttributeType[] getMayList() throws NamingException
    {
        if ( this.mayListOids == null )
        {
            return EMPTY_AT_ARRAY;
        }

        if ( mayListReloaded )
        {
            for ( int ii = 0; ii < mayListOids.length; ii++ )
            {
                mayList[ii] = registries.getAttributeTypeRegistry().lookup( mayListOids[ii] );
            }
            
            mayListReloaded = false;
        }

        return mayList;
    }
    
    
    public void setMayListOids( String[] mayListOids ) throws NamingException
    {
        if ( mayListOids == null )
        {
            this.mayListOids = EMPTY_STR_ARRAY;
            mayList = EMPTY_AT_ARRAY;
        }
        else
        {
            this.mayListOids = mayListOids;
            mayList = new AttributeType[mayListOids.length];
        }

        mayListReloaded = true;
    }


    public AttributeType[] getMustList() throws NamingException
    {
        if ( mustListOids == null )
        {
            return EMPTY_AT_ARRAY;
        }
        
        if ( mustListReloaded )
        {
            for ( int ii = 0; ii < mustListOids.length; ii++ )
            {
                mustList[ii] = registries.getAttributeTypeRegistry().lookup( mustListOids[ii] );
            }
            
            mustListReloaded = false;
        }
        
        return mustList;
    }
    
    
    public void setMustListOids( String[] mustListOids ) throws NamingException
    {
        if ( mustListOids == null )
        {
            this.mustListOids = EMPTY_STR_ARRAY;
            mustList = EMPTY_AT_ARRAY;
        }
        else
        {
            this.mustListOids = mustListOids;
            mustList = new AttributeType[mustListOids.length];
        }
        
        mustListReloaded = true;
    }


    public ObjectClass[] getSuperClasses() throws NamingException
    {
        if ( superClassOids == null )
        {
            return EMPTY_OC_ARRAY;
        }
        
        for ( int ii = 0; ii < superClassOids.length; ii++ )
        {
            superClasses[ii] = registries.getObjectClassRegistry().lookup( superClassOids[ii] );
        }
        
        return superClasses;
    }

    
    void setSuperClassOids( String[] superClassOids )
    {
        if ( superClassOids == null || superClassOids.length == 0 )
        {
            this.superClassOids = EMPTY_STR_ARRAY;
            this.superClasses = EMPTY_OC_ARRAY;
        }
        else
        {
            this.superClassOids = superClassOids;
            this.superClasses = new ObjectClass[superClassOids.length];
        }
    }
    

    public ObjectClassTypeEnum getType()
    {
        return objectClassType;
    }
    
    
    public boolean isStructural()
    {
        return objectClassType == ObjectClassTypeEnum.STRUCTURAL;
    }


    public boolean isAbstract()
    {
        return objectClassType == ObjectClassTypeEnum.ABSTRACT;
    }

    
    public boolean isAuxiliary()
    {
        return objectClassType == ObjectClassTypeEnum.AUXILIARY;
    }

    
    void setType( ObjectClassTypeEnum objectClassType )
    {
        this.objectClassType = objectClassType;
    }
}
