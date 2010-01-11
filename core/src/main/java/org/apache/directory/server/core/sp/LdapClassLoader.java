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
package org.apache.directory.server.core.sp;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A class loader that loads classes from an LDAP DIT.
 * 
 * <p>
 * This loader looks for an configuration entry whose DN is
 * determined by defaultSearchContextsConfig variable. If there is such
 * an entry it gets the search contexts from the entry and searches the 
 * class to be loaded in those contexts.
 * If there is no default search context configuration entry it searches
 * the class in the whole DIT. 
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$ $Date$
 */
public class LdapClassLoader extends ClassLoader
{
    private static final Logger log = LoggerFactory.getLogger( LdapClassLoader.class );
    public static String defaultSearchContextsConfig = "cn=classLoaderDefaultSearchContext,ou=configuration,ou=system";
    
    private LdapDN defaultSearchDn;
    private DirectoryService directoryService;

    
    public LdapClassLoader( DirectoryService directoryService ) throws NamingException
    {
        super( LdapClassLoader.class.getClassLoader() );
        this.directoryService = directoryService;
        defaultSearchDn = new LdapDN( defaultSearchContextsConfig );
        defaultSearchDn.normalize( directoryService.getSchemaManager().getNormalizerMapping() );
    }

    
    private byte[] findClassInDIT( List<LdapDN> searchContexts, String name ) throws ClassNotFoundException
    {
        // Set up the search filter
        BranchNode filter = new AndNode( );
        filter.addNode( new EqualityNode<String>( "fullyQualifiedJavaClassName", 
            new ClientStringValue( name ) ) );
        filter.addNode( new EqualityNode<String>( SchemaConstants.OBJECT_CLASS_AT, 
            new ClientStringValue( ApacheSchemaConstants.JAVA_CLASS_OC ) ) );
        
        try
        {
            for ( LdapDN base : searchContexts )
            {
                EntryFilteringCursor cursor = null;
                try
                {
                    cursor = directoryService.getAdminSession()
                        .search( base, SearchScope.SUBTREE, filter, AliasDerefMode.DEREF_ALWAYS, null );
                    
                    cursor.beforeFirst();
                    if ( cursor.next() ) // there should be only one!
                    {
                        log.debug( "Class {} found under {} search context.", name, base );
                        ServerEntry classEntry = cursor.get();

                        if ( cursor.next() )
                        {
                            ServerEntry other = cursor.get();
                            log.warn( "More than one class found on classpath at locations: {} \n\tand {}", 
                                classEntry, other );
                        }

                        return classEntry.get( "javaClassByteCode" ).getBytes();
                    }
                }
                finally
                {
                    if ( cursor != null )
                    {
                        cursor.close();
                    }
                }
            }
        }
        catch ( Exception e )
        {
            log.error( "Exception while searching the DIT for class: " + name, e );
        }

        throw new ClassNotFoundException();
    }
    
    
    public Class<?> findClass( String name ) throws ClassNotFoundException
    {
        byte[] classBytes = null;

        try 
        {   
            // TODO we should cache this information and register with the event
            // service to get notified if this changes so we can update the cached
            // copy - there's absolutely no reason why we should be performing this
            // lookup every time!!!
            
            ServerEntry configEntry = null;
            
            try
            {
                configEntry = directoryService.getAdminSession().lookup( defaultSearchDn );
            }
            catch ( NamingException e )
            {
                log.debug( "No configuration data found for class loader default search contexts." );
            }
            
            if ( configEntry != null )
            {
                List<LdapDN> searchContexts = new ArrayList<LdapDN>();
                EntryAttribute attr = configEntry.get( "classLoaderDefaultSearchContext" );
                
                for ( Value<?> val : attr )
                {
                    LdapDN dn = new LdapDN( val.getString() );
                    dn.normalize( directoryService.getSchemaManager().getNormalizerMapping() );
                    searchContexts.add( dn );
                }
                
                try
                {
                    classBytes = findClassInDIT( searchContexts, name );
                    
                    log.debug( "Class " + name + " found under default search contexts." );
                }
                catch ( ClassNotFoundException e )
                {
                    log.debug( "Class " + name + " could not be found under default search contexts." );
                }
            }
            
            if ( classBytes == null )
            {
                List<LdapDN> namingContexts = new ArrayList<LdapDN>();
                
                // TODO - why is this an operation????  Why can't we just list these damn things
                // who went stupid crazy making everything into a damn operation  !!!! grrrr 
                Set<String> suffixes = 
                    directoryService.getPartitionNexus().listSuffixes( 
                        new ListSuffixOperationContext( directoryService.getAdminSession() ) );

                for ( String suffix:suffixes )
                {
                    LdapDN dn = new LdapDN( suffix );
                    dn.normalize( directoryService.getSchemaManager().getNormalizerMapping() );
                    namingContexts.add( dn );
                }
                
                classBytes = findClassInDIT( namingContexts, name );
            }
        } 
        catch ( ClassNotFoundException e )
        {
            String msg = "Class " + name + " not found in DIT.";
            log.debug( msg );
            throw new ClassNotFoundException( msg );
        }
        catch ( Exception e ) 
        {
            String msg = "Encountered failure while searching directory for class: " + name;
            log.error( msg + e );
            throw new ClassNotFoundException( msg );
        }
        
        return defineClass( name, classBytes, 0, classBytes.length );
    }
}
