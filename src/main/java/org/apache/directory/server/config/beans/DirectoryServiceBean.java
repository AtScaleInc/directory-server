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
package org.apache.directory.server.config.beans;


import java.util.Set;

import org.apache.directory.server.core.authn.PasswordPolicyConfiguration;


/**
 * A class used to store the DirectoryService configuration.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DirectoryServiceBean extends BaseAdsBean
{
    /** The DS instance Id */
    private String directoryServiceId;

    /** The directory instance replication ID */
    private int dsReplicaId;

    /** The flag that tells if the AccessControl system is activated */
    private boolean dsAccessControlEnabled = true;

    /** The flag that tells if Anonymous connections are allowed */
    private boolean dsAllowAnonymousAccess = false;

    /** The flag that tells if DN must be denormalized */
    private boolean dsDenormalizeOpAttrsEnabled = true;

    /** The maximum size of an incoming PDU */
    private int dsMaxPDUSize = 2048;

    /** The flag that tells if the password should be returned as a normal attribute or not */
    private boolean dsPasswordHidden = true;

    /** The delay between two flushes on disk */
    private long dsSyncPeriodMillis = 15000L;

    /** The ldif entries to inject into the server at startup */
    private String dsTestEntries;

    /** The ChangeLog component */
    private ChangeLogBean dsChangeLog;

    /** The journal component */
    private JournalBean dsJournal;

    /** The replication component */
    //private ReplicationBean dsReplication;

    /** The list of declared interceptors */
    private Set<InterceptorBean> interceptors;

    /** The set of associated partitions */
    private Set<PartitionBean> partitions;

    /** The reference to the replication provider component */
    //private ReplicationProviderBean replicationProvider;

    /** The reference to the replication consumer component */
    //private ReplicationConsumerBean replicationConsumer;

    /** The reference to the Password Policy component */
    private PasswordPolicyConfiguration passwordPolicy;


    /**
     * Create a new DnsServerBean instance
     */
    public DirectoryServiceBean()
    {
    }


    /**
     * Sets the ID for this DirectoryService
     * @param directoryServiceId The DirectoryService ID
     */
    public void setDirectoryServiceId( String directoryServiceId )
    {
        this.directoryServiceId = directoryServiceId;
    }


    /**
     * @return The DirectoryService Id
     */
    public String getDirectoryServiceId()
    {
        return directoryServiceId;
    }


    /**
     * @return the replicaId
     */
    public int getDsReplicaId()
    {
        return dsReplicaId;
    }


    /**
     * @param dsReplicaId the replicaId to set
     */
    public void setDsReplicaId( int dsReplicaId )
    {
        if ( ( dsReplicaId < 0 ) || ( dsReplicaId > 999 ) )
        {
            this.dsReplicaId = 0;
        }
        else
        {
            this.dsReplicaId = dsReplicaId;
        }
    }


    /**
     * Returns interceptors in the server.
     *
     * @return the interceptors in the server.
     */
    public Set<InterceptorBean> getInterceptors()
    {
        return interceptors;
    }


    /**
     * Sets the interceptors in the server.
     *
     * @param interceptors the interceptors to be used in the server.
     */
    public void setInterceptors( Set<InterceptorBean> interceptors )
    {
        this.interceptors = interceptors;
    }


    /**
     * @return the dsAccessControlEnabled
     */
    public boolean isDsAccessControlEnabled()
    {
        return dsAccessControlEnabled;
    }


    /**
     * @param dsAccessControlEnabled the dsAccessControlEnabled to set
     */
    public void setDsAccessControlEnabled( boolean dsAccessControlEnabled )
    {
        this.dsAccessControlEnabled = dsAccessControlEnabled;
    }


    /**
     * @return the dsAllowAnonymousAccess
     */
    public boolean isDsAllowAnonymousAccess()
    {
        return dsAllowAnonymousAccess;
    }


    /**
     * @param dsAllowAnonymousAccess the dsAllowAnonymousAccess to set
     */
    public void setDsAllowAnonymousAccess( boolean dsAllowAnonymousAccess )
    {
        this.dsAllowAnonymousAccess = dsAllowAnonymousAccess;
    }


    /**
     * @return the dsDenormalizeOpAttrsEnabled
     */
    public boolean isDsDenormalizeOpAttrsEnabled()
    {
        return dsDenormalizeOpAttrsEnabled;
    }


    /**
     * @param dsDenormalizeOpAttrsEnabled the dsDenormalizeOpAttrsEnabled to set
     */
    public void setDsDenormalizeOpAttrsEnabled( boolean dsDenormalizeOpAttrsEnabled )
    {
        this.dsDenormalizeOpAttrsEnabled = dsDenormalizeOpAttrsEnabled;
    }


    /**
     * @return the dsMaxPDUSize
     */
    public int getDsMaxPDUSize()
    {
        return dsMaxPDUSize;
    }


    /**
     * @param dsMaxPDUSize the dsMaxPDUSize to set
     */
    public void setDsMaxPDUSize( int dsMaxPDUSize )
    {
        this.dsMaxPDUSize = dsMaxPDUSize;
    }


    /**
     * @return the dsPasswordHidden
     */
    public boolean isDsPasswordHidden()
    {
        return dsPasswordHidden;
    }


    /**
     * @param dsPasswordHidden the dsPasswordHidden to set
     */
    public void setDsPasswordHidden( boolean dsPasswordHidden )
    {
        this.dsPasswordHidden = dsPasswordHidden;
    }


    /**
     * @return the dsSyncPeriodMillis
     */
    public long getDsSyncPeriodMillis()
    {
        return dsSyncPeriodMillis;
    }


    /**
     * @param dsSyncPeriodMillis the dsSyncPeriodMillis to set
     */
    public void setDsSyncPeriodMillis( long dsSyncPeriodMillis )
    {
        this.dsSyncPeriodMillis = dsSyncPeriodMillis;
    }


    /**
     * @return the dsTestEntries
     */
    public String getDsTestEntries()
    {
        return dsTestEntries;
    }


    /**
     * @param dsTestEntries the dsTestEntries to set
     */
    public void setDsTestEntries( String dsTestEntries )
    {
        this.dsTestEntries = dsTestEntries;
    }


    /**
     * @return the dsChangeLog
     */
    public ChangeLogBean getDsChangeLog()
    {
        return dsChangeLog;
    }


    /**
     * @param dsChangeLog the dsChangeLog to set
     */
    public void setDsChangeLog( ChangeLogBean dsChangeLog )
    {
        this.dsChangeLog = dsChangeLog;
    }


    /**
     * @return the dsJournal
     */
    public JournalBean getDsJournal()
    {
        return dsJournal;
    }


    /**
     * @param dsJournal the dsJournal to set
     */
    public void setDsJournal( JournalBean dsJournal )
    {
        this.dsJournal = dsJournal;
    }


    /**
     * @return the dsReplication
     *
    public ReplicationBean getDsReplication()
    {
        return dsReplication;
    }


    /**
     * @param dsReplication the dsReplication to set
     *
    public void setDsReplication( ReplicationBean dsReplication )
    {
        this.dsReplication = dsReplication;
    }


    /**
     * @return the partitions
     */
    public Set<PartitionBean> getPartitions()
    {
        return partitions;
    }


    /**
     * @param partitions the partitions to set
     */
    public void setPartitions( Set<PartitionBean> partitions )
    {
        this.partitions = partitions;
    }


    /**
     * @return the replicationProvider
     *
    public ReplicationProviderBean getReplicationProvider()
    {
        return replicationProvider;
    }


    /**
     * @param replicationProvider the replicationProvider to set
     *
    public void setReplicationProvider( ReplicationProviderBean replicationProvider )
    {
        this.replicationProvider = replicationProvider;
    }


    /**
     * @return the replicationConsumer
     *
    public ReplicationConsumerBean getReplicationConsumer()
    {
        return replicationConsumer;
    }


    /**
     * @param replicationConsumer the replicationConsumer to set
     *
    public void setReplicationConsumer( ReplicationConsumerBean replicationConsumer )
    {
        this.replicationConsumer = replicationConsumer;
    }


    /**
     * @return the passwordPolicy
     */
    public PasswordPolicyConfiguration getPasswordPolicy()
    {
        return passwordPolicy;
    }


    /**
     * @param passwordPolicy the passwordPolicy to set
     */
    public void setPasswordPolicy( PasswordPolicyConfiguration passwordPolicy )
    {
        this.passwordPolicy = passwordPolicy;
    }
}
