/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.integ.state;


import org.apache.directory.server.core.integ.DirectoryServiceFactory;
import org.apache.directory.server.core.integ.InheritableSettings;
import org.apache.directory.server.core.integ.SetupMode;
import org.junit.internal.runners.TestClass;
import org.junit.internal.runners.TestMethod;
import org.junit.runner.notification.RunNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;


/**
 * A test service state where the server is running and has not been used for
 * any integration test since it was created.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class StartedPristineState implements TestServiceState
{
    private static final Logger LOG = LoggerFactory.getLogger( StartedPristineState.class );
    private final TestServiceContext context;


    public StartedPristineState( TestServiceContext context )
    {
        this.context = context;
    }


    public void create( DirectoryServiceFactory factory )
    {
        throw new IllegalStateException( "Cannot create new instance while service is running." );
    }


    public void destroy()
    {
        throw new IllegalStateException( "Cannot destroy started service." );
    }


    public void cleanup()
    {
        throw new IllegalStateException( "Cannot cleanup started service." );
    }


    public void startup()
    {
        throw new IllegalStateException( "Cannot startup started service." );
    }


    public void shutdown() throws NamingException
    {
        LOG.debug( "calling shutdown()" );
        context.getService().shutdown();
        context.setState( context.getStoppedPristineState() );
    }


    public void test( TestClass testClass, TestMethod testMethod, RunNotifier notifier, InheritableSettings settings )
    {
        LOG.debug( "calling test(): {}", settings.getDescription().getDisplayName() );

        if ( settings.getMode() == SetupMode.NOSERVICE || testMethod.isIgnored() )
        {
            // no state change here
            TestServiceContext.invokeTest( testClass, testMethod, notifier, settings.getDescription() );
            return;
        }

        try
        {
            context.getService().getChangeLog().tag();
        }
        catch ( NamingException e )
        {
            // @TODO - we might want to check the revision of the service before
            // we presume that it has been soiled.  Some tests may simply peform
            // some read operations or checks on the service and may not alter it
            context.setState( context.getStartedDirtyState() );

            notifier.testAborted( settings.getDescription(), e );
            return;
        }

        TestServiceContext.invokeTest( testClass, testMethod, notifier, settings.getDescription() );

        // @TODO - we might want to check the revision of the service before
        // we presume that it has been soiled.  Some tests may simply peform
        // some read operations or checks on the service and may not alter it
        context.setState( context.getStartedDirtyState() );
    }


    public void revert() throws NamingException
    {
        throw new IllegalStateException( "Cannot revert already pristine service." );
    }
}