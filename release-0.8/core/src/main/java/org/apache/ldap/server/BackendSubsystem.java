package org.apache.ldap.server;


import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;


/**
 * 
 */
public interface BackendSubsystem
{
    /**
     * Gets an LdapContext to attach to a point in the DIT using the supplied 
     * environment parameters.
     * 
     * @param env environment settings to use for the context
     * @return an LdapContext using the supplied environment 
     * @throws NamingException if something goes wrong
     */
    LdapContext getLdapContext( Hashtable env ) throws NamingException;

    void sync() throws NamingException;

    void shutdown() throws NamingException;
}
