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
package org.apache.directory.server.core.api;


import java.io.File;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.api.OperationManager;
import org.apache.directory.server.core.api.ReferralManager;
import org.apache.directory.server.core.api.administrative.AccessControlAdministrativePoint;
import org.apache.directory.server.core.api.administrative.CollectiveAttributeAdministrativePoint;
import org.apache.directory.server.core.api.administrative.SubschemaAdministrativePoint;
import org.apache.directory.server.core.api.administrative.TriggerExecutionAdministrativePoint;
import org.apache.directory.server.core.api.changelog.ChangeLog;
import org.apache.directory.server.core.api.event.EventService;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.directory.server.core.api.interceptor.InterceptorChain;
import org.apache.directory.server.core.api.journal.Journal;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.partition.PartitionNexus;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.api.subtree.SubentryCache;
import org.apache.directory.server.core.api.subtree.SubtreeEvaluator;
import org.apache.directory.shared.ldap.codec.api.LdapApiService;
import org.apache.directory.shared.ldap.model.csn.Csn;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.exception.LdapException;
import org.apache.directory.shared.ldap.model.ldif.LdifEntry;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.apache.directory.shared.ldap.model.schema.SchemaManager;
import org.apache.directory.shared.ldap.util.tree.DnNode;


/**
 * A mock DirectoryService used for tests
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class MockDirectoryService implements DirectoryService
{
    int count;
    
    /** The schemaManager */
    SchemaManager schemaManager;
    
    
    public MockDirectoryService()
    {
        this( 0 );
    }
    
    
    public MockDirectoryService( int count )
    {
        this.count = count;
    }
    
    public Hashtable<String, Object> getEnvironment()
    {
        return null;
    }


    public void setEnvironment( Hashtable<String, Object> environment )
    {
    }


    public long revert( long revision ) throws LdapException
    {
        return 0;
    }


    public long revert() throws LdapException
    {
        return 0;
    }


    public PartitionNexus getPartitionNexus()
    {
        return null;
    }


    public InterceptorChain getInterceptorChain()
    {
        return null;
    }


    public void addPartition( Partition partition ) throws LdapException
    {
    }


    public void removePartition( Partition partition ) throws LdapException
    {
    }


    public SchemaManager getSchemaManager()
    {
        return schemaManager;
    }


    public ReferralManager getReferralManager()
    {
        return null;
    }


    public void setReferralManager( ReferralManager referralManager )
    {
    }


    public void setSchemaManager( SchemaManager schemaManager )
    {
        this.schemaManager = schemaManager;
    }


    public SchemaPartition getSchemaPartition()
    {
        return null;
    }


    public void setSchemaPartition( SchemaPartition schemaPartition )
    {
    }


    public void startup() throws LdapException
    {
    }


    public void shutdown() throws LdapException
    {
    }


    public void sync() throws LdapException
    {
    }


    public boolean isStarted()
    {
        return true;
    }


    public LdapContext getJndiContext() throws LdapException
    {
        return null;
    }


    public DirectoryService getDirectoryService()
    {
        return null;
    }


    public void setInstanceId( String instanceId )
    {

    }


    public String getInstanceId()
    {
        return null;
    }


    public Set<? extends Partition> getPartitions()
    {
        return null;
    }


    public void setPartitions( Set<? extends Partition> partitions )
    {
    }


    public boolean isAccessControlEnabled()
    {
        return false;
    }


    public void setAccessControlEnabled( boolean accessControlEnabled )
    {
    }


    public boolean isAllowAnonymousAccess()
    {
        return false;
    }


    public void setAllowAnonymousAccess( boolean enableAnonymousAccess )
    {

    }


    public List<Interceptor> getInterceptors()
    {
        return null;
    }


    public void setInterceptors( List<Interceptor> interceptors )
    {

    }


    public List<LdifEntry> getTestEntries()
    {
        return null;
    }


    public void setTestEntries( List<? extends LdifEntry> testEntries )
    {
    }


    public File getWorkingDirectory()
    {
        return null;
    }


    public void validate()
    {
    }


    public void setShutdownHookEnabled( boolean shutdownHookEnabled )
    {

    }


    public boolean isShutdownHookEnabled()
    {
        return false;
    }


    public void setExitVmOnShutdown( boolean exitVmOnShutdown )
    {

    }


    public boolean isExitVmOnShutdown()
    {
        return false;
    }


    public void setMaxSizeLimit( long maxSizeLimit )
    {

    }


    public long getMaxSizeLimit()
    {
        return 0;
    }


    public void setMaxTimeLimit( int maxTimeLimit )
    {

    }


    public int getMaxTimeLimit()
    {
        return 0;
    }


    public void setSystemPartition( Partition systemPartition )
    {

    }


    public Partition getSystemPartition()
    {
        return null;
    }


    public boolean isDenormalizeOpAttrsEnabled()
    {
        return false;
    }


    public void setDenormalizeOpAttrsEnabled( boolean denormalizeOpAttrsEnabled )
    {

    }
    
    public void setChangeLog( ChangeLog changeLog )
    {
        
    }
    
    public ChangeLog getChangeLog()
    {
        return null;
    }


    public Journal getJournal()
    {
        return null;
    }


    public Entry newEntry( Dn dn ) throws LdapException
    {
        return null;
    }
    
    public Entry newEntry( String ldif, String dn )
    {
        return null;
    }


    public OperationManager getOperationManager()
    {
        return new MockOperationManager( count );
    }


    public CoreSession getSession() throws LdapException
    {
        return null;
    }


    public CoreSession getSession( LdapPrincipal principal ) throws LdapException
    {
        return null;
    }


    public CoreSession getSession( Dn principalDn, byte[] credentials ) throws LdapException
    {
        return null;
    }

    
    public CoreSession getSession( Dn principalDn, byte[] credentials, String saslMechanism, String saslAuthId )
        throws LdapException
    {
        return null;
    }

    public CoreSession getAdminSession()
    {
        return null;
    }

    public EventService getEventService()
    {
        return null;
    }

    public void setEventService( EventService eventService )
    {
    }
    
    
    public boolean isPasswordHidden()
    {
        return false;
    }
         
    
    public void setPasswordHidden( boolean passwordHidden )
    {
    }

    
    public int getMaxPDUSize()
    {
        return Integer.MAX_VALUE;
    }


    public void setMaxPDUSize( int maxPDUSize )
    {
        // Do nothing
    }

    
    public Interceptor getInterceptor( String interceptorName )
    {
        return null;
    }
    
    
    public Csn getCSN()
    {
        return null;
    }
    
    
    public int getReplicaId()
    {
        return 0;
    }
    
    
    public void setReplicaId( int replicaId )
    {
    }

    public void setJournal( Journal journal )
    {
    }

    public String getContextCsn()
    {
        return null;
    }

    public long getSyncPeriodMillis()
    {
        return 0;
    }

    public void setContextCsn( String lastCommittedCsnVal )
    {
    }

    public void setSyncPeriodMillis( long syncPeriodMillis )
    {
    }


    public CacheService getCacheService()
    {
        return null;
    }


    /**
     * {@inheritDoc}
     */
    public DnNode<AccessControlAdministrativePoint> getAccessControlAPCache()
    {
        return null;
    }


    /**
     * {@inheritDoc}
     */
    public DnNode<CollectiveAttributeAdministrativePoint> getCollectiveAttributeAPCache()
    {
        return null;
    }


    /**
     * {@inheritDoc}
     */
    public DnNode<SubschemaAdministrativePoint> getSubschemaAPCache()
    {
        return null;
    }


    /**
     * {@inheritDoc}
     */
    public DnNode<TriggerExecutionAdministrativePoint> getTriggerExecutionAPCache()
    {
        return null;
    }


    public InstanceLayout getInstanceLayout()
    {
        return null;
    }


    public void setInstanceLayout( InstanceLayout instanceLayout )
    {
    }


    /**
     * {@inheritDoc}
     */
    public boolean isPwdPolicyEnabled()
    {
        return false;
    }


    /**
     * {@inheritDoc}
     */
    public DnFactory getDnFactory()
    {
        return null;
    }


    public LdapApiService getLdapCodecService()
    {
        return null;
    }


    @Override
    public SubentryCache getSubentryCache()
    {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public SubtreeEvaluator getEvaluator()
    {
        // TODO Auto-generated method stub
        return null;
    }
}
