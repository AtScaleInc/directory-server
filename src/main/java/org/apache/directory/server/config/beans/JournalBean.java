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

/**
 * A class used to store the Journal configuration.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class JournalBean extends BaseAdsBean
{
    /** The journal file name */
    private String journalFileName;
    
    /** The journal working directory */
    private String journalWorkingDir;
    
    /** The journal rotation */
    private int journalRotation;

    /**
     * Create a new JournalBean instance
     */
    public JournalBean()
    {
        // Default to infinite
        journalRotation = 0;
        
        // Not enabled by default
        setEnabled( false );
    }
    
    
    /**
     * @return the fileName
     */
    public String getJournalFileName() 
    {
        return journalFileName;
    }

    
    /**
     * @param journalFileName the journalFileName to set
     */
    public void setJournalFileName( String journalFileName ) 
    {
        this.journalFileName = journalFileName;
    }

    
    /**
     * @return the journal WorkingDir
     */
    public String getJournalWorkingDir() 
    {
        return journalWorkingDir;
    }

    
    /**
     * @param journalWorkingDir the journal WorkingDir to set
     */
    public void setJournalWorkingDir( String journalWorkingDir ) 
    {
        this.journalWorkingDir = journalWorkingDir;
    }

    
    /**
     * @return the journal Rotation
     */
    public int getJournalRotation() 
    {
        return journalRotation;
    }

    
    /**
     * @param journalRotation the journal Rotation to set
     */
    public void setJournalRotation( int journalRotation ) 
    {
        this.journalRotation = journalRotation;
    }
}
