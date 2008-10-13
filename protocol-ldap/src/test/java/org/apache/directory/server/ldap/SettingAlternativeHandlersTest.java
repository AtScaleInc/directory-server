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
package org.apache.directory.server.ldap;


import junit.framework.TestCase;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.server.ldap.handlers.AbandonHandler;
import org.apache.directory.server.ldap.handlers.AddHandler;
import org.apache.directory.server.ldap.handlers.BindHandler;
import org.apache.directory.server.ldap.handlers.CompareHandler;
import org.apache.directory.server.ldap.handlers.DeleteHandler;
import org.apache.directory.server.ldap.handlers.ModifyDnHandler;
import org.apache.directory.server.ldap.handlers.ModifyHandler;
import org.apache.directory.server.ldap.handlers.SearchHandler;
import org.apache.directory.server.ldap.handlers.UnbindHandler;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.message.AbandonRequest;
import org.apache.directory.shared.ldap.message.AddRequest;
import org.apache.directory.shared.ldap.message.BindRequest;
import org.apache.directory.shared.ldap.message.CompareRequest;
import org.apache.directory.shared.ldap.message.DeleteRequest;
import org.apache.directory.shared.ldap.message.ModifyDnRequest;
import org.apache.directory.shared.ldap.message.ModifyRequest;
import org.apache.directory.shared.ldap.message.SearchRequest;
import org.apache.directory.shared.ldap.message.UnbindRequest;
import org.apache.mina.core.session.IoSession;


/**
 * This test is simply used to test that handlers can be set properly.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SettingAlternativeHandlersTest extends TestCase
{
    LdapService ldapService;


    public void setUp() throws Exception
    {
        ldapService = new LdapService();
        
        if ( getName().equals( "testAlternativeConfiguration" ) )
        {
            ldapService.setAbandonHandler( new BogusAbandonHandler() );
            ldapService.setAddHandler( new BogusAddHandler() );
            ldapService.setBindHandler( new BogusBindHandler() );
            ldapService.setCompareHandler( new BogusCompareHandler() );
            ldapService.setDeleteHandler( new BogusDeleteHandler() );
            ldapService.setModifyDnHandler( new BogusModifyDnHandler() );
            ldapService.setModifyHandler( new BogusModifyHandler() );
            ldapService.setSearchHandler( new BogusSearchHandler() );
            ldapService.setUnbindHandler( new BogusUnbindHandler() );
        }
    }


    /**
     * Tests to make sure all the default handlers are kicking in properly with
     * the right request type.
     *
     * @throws LdapNamingException if there are problems initializing the
     * provider
     */
    public void testDefaultOperation() throws LdapNamingException
    {
        assertEquals( ldapService.getName(), LdapService.SERVICE_NAME );
    }


    /**
     * Tests to make sure handlers for alternative configurations are kicking
     * in properly with the right request type.
     *
     * @throws LdapNamingException if there are problems initializing the
     * provider
     */
    public void testAlternativeConfiguration() throws LdapNamingException
    {
        assertEquals( ldapService.getAbandonHandler().getClass(), BogusAbandonHandler.class  );
        assertEquals( ldapService.getAddHandler().getClass(), BogusAddHandler.class  );
        assertEquals( ldapService.getBindHandler().getClass(), BogusBindHandler.class  );
        assertEquals( ldapService.getCompareHandler().getClass(), BogusCompareHandler.class  );
        assertEquals( ldapService.getDeleteHandler().getClass(), BogusDeleteHandler.class  );
        assertEquals( ldapService.getModifyDnHandler().getClass(), BogusModifyDnHandler.class  );
        assertEquals( ldapService.getModifyHandler().getClass(), BogusModifyHandler.class  );
        assertEquals( ldapService.getSearchHandler().getClass(), BogusSearchHandler.class  );
        assertEquals( ldapService.getUnbindHandler().getClass(), BogusUnbindHandler.class  );
        assertEquals( ldapService.getName(), LdapService.SERVICE_NAME );
    }

    
    public static class BogusAbandonHandler extends AbandonHandler
    {
        public void abandonMessageReceived( IoSession session, AbandonRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    
    public static class BogusUnbindHandler extends UnbindHandler
    {
        public void unbindMessageReceived( IoSession session, UnbindRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusAddHandler extends AddHandler
    {
        public void addMessageReceived( IoSession session, AddRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusBindHandler extends BindHandler
    {
        public void setDirectoryService( DirectoryService directoryService )
        {
        }


        public void bindMessageReceived( IoSession session, BindRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusCompareHandler extends CompareHandler
    {
        public void compareMessageReceived( IoSession session, CompareRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusDeleteHandler extends  DeleteHandler
    {
        public void deleteMessageReceived( IoSession session, DeleteRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusModifyDnHandler extends  ModifyDnHandler
    {
        public void modifyDnMessageReceived( IoSession session, ModifyDnRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusModifyHandler extends ModifyHandler
    {
        public void modifyMessageReceived( IoSession session, ModifyRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }

    public static class BogusSearchHandler extends SearchHandler
    {
        public void searchMessageReceived( IoSession session, SearchRequest request )
        {
            throw new NotImplementedException( "handler not implemented!" );
        }
    }
}
