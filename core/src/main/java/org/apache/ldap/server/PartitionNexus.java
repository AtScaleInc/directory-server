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
package org.apache.ldap.server;


import java.util.Iterator;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;


/**
 * The PartitionNexus is a special type of BackingStore designed to route
 * BackingStore operations to ContextPartitions based on namespace to respective
 * ContextPartitions attached to the nexus at the appropriate naming contexts.
 * These naming contexts are also the suffixes of ContextPartitions.  All
 * entries within a ContextPartition have the same suffix.  The PartitionNexus
 * is a singleton where as ContextPartitions can be many hanging off of
 * different contexts on the nexus.
 *
 * The PartitionNexus routes or proxies BackingStore calls to the appropriate
 * PartitionContext implementation.  It also provides some extended operations
 * for the entire backend apparatus like listing the various naming contexts or
 * partition suffixes within the system.  The nexus is also responsibe for
 * returning the entry Attributes for the root DSE when the approapriate search
 * is conducted: empty filter String and base scope search.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface PartitionNexus extends BackingStore
{
    /**
     * Gets the LdapContext associated with the calling thread.
     * 
     * @return The LdapContext associated with the thread of execution or null
     * if no context is associated with the calling thread.
     */
    LdapContext getLdapContext();

    /**
     * Gets the most significant Dn that exists within the server for any Dn.
     *
     * @param dn the normalized distinguished name to use for matching.
     * @param normalized boolean if true cause the return of a normalized Dn,
     * if false it returns the original user provided distinguished name for 
     * the matched portion of the Dn as it was provided on entry creation.
     * @return a distinguished name representing the matching portion of dn,
     * as originally provided by the user on creation of the matched entry or 
     * the empty string distinguished name if no match was found.
     * @throws NamingException if there are any problems
     */
    Name getMatchedDn( Name dn, boolean normalized ) throws NamingException;

    /**
     * Gets the distinguished name of the suffix for the naming context that 
     * would hold a distinguished name or the empty string Dn if a naming 
     * context is not associated with the name.
     *
     * @param dn to use for finding a suffix.
     * @param normalized if true causes the return of a normalized Dn, but
     * if false it returns the original user provided distinguished name for 
     * the suffix Dn as it was provided on suffix entry creation.
     * @return the suffix portion of dn, or the valid empty string Dn if no
     * naming context was found for dn.
     * @throws NamingException if there are any problems
     */
    Name getSuffix( Name dn, boolean normalized ) throws NamingException;

    /**
     * Gets an iteration over the Name suffixes of the Backends managed by this
     * BackendNexus.
     *
     * @param normalized if true the returned Iterator contains normalized Dn
     * but, if false, it returns the original user provided distinguished names
     * in the Iterator.
     * @return Iteration over ContextPartition suffix names as Names.
     * @throws NamingException if there are any problems
     */
    Iterator listSuffixes( boolean normalized ) throws NamingException;

    /**
     * Registers an ContextPartition with this BackendManager.  Called by each
     * ContextPartition implementation after it has started to register for
     * backend operation calls.  This method effectively puts the 
     * ContextPartition's naming context online.
     *
     * Operations against the naming context should result in an LDAP BUSY
     * result code in the returnValue if the naming context is not online.
     *
     * @param partition ContextPartition component to register with this
     * BackendNexus.
     */
    void register( ContextPartition partition );

    /**
     * Unregisters an ContextPartition with this BackendManager.  Called for each
     * registered Backend right befor it is to be stopped.  This prevents
     * protocol server requests from reaching the Backend and effectively puts
     * the ContextPartition's naming context offline.
     *
     * Operations against the naming context should result in an LDAP BUSY
     * result code in the returnValue if the naming context is not online.
     *
     * @param partition ContextPartition component to unregister with this
     * BackendNexus.
     */
    void unregister( ContextPartition partition );
}
