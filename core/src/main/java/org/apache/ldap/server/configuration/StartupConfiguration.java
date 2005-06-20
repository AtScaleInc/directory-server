/*
 *   @(#) $Id$
 *
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
package org.apache.ldap.server.configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.directory.Attributes;

import org.apache.ldap.server.authn.AnonymousAuthenticator;
import org.apache.ldap.server.authn.AuthenticationService;
import org.apache.ldap.server.authn.SimpleAuthenticator;
import org.apache.ldap.server.authz.AuthorizationService;
import org.apache.ldap.server.exception.ExceptionService;
import org.apache.ldap.server.normalization.NormalizationService;
import org.apache.ldap.server.operational.OperationalAttributeService;
import org.apache.ldap.server.schema.SchemaService;
import org.apache.ldap.server.schema.bootstrap.ApacheSchema;
import org.apache.ldap.server.schema.bootstrap.BootstrapSchema;
import org.apache.ldap.server.schema.bootstrap.CoreSchema;
import org.apache.ldap.server.schema.bootstrap.CosineSchema;
import org.apache.ldap.server.schema.bootstrap.InetorgpersonSchema;
import org.apache.ldap.server.schema.bootstrap.JavaSchema;
import org.apache.ldap.server.schema.bootstrap.SystemSchema;

/**
 * A {@link Configuration} that starts up ApacheDS.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class StartupConfiguration extends Configuration
{
    private static final long serialVersionUID = 4826762196566871677L;

    private File workingDirectory = new File( "server-work" );
    private boolean allowAnonymousAccess = true; // allow by default
    private Set authenticatorConfigurations; // Set<AuthenticatorConfiguration>
    private List interceptorConfigurations; // Set<InterceptorConfiguration>
    
    private Set bootstrapSchemas; // Set<BootstrapSchema>
    private Set contextPartitionConfigurations = new HashSet(); // Set<ContextPartitionConfiguration>
    private Set testEntries = new HashSet(); // Set<Attributes>
    
    protected StartupConfiguration()
    {
        setDefaultAuthenticatorConfigurations();
        setDefaultBootstrapSchemas();
        setDefaultInterceptorConfigurations();
    }

    private void setDefaultAuthenticatorConfigurations()
    {
        Set set; 
        
        // Set default authenticator configurations
        set = new HashSet();
        
        MutableAuthenticatorConfiguration authCfg;

        // Anonymous
        authCfg = new MutableAuthenticatorConfiguration();
        authCfg.setName( "Anonymous" );
        authCfg.setAuthenticator( new AnonymousAuthenticator() );
        set.add( authCfg );

        // Simple
        authCfg = new MutableAuthenticatorConfiguration();
        authCfg.setName( "Simple" );
        authCfg.setAuthenticator( new SimpleAuthenticator() );
        set.add( authCfg );
        
        setAuthenticatorConfigurations( set );
    }

    private void setDefaultBootstrapSchemas()
    {
        Set set;
        // Set default bootstrap schemas
        set = new HashSet();
        
        set.add( new CoreSchema() );
        set.add( new CosineSchema() );        
        set.add( new ApacheSchema() );        
        set.add( new InetorgpersonSchema() );        
        set.add( new JavaSchema() );        
        set.add( new SystemSchema() );
        
        setBootstrapSchemas( set );
    }

    private void setDefaultInterceptorConfigurations()
    {
        // Set default interceptor chains
        InterceptorConfiguration interceptorCfg;
        List list = new ArrayList();
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "normalizationService" );
        interceptorCfg.setInterceptor( new NormalizationService() );
        list.add( interceptorCfg );
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "authenticationService" );
        interceptorCfg.setInterceptor( new AuthenticationService() );
        list.add( interceptorCfg );
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "authorizationService" );
        interceptorCfg.setInterceptor( new AuthorizationService() );
        list.add( interceptorCfg );
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "exceptionService" );
        interceptorCfg.setInterceptor( new ExceptionService() );
        list.add( interceptorCfg );
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "schemaService" );
        interceptorCfg.setInterceptor( new SchemaService() );
        list.add( interceptorCfg );
        
        interceptorCfg = new MutableInterceptorConfiguration();
        interceptorCfg.setName( "operationalAttributeService" );
        interceptorCfg.setInterceptor( new OperationalAttributeService() );
        list.add( interceptorCfg );
        
        setInterceptorConfigurations( list );
    }

    /**
     * Returns {@link AuthenticatorConfiguration}s to use for authenticating clients.
     */
    public Set getAuthenticatorConfigurations()
    {
        return ConfigurationUtil.getClonedSet( authenticatorConfigurations );
    }

    /**
     * Sets {@link AuthenticatorConfiguration}s to use for authenticating clients.
     */
    protected void setAuthenticatorConfigurations( Set authenticatorConfigurations )
    {
        Set newSet = ConfigurationUtil.getTypeSafeSet(
                authenticatorConfigurations, AuthenticatorConfiguration.class );
        
        Set names = new HashSet();
        Iterator i = newSet.iterator();
        while( i.hasNext() )
        {
            AuthenticatorConfiguration cfg = ( AuthenticatorConfiguration ) i.next();
            cfg.validate();
            
            String name = cfg.getName();
            if( names.contains( name ) )
            {
                throw new ConfigurationException( "Duplicate authenticator name: " + name );
            }
            names.add( name );
        }
        
        this.authenticatorConfigurations = newSet;
    }

    /**
     * Returns {@link BootstrapSchema}s to load while bootstrapping.
     */
    public Set getBootstrapSchemas()
    {
        return ConfigurationUtil.getClonedSet( bootstrapSchemas );
    }

    /**
     * Sets {@link BootstrapSchema}s to load while bootstrapping.
     */
    protected void setBootstrapSchemas( Set bootstrapSchemas )
    {
        this.bootstrapSchemas = ConfigurationUtil.getTypeSafeSet(
                bootstrapSchemas, BootstrapSchema.class );
    }

    /**
     * Returns {@link ContextPartitionConfiguration}s to configure context partitions.
     */
    public Set getContextPartitionConfigurations()
    {
        return ConfigurationUtil.getClonedSet( contextPartitionConfigurations );
    }

    /**
     * Sets {@link ContextPartitionConfiguration}s to configure context partitions.
     */
    protected void setContextPartitionConfigurations( Set contextParitionConfigurations )
    {
        Set newSet = ConfigurationUtil.getTypeSafeSet(
                contextParitionConfigurations, ContextPartitionConfiguration.class );
        
        Set names = new HashSet();
        Iterator i = newSet.iterator();
        while( i.hasNext() )
        {
            ContextPartitionConfiguration cfg = ( ContextPartitionConfiguration ) i.next();
            cfg.validate();

            String name = cfg.getName();
            if( names.contains( name ) )
            {
                throw new ConfigurationException( "Duplicate partition name: " + name );
            }
            names.add( name );
        }
        
        this.contextPartitionConfigurations = newSet;
    }

    /**
     * Returns <tt>true</tt> if anonymous access is allowed.
     */
    public boolean isAllowAnonymousAccess()
    {
        return allowAnonymousAccess;
    }

    /**
     * Sets whether to allow anonymous access or not
     */
    protected void setAllowAnonymousAccess( boolean enableAnonymousAccess )
    {
        this.allowAnonymousAccess = enableAnonymousAccess;
    }

    /**
     * Returns interceptor chain.
     */
    public List getInterceptorConfigurations()
    {
        return ConfigurationUtil.getClonedList( interceptorConfigurations );
    }

    /**
     * Sets interceptor chain.
     */
    protected void setInterceptorConfigurations( List interceptorConfigurations )
    {
        List newList = ConfigurationUtil.getTypeSafeList(
                interceptorConfigurations, InterceptorConfiguration.class );
        
        Set names = new HashSet();
        Iterator i = newList.iterator();
        while( i.hasNext() )
        {
            InterceptorConfiguration cfg = ( InterceptorConfiguration ) i.next();
            cfg.validate();

            String name = cfg.getName();
            if( names.contains( name ) )
            {
                throw new ConfigurationException( "Duplicate interceptor name: " + name );
            }
            names.add( name );
        }

        this.interceptorConfigurations = interceptorConfigurations;
    }

    /**
     * Returns test directory entries({@link Attributes}) to be loaded while
     * bootstrapping.
     */
    public Set getTestEntries()
    {
        return ConfigurationUtil.getClonedAttributesSet( testEntries );
    }

    /**
     * Sets test directory entries({@link Attributes}) to be loaded while
     * bootstrapping.
     */
    protected void setTestEntries( Set testEntries )
    {
         testEntries = ConfigurationUtil.getClonedAttributesSet(
                ConfigurationUtil.getTypeSafeSet( testEntries, Attributes.class ) );
         
         Iterator i = testEntries.iterator();
         while( i.hasNext() )
         {
             Attributes entry = ( Attributes ) i.next();
             if( entry.get( "dn" ) == null )
             {
                 throw new ConfigurationException( "Test entries must have DN attributes" );
             }
         }

         this.testEntries = testEntries;
    }

    /**
     * Returns working directory (counterpart of <tt>var/lib</tt>).
     */
    public File getWorkingDirectory()
    {
        return workingDirectory;
    }

    /**
     * Sets working directory (counterpart of <tt>var/lib</tt>).
     */
    protected void setWorkingDirectory( File workingDirectory )
    {
        workingDirectory.mkdirs();
        if( !workingDirectory.exists() )
        {
            throw new ConfigurationException( "Working directory '" + workingDirectory + "' doesn't exist." );
        }
        if( !workingDirectory.isDirectory() )
        {
            throw new ConfigurationException( "Working directory '" + workingDirectory + "' is not a directory." );
        }

        this.workingDirectory = workingDirectory;
    }
    
    public void validate()
    {
        setWorkingDirectory( workingDirectory );
    }
}
