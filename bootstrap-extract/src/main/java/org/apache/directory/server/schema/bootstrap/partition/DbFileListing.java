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
package org.apache.directory.server.schema.bootstrap.partition;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.net.URL;


/**
 * Parses the dbfile listing file within this jar.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DbFileListing
{
    Map<String, DbFileType> name2type = new HashMap<String, DbFileType>();
    private static final String BASE_PATH = DbFileListing.class.getName()
        .substring( 0, DbFileListing.class.getName().lastIndexOf( "." ) + 1 ).replace( '.', '/' );


    public DbFileListing() throws IOException
    {
        init();
    }


    private void init() throws IOException
    {

        boolean userIndexMode = false;
        String line;
        BufferedReader in = new BufferedReader( new InputStreamReader( getUniqueResourceAsStream( "DBFILES" ) ) );
        try
        {
            while ( ( line = in.readLine() ) != null )
            {
                if ( line.indexOf( "master.db" ) != -1 )
                {
                    name2type.put( line.trim(), DbFileType.MASTER_FILE );
                    continue;
                }

                if ( line.indexOf( "USER INDICES" ) != -1 )
                {
                    userIndexMode = true;
                    continue;
                }

                if ( userIndexMode )
                {
                    name2type.put( line.trim(), DbFileType.USER_INDEX );
                } else
                {
                    name2type.put( line.trim(), DbFileType.SYSTEM_INDEX );
                }
            }
        }
        finally
        {
            in.close();
        }
    }

    public static InputStream getUniqueResourceAsStream( String resourceName ) throws IOException
    {
        resourceName = BASE_PATH + resourceName;
        URL result = getUniqueResource( resourceName );      
        return result.openStream();
    }

    static URL getUniqueResource( String resourceName )
            throws IOException
    {
        Enumeration<URL> resources = DbFileListing.class.getClassLoader().getResources( resourceName );
        if ( !resources.hasMoreElements() )
        {
            throw new IllegalStateException( "No resource named " + resourceName );
        }
        URL result = resources.nextElement();
        if ( resources.hasMoreElements() )
        {
            StringBuffer buffer = new StringBuffer( "More than one resource named: " ).append( resourceName );
            buffer.append( result ).append( "\n" );
            while ( resources.hasMoreElements() )
            {
                buffer.append( resources.nextElement() ).append( "\n" );
            }
            throw new IllegalStateException( buffer.toString() );
        }
        return result;
    }


    public DbFileType getType( String dbfile )
    {
        return name2type.get( dbfile );
    }


    public Iterator<String> iterator()
    {
        return name2type.keySet().iterator();
    }


    public String getIndexAttributeName( String dbfile )
    {
        if ( dbfile.length() < 10 )
        {
            throw new IllegalArgumentException( "db file must have a relative jar path name of over 10 characters" );
        }

        // remove 'schema/'
        String dbfileName = dbfile.substring( 7 );
        return dbfileName.substring( 0, dbfileName.length() - 3 );
    }


    /**
     * Gets the user indices WITHOUT the system indices.
     *
     * @return set of user index names
     */
    public Set<Object> getIndexedAttributes()
    {
        Set<Object> attributes = new HashSet<Object>();
        Iterator<String> ii = iterator();
        while( ii.hasNext() )
        {
            String name = ii.next();
            if ( name2type.get( name ) == DbFileType.USER_INDEX )
            {
                attributes.add( getIndexAttributeName( name ) );
            }
        }
        return attributes;
    }
}
