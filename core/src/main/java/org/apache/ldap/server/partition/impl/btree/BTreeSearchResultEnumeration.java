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
package org.apache.ldap.server.partition.impl.btree;


import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.apache.ldap.common.message.LockableAttributesImpl;
import org.apache.ldap.server.enumeration.SearchResultEnumeration;


/**
 * An enumeration that transforms another underlying enumeration over a set of 
 * IndexRecords into an enumeration over a set of SearchResults.  Note that the
 * SearchResult created may not be complete and other parts of the system may
 * modify it before return.  This enumeration simply creates a new copy of the 
 * entry to return stuffing it with the attributes that were specified.  This is
 * all that it does now but this may change later.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class BTreeSearchResultEnumeration implements SearchResultEnumeration
{
    /** Database used to lookup entries from */
    private BTreeContextPartition partition = null;
    /** base of search for relative names */
    /** the attributes to return */
    private final String [] attrIds;
    /** underlying enumeration over IndexRecords */
    private final NamingEnumeration underlying;


    /**
     * Creates an enumeration that returns entries packaged within SearchResults
     * using the search parameters supplied to a search call.
     * 
     * @param attrIds the returned attributes
     * @param underlying the enumeration over IndexRecords
     */
    public BTreeSearchResultEnumeration( String [] attrIds, 
                                    NamingEnumeration underlying,
                                    BTreeContextPartition db )
    {
        this.partition = db;
        this.attrIds = attrIds;
        this.underlying = underlying;
    }
    
    
    /**
     * @see javax.naming.NamingEnumeration#close()
     */
    public void close() throws NamingException
    {
        underlying.close();
    }

    
    /**
     * @see javax.naming.NamingEnumeration#hasMore()
     */
    public boolean hasMore() throws NamingException
    {
        return underlying.hasMore();
    }

   
    /**
     * @see javax.naming.NamingEnumeration#next()
     */
    public Object next() throws NamingException
    {
        IndexRecord rec = ( IndexRecord ) underlying.next();
        Attributes entry;
        String name = partition.getEntryUpdn( rec.getEntryId() );
        
        if ( null == rec.getAttributes() )
        {
            rec.setAttributes( partition.lookup( rec.getEntryId() ) );
        }

        if ( attrIds == null )
        {
            entry = ( Attributes ) rec.getAttributes().clone();
        }
        else
        {
            entry = new LockableAttributesImpl();

            for ( int ii = 0; ii < attrIds.length; ii++ )
            {
                // there is no attribute by that name in the entry so we continue
                if ( null == rec.getAttributes().get( attrIds[ii] ) )
                {
                    continue;
                }

                // clone attribute to stuff into the new resultant entry
                Attribute attr = ( Attribute ) rec.getAttributes().get( attrIds[ii] ).clone();
                entry.put( attr );
            }
        }

        return new PartitionStoreSearchResult( rec.getEntryId(), name, null, entry );
    }

    
    /**
     * @see java.util.Enumeration#hasMoreElements()
     */
    public boolean hasMoreElements()
    {
        return underlying.hasMoreElements();
    }


    /**
     * @see java.util.Enumeration#nextElement()
     */
    public Object nextElement()
    {
        return underlying.nextElement();
    }
}
