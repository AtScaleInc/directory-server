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
package org.apache.directory.shared.ldap.codec;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.directory.ModificationItem;

import org.apache.directory.shared.asn1.Asn1Object;
import org.apache.directory.shared.asn1.codec.DecoderException;
import org.apache.directory.shared.asn1.primitives.OID;
import org.apache.directory.shared.ldap.codec.abandon.AbandonRequest;
import org.apache.directory.shared.ldap.codec.add.AddRequest;
import org.apache.directory.shared.ldap.codec.add.AddResponse;
import org.apache.directory.shared.ldap.codec.bind.BindRequest;
import org.apache.directory.shared.ldap.codec.bind.BindResponse;
import org.apache.directory.shared.ldap.codec.bind.SaslCredentials;
import org.apache.directory.shared.ldap.codec.bind.SimpleAuthentication;
import org.apache.directory.shared.ldap.codec.compare.CompareRequest;
import org.apache.directory.shared.ldap.codec.compare.CompareResponse;
import org.apache.directory.shared.ldap.codec.del.DelRequest;
import org.apache.directory.shared.ldap.codec.del.DelResponse;
import org.apache.directory.shared.ldap.codec.extended.ExtendedRequest;
import org.apache.directory.shared.ldap.codec.extended.ExtendedResponse;
import org.apache.directory.shared.ldap.codec.modify.ModifyRequest;
import org.apache.directory.shared.ldap.codec.modify.ModifyResponse;
import org.apache.directory.shared.ldap.codec.modifyDn.ModifyDNRequest;
import org.apache.directory.shared.ldap.codec.modifyDn.ModifyDNResponse;
import org.apache.directory.shared.ldap.codec.search.AndFilter;
import org.apache.directory.shared.ldap.codec.search.AttributeValueAssertionFilter;
import org.apache.directory.shared.ldap.codec.search.ConnectorFilter;
import org.apache.directory.shared.ldap.codec.search.ExtensibleMatchFilter;
import org.apache.directory.shared.ldap.codec.search.Filter;
import org.apache.directory.shared.ldap.codec.search.NotFilter;
import org.apache.directory.shared.ldap.codec.search.OrFilter;
import org.apache.directory.shared.ldap.codec.search.PresentFilter;
import org.apache.directory.shared.ldap.codec.search.SearchRequest;
import org.apache.directory.shared.ldap.codec.search.SearchResultDone;
import org.apache.directory.shared.ldap.codec.search.SearchResultEntry;
import org.apache.directory.shared.ldap.codec.search.SearchResultReference;
import org.apache.directory.shared.ldap.codec.search.SubstringFilter;
import org.apache.directory.shared.ldap.codec.search.controls.PSearchControlCodec;
import org.apache.directory.shared.ldap.codec.search.controls.SubEntryControlCodec;
import org.apache.directory.shared.ldap.codec.util.LdapURLEncodingException;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ExtensibleNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LeafNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.message.AbandonRequestImpl;
import org.apache.directory.shared.ldap.message.AbstractMutableControlImpl;
import org.apache.directory.shared.ldap.message.AddRequestImpl;
import org.apache.directory.shared.ldap.message.AddResponseImpl;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.BindRequestImpl;
import org.apache.directory.shared.ldap.message.BindResponseImpl;
import org.apache.directory.shared.ldap.message.CascadeControl;
import org.apache.directory.shared.ldap.message.CompareRequestImpl;
import org.apache.directory.shared.ldap.message.CompareResponseImpl;
import org.apache.directory.shared.ldap.message.DeleteRequestImpl;
import org.apache.directory.shared.ldap.message.DeleteResponseImpl;
import org.apache.directory.shared.ldap.message.ExtendedRequestImpl;
import org.apache.directory.shared.ldap.message.ExtendedResponseImpl;
import org.apache.directory.shared.ldap.message.LdapResultImpl;
import org.apache.directory.shared.ldap.message.Message;
import org.apache.directory.shared.ldap.message.ModifyDnRequestImpl;
import org.apache.directory.shared.ldap.message.ModifyDnResponseImpl;
import org.apache.directory.shared.ldap.message.ModifyRequestImpl;
import org.apache.directory.shared.ldap.message.ModifyResponseImpl;
import org.apache.directory.shared.ldap.message.PersistentSearchControl;
import org.apache.directory.shared.ldap.message.Referral;
import org.apache.directory.shared.ldap.message.ReferralImpl;
import org.apache.directory.shared.ldap.message.SearchRequestImpl;
import org.apache.directory.shared.ldap.message.SearchResponseDoneImpl;
import org.apache.directory.shared.ldap.message.SearchResponseEntryImpl;
import org.apache.directory.shared.ldap.message.SearchResponseReferenceImpl;
import org.apache.directory.shared.ldap.message.SubentriesControl;
import org.apache.directory.shared.ldap.message.UnbindRequestImpl;
import org.apache.directory.shared.ldap.message.extended.GracefulShutdownRequest;
import org.apache.directory.shared.ldap.message.spi.Provider;
import org.apache.directory.shared.ldap.message.spi.TransformerSpi;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.LdapURL;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Twix to Snickers Message transformer.
 * 
 * @author <a href="mailto:dev@directory.apache.org"> Apache Directory Project</a>
 * @version $Rev$, $Date$, 
 */
public class TwixTransformer implements TransformerSpi
{
    /** The logger */
    private static Logger log = LoggerFactory.getLogger( TwixTransformer.class );

    /** A speedup for logger */
    private static final boolean IS_DEBUG = log.isDebugEnabled();
    
    /** the provider this transformer is part of */
    private final Provider provider;


    /**
     * Creates a passthrough transformer that really does nothing at all.
     * 
     * @param provider the provider for this transformer
     */
    public TwixTransformer(Provider provider)
    {
        this.provider = provider;
    }


    /**
     * Gets the Provider associated with this SPI implementation object.
     * 
     * @return Provider.
     */
    public Provider getProvider()
    {
        return provider;
    }


    /**
     * Transform an AbandonRequest message from a TwixMessage to a
     * SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers AbandonRequestImpl
     */
    private Message transformAbandonRequest( LdapMessage twixMessage, int messageId )
    {
        AbandonRequestImpl snickersMessage = new AbandonRequestImpl( messageId );
        AbandonRequest abandonRequest = twixMessage.getAbandonRequest();

        // Twix : int abandonnedMessageId -> Snickers : int abandonId
        snickersMessage.setAbandoned( abandonRequest.getAbandonedMessageId() );

        return snickersMessage;
    }


    /**
     * Transform an AddRequest message from a TwixMessage to a SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers AddRequestImpl
     */
    private Message transformAddRequest( LdapMessage twixMessage, int messageId )
    {
        AddRequestImpl snickersMessage = new AddRequestImpl( messageId );
        AddRequest addRequest = twixMessage.getAddRequest();

        // Twix : LdapDN entry -> Snickers : String name
        snickersMessage.setEntry( addRequest.getEntry() );

        // Twix : Attributes attributes -> Snickers : Attributes entry
        snickersMessage.setEntry( addRequest.getEntry() );

        return snickersMessage;
    }


    /**
     * Transform a BindRequest message from a TwixMessage to a SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers BindRequestImpl
     */
    private Message transformBindRequest( LdapMessage twixMessage, int messageId )
    {
        BindRequestImpl snickersMessage = new BindRequestImpl( messageId );
        BindRequest bindRequest = twixMessage.getBindRequest();

        // Twix : int version -> Snickers : boolean isVersion3
        snickersMessage.setVersion3( bindRequest.isLdapV3() );

        // Twix : LdapDN name -> Snickers : LdapDN name
        snickersMessage.setName( bindRequest.getName() );

        // Twix : Asn1Object authentication instanceOf SimpleAuthentication ->
        // Snickers : boolean isSimple
        // Twix : SimpleAuthentication OctetString simple -> Snickers : byte []
        // credentials
        Asn1Object authentication = bindRequest.getAuthentication();

        if ( authentication instanceof SimpleAuthentication )
        {
            snickersMessage.setSimple( true );
            snickersMessage.setCredentials( ( ( SimpleAuthentication ) authentication ).getSimple() );
        }
        else
        {
            snickersMessage.setSimple( false );
            snickersMessage.setCredentials( ( ( SaslCredentials ) authentication ).getCredentials() );
            snickersMessage.setSaslMechanism( ( ( SaslCredentials ) authentication ).getMechanism() );
        }

        return snickersMessage;
    }


    /**
     * Transform a BindResponse message from a TwixMessage to a 
     * SnickersMessage.  This is used by clients which are receiving a 
     * BindResponse PDU and must decode it to return the Snickers 
     * representation.
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return a Snickers BindResponseImpl
     */
    private Message transformBindResponse( LdapMessage twixMessage, int messageId )
    {
        BindResponseImpl snickersMessage = new BindResponseImpl( messageId );
        BindResponse bindResponse = twixMessage.getBindResponse();

        // Twix : byte[] serverSaslcreds -> Snickers : byte[] serverSaslCreds
        snickersMessage.setServerSaslCreds( bindResponse.getServerSaslCreds() );
        transformControlsTwixToSnickers( twixMessage, snickersMessage );
        transformLdapResultTwixToSnickers( bindResponse.getLdapResult(), snickersMessage.getLdapResult() );
        
        return snickersMessage;
    }

    
    /**
     * Transforms parameters of a Twix LdapResult into a Snickers LdapResult.
     *
     * @param twixResult the Twix LdapResult representation
     * @param snickersResult the Snickers LdapResult representation
     */
    private void transformLdapResultTwixToSnickers( LdapResult twixResult, 
        org.apache.directory.shared.ldap.message.LdapResult snickersResult )
    {
        snickersResult.setErrorMessage( twixResult.getErrorMessage() );
        
        try
        {
            snickersResult.setMatchedDn( new LdapDN( twixResult.getMatchedDN() ) );
        }
        catch ( InvalidNameException e )
        {
            log.error( "Could not parse matchedDN while transforming twix value to snickers: {}", 
                twixResult.getMatchedDN() );
            snickersResult.setMatchedDn( new LdapDN() );
        }
        
        snickersResult.setResultCode( twixResult.getResultCode() );

        if ( twixResult.getReferrals() == null )
        {
            
        }
        else
        {
            ReferralImpl referral = new ReferralImpl();
            
            for ( LdapURL url : twixResult.getReferrals() )
            {
                referral.addLdapUrl( url.toString() );
            }
            
            snickersResult.setReferral( referral );
        }
    }
    

    /**
     * Transform a CompareRequest message from a TwixMessage to a
     * SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers CompareRequestImpl
     */
    private Message transformCompareRequest( LdapMessage twixMessage, int messageId )
    {
        CompareRequestImpl snickersMessage = new CompareRequestImpl( messageId );
        CompareRequest compareRequest = twixMessage.getCompareRequest();

        // Twix : LdapDN entry -> Snickers : private LdapDN
        snickersMessage.setName( compareRequest.getEntry() );

        // Twix : LdapString attributeDesc -> Snickers : String attrId
        snickersMessage.setAttributeId( compareRequest.getAttributeDesc() );

        // Twix : OctetString assertionValue -> Snickers : byte[] attrVal
        if ( compareRequest.getAssertionValue() instanceof String )
        {
            snickersMessage.setAssertionValue( ( String ) compareRequest.getAssertionValue() );
        }
        else
        {
            snickersMessage.setAssertionValue( ( byte[] ) compareRequest.getAssertionValue() );
        }

        return snickersMessage;
    }


    /**
     * Transform a DelRequest message from a TwixMessage to a SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers DeleteRequestImpl
     */
    private Message transformDelRequest( LdapMessage twixMessage, int messageId )
    {
        DeleteRequestImpl snickersMessage = new DeleteRequestImpl( messageId );
        DelRequest delRequest = twixMessage.getDelRequest();

        // Twix : LdapDN entry -> Snickers : LdapDN
        snickersMessage.setName( delRequest.getEntry() );

        return snickersMessage;
    }


    /**
     * Transform an ExtendedRequest message from a TwixMessage to a
     * SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers ExtendedRequestImpl
     */
    private Message transformExtendedRequest( LdapMessage twixMessage, int messageId )
    {
        ExtendedRequest extendedRequest = twixMessage.getExtendedRequest();
        ExtendedRequestImpl snickersMessage;

        if ( extendedRequest.getRequestName().equals( GracefulShutdownRequest.EXTENSION_OID ) )
        {
            snickersMessage = new GracefulShutdownRequest( messageId );
        }
        else
        {
            snickersMessage = new ExtendedRequestImpl( messageId );
        }

        // Twix : OID requestName -> Snickers : String oid
        snickersMessage.setOid( extendedRequest.getRequestName() );

        // Twix : OctetString requestValue -> Snickers : byte [] payload
        snickersMessage.setPayload( extendedRequest.getRequestValue() );

        return snickersMessage;
    }


    /**
     * Transform a ModifyDNRequest message from a TwixMessage to a
     * SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers ModifyDNRequestImpl
     */
    private Message transformModifyDNRequest( LdapMessage twixMessage, int messageId )
    {
        ModifyDnRequestImpl snickersMessage = new ModifyDnRequestImpl( messageId );
        ModifyDNRequest modifyDNRequest = twixMessage.getModifyDNRequest();

        // Twix : LdapDN entry -> Snickers : LdapDN m_name
        snickersMessage.setName( modifyDNRequest.getEntry() );

        // Twix : RelativeLdapDN newRDN -> Snickers : LdapDN m_newRdn
        snickersMessage.setNewRdn( modifyDNRequest.getNewRDN() );

        // Twix : boolean deleteOldRDN -> Snickers : boolean m_deleteOldRdn
        snickersMessage.setDeleteOldRdn( modifyDNRequest.isDeleteOldRDN() );

        // Twix : LdapDN newSuperior -> Snickers : LdapDN m_newSuperior
        snickersMessage.setNewSuperior( modifyDNRequest.getNewSuperior() );

        return snickersMessage;
    }


    /**
     * Transform a ModifyRequest message from a TwixMessage to a SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers ModifyRequestImpl
     */
    private Message transformModifyRequest( LdapMessage twixMessage, int messageId )
    {
        ModifyRequestImpl snickersMessage = new ModifyRequestImpl( messageId );
        ModifyRequest modifyRequest = twixMessage.getModifyRequest();

        // Twix : LdapDN object -> Snickers : String name
        snickersMessage.setName( modifyRequest.getObject() );

        // Twix : ArrayList modifications -> Snickers : ArrayList mods
        if ( modifyRequest.getModifications() != null )
        {
            // Loop through the modifications
            for ( ModificationItem modification:modifyRequest.getModifications() )
            {
                snickersMessage.addModification( modification );
            }
        }

        return snickersMessage;
    }


    /**
     * Transform the Filter part of a SearchRequest to en ExprNode
     * 
     * @param twixFilter The filter to be transformed
     * @return An ExprNode
     */
    private ExprNode transformFilter( Filter twixFilter )
    {
        if ( twixFilter != null )
        {
            // Transform OR, AND or NOT leaves
            if ( twixFilter instanceof ConnectorFilter )
            {
                BranchNode branch = null;

                if ( twixFilter instanceof AndFilter )
                {
                    branch = new AndNode();
                }
                else if ( twixFilter instanceof OrFilter )
                {
                    branch = new OrNode();
                }
                else if ( twixFilter instanceof NotFilter )
                {
                    branch = new NotNode();
                }

                List<Filter> filtersSet = ( ( ConnectorFilter ) twixFilter ).getFilterSet();

                // Loop on all AND/OR children
                if ( filtersSet != null )
                {
                    for ( Filter filter:filtersSet )
                    {
                        branch.addNode( transformFilter( filter ) );
                    }
                }

                return branch;
            }
            else
            {
                // Transform PRESENT or ATTRIBUTE_VALUE_ASSERTION
                LeafNode branch = null;

                if ( twixFilter instanceof PresentFilter )
                {
                    branch = new PresenceNode( ( ( PresentFilter ) twixFilter ).getAttributeDescription() );
                }
                else if ( twixFilter instanceof AttributeValueAssertionFilter )
                {
                    AttributeValueAssertion ava = ( ( AttributeValueAssertionFilter ) twixFilter ).getAssertion();

                    // Transform =, >=, <=, ~= filters
                    switch ( ( ( AttributeValueAssertionFilter ) twixFilter ).getFilterType() )
                    {
                        case LdapConstants.EQUALITY_MATCH_FILTER:
                            branch = new EqualityNode( ava.getAttributeDesc(), 
                                ava.getAssertionValue() );
                            
                            break;

                        case LdapConstants.GREATER_OR_EQUAL_FILTER:
                            branch = new GreaterEqNode( ava.getAttributeDesc(),
                                ava.getAssertionValue() );

                            break;

                        case LdapConstants.LESS_OR_EQUAL_FILTER:
                            branch = new LessEqNode( ava.getAttributeDesc(), 
                                ava.getAssertionValue() );

                            break;

                        case LdapConstants.APPROX_MATCH_FILTER:
                            branch = new ApproximateNode( ava.getAttributeDesc(), 
                                ava.getAssertionValue() );

                            break;
                    }

                }
                else if ( twixFilter instanceof SubstringFilter )
                {
                    // Transform Substring filters
                    SubstringFilter filter = ( SubstringFilter ) twixFilter;
                    String initialString = null;
                    String finalString = null;
                    List<String> anyString = null;

                    if ( filter.getInitialSubstrings() != null )
                    {
                        initialString = filter.getInitialSubstrings();
                    }

                    if ( filter.getFinalSubstrings() != null )
                    {
                        finalString = filter.getFinalSubstrings();
                    }

                    if ( filter.getAnySubstrings() != null )
                    {
                        anyString = new ArrayList<String>();

                        for ( String any:filter.getAnySubstrings() )
                        {
                            anyString.add( any );
                        }
                    }

                    branch = new SubstringNode( anyString, filter.getType(), initialString, finalString );
                }
                else if ( twixFilter instanceof ExtensibleMatchFilter )
                {
                    // Transform Extensible Match Filter
                    ExtensibleMatchFilter filter = ( ExtensibleMatchFilter ) twixFilter;
                    String attribute = null;
                    String matchingRule = null;

                    if ( filter.getType() != null )
                    {
                        attribute = filter.getType();
                    }

                    Object value = filter.getMatchValue();

                    if ( filter.getMatchingRule() != null )
                    {
                        matchingRule = filter.getMatchingRule();
                    }

                    if ( value instanceof String )
                    {
                        branch = new ExtensibleNode( attribute, (String)value, matchingRule, filter.isDnAttributes() );
                    }
                    else
                    {
                        if ( value != null )
                        {
                            branch = new ExtensibleNode( attribute, (byte[])value, matchingRule, filter.isDnAttributes() );
                        }
                        else
                        {
                            branch = new ExtensibleNode( attribute, (byte[])null, matchingRule, filter.isDnAttributes() );
                        }
                    }
                }

                return branch;
            }
        }
        else
        {
            // We have found nothing to transform. Return null then.
            return null;
        }
    }


    /**
     * Transform a SearchRequest message from a TwixMessage to a SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers SearchRequestImpl
     */
    private Message transformSearchRequest( LdapMessage twixMessage, int messageId )
    {
        SearchRequestImpl snickersMessage = new SearchRequestImpl( messageId );
        SearchRequest searchRequest = twixMessage.getSearchRequest();

        // Twix : LdapDN baseObject -> Snickers : String baseDn
        snickersMessage.setBase( searchRequest.getBaseObject() );

        // Twix : int scope -> Snickers : ScopeEnum scope
        snickersMessage.setScope( searchRequest.getScope() );

        // Twix : int derefAliases -> Snickers : AliasDerefMode derefAliases
        switch ( searchRequest.getDerefAliases() )
        {
            case LdapConstants.DEREF_ALWAYS:
                snickersMessage.setDerefAliases( AliasDerefMode.DEREF_ALWAYS );
                break;

            case LdapConstants.DEREF_FINDING_BASE_OBJ:
                snickersMessage.setDerefAliases( AliasDerefMode.DEREF_FINDING_BASE_OBJ );
                break;

            case LdapConstants.DEREF_IN_SEARCHING:
                snickersMessage.setDerefAliases( AliasDerefMode.DEREF_IN_SEARCHING );
                break;

            case LdapConstants.NEVER_DEREF_ALIASES:
                snickersMessage.setDerefAliases( AliasDerefMode.NEVER_DEREF_ALIASES );
                break;
        }

        // Twix : int sizeLimit -> Snickers : int sizeLimit
        snickersMessage.setSizeLimit( searchRequest.getSizeLimit() );

        // Twix : int timeLimit -> Snickers : int timeLimit
        snickersMessage.setTimeLimit( searchRequest.getTimeLimit() );

        // Twix : boolean typesOnly -> Snickers : boolean typesOnly
        snickersMessage.setTypesOnly( searchRequest.isTypesOnly() );

        // Twix : Filter filter -> Snickers : ExprNode filter
        Filter twixFilter = searchRequest.getFilter();

        snickersMessage.setFilter( transformFilter( twixFilter ) );

        // Twix : ArrayList attributes -> Snickers : ArrayList attributes
        if ( searchRequest.getAttributes() != null )
        {
            List<EntryAttribute> attributes = searchRequest.getAttributes();

            if ( ( attributes != null ) && ( attributes.size() != 0 ) )
            {
                for ( EntryAttribute attribute:attributes )
                {
                    if ( attribute != null )
                    {
                        snickersMessage.addAttribute( attribute.getId() );
                    }
                }
            }
        }

        return snickersMessage;
    }


    /**
     * Transform an UnBindRequest message from a TwixMessage to a
     * SnickersMessage
     * 
     * @param twixMessage The message to transform
     * @param messageId The message Id
     * @return A Snickers UnBindRequestImpl
     */
    private Message transformUnBindRequest( LdapMessage twixMessage, int messageId )
    {
        return new UnbindRequestImpl( messageId );
    }


    /**
     * Transform the Twix message to a codec neutral message.
     * 
     * @param obj the object to transform
     * @return the object transformed
     */
    public Message transform( Object obj )
    {
        LdapMessage twixMessage = ( LdapMessage ) obj;
        int messageId = twixMessage.getMessageId();

        if ( IS_DEBUG )
        {
            log.debug( "Transforming LdapMessage <" + messageId + ", " + twixMessage.getMessageTypeName()
                + "> from Twix to Snickers." );
        }

        Message snickersMessage = null;

        int messageType = twixMessage.getMessageType();

        switch ( messageType )
        {
            case ( LdapConstants.BIND_REQUEST  ):
                snickersMessage = transformBindRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.UNBIND_REQUEST  ):
                snickersMessage = transformUnBindRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.SEARCH_REQUEST  ):
                snickersMessage = transformSearchRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.MODIFY_REQUEST  ):
                snickersMessage = transformModifyRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.ADD_REQUEST  ):
                snickersMessage = transformAddRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.DEL_REQUEST  ):
                snickersMessage = transformDelRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.MODIFYDN_REQUEST  ):
                snickersMessage = transformModifyDNRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.COMPARE_REQUEST  ):
                snickersMessage = transformCompareRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.ABANDON_REQUEST  ):
                snickersMessage = transformAbandonRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.EXTENDED_REQUEST  ):
                snickersMessage = transformExtendedRequest( twixMessage, messageId );
                break;

            case ( LdapConstants.BIND_RESPONSE  ):
                snickersMessage = transformBindResponse( twixMessage, messageId );
                break;

            case ( LdapConstants.SEARCH_RESULT_ENTRY  ):
            case ( LdapConstants.SEARCH_RESULT_DONE  ):
            case ( LdapConstants.SEARCH_RESULT_REFERENCE  ):
            case ( LdapConstants.MODIFY_RESPONSE  ):
            case ( LdapConstants.ADD_RESPONSE  ):
            case ( LdapConstants.DEL_RESPONSE  ):
            case ( LdapConstants.MODIFYDN_RESPONSE  ):
            case ( LdapConstants.COMPARE_RESPONSE  ):
            case ( LdapConstants.EXTENDED_RESPONSE  ):
                // Nothing to do !
                break;

            default:
                throw new IllegalStateException( "shouldn't happen - if it does then we have issues" );
        }

        // Transform the controls, too
        List<org.apache.directory.shared.ldap.codec.Control> twixControls = twixMessage.getControls();

        if ( twixControls != null )
        {
            for ( final Control twixControl:twixControls )
            {
                AbstractMutableControlImpl neutralControl = null;

                if ( twixControl.getControlValue() instanceof 
                    org.apache.directory.shared.ldap.codec.controls.CascadeControlCodec )
                {
                    neutralControl = new CascadeControl();
                    neutralControl.setCritical( twixControl.getCriticality() );
                }
                else if ( twixControl.getControlValue() instanceof PSearchControlCodec )
                {
                    PersistentSearchControl neutralPsearch = new PersistentSearchControl();
                    neutralControl = neutralPsearch;
                    PSearchControlCodec twixPsearch = ( PSearchControlCodec ) twixControl.getControlValue();
                    neutralPsearch.setChangeTypes( twixPsearch.getChangeTypes() );
                    neutralPsearch.setChangesOnly( twixPsearch.isChangesOnly() );
                    neutralPsearch.setReturnECs( twixPsearch.isReturnECs() );
                    neutralPsearch.setCritical( twixControl.getCriticality() );
                }
                else if ( twixControl.getControlValue() instanceof SubEntryControlCodec )
                {
                    SubentriesControl neutralSubentriesControl = new SubentriesControl();
                    SubEntryControlCodec twixSubentriesControl = ( SubEntryControlCodec ) twixControl.getControlValue();
                    neutralControl = neutralSubentriesControl;
                    neutralSubentriesControl.setVisibility( twixSubentriesControl.isVisible() );
                    neutralSubentriesControl.setCritical( twixControl.getCriticality() );
                }
                else if ( twixControl.getControlValue() instanceof byte[] )
                {
                    neutralControl = new AbstractMutableControlImpl()
                    {
                        public byte[] getEncodedValue()
                        {
                            return ( byte[] ) twixControl.getControlValue();
                        }
                    };

                    // Twix : boolean criticality -> Snickers : boolean
                    // m_isCritical
                    neutralControl.setCritical( twixControl.getCriticality() );

                    // Twix : OID controlType -> Snickers : String m_oid
                    neutralControl.setID( twixControl.getControlType() );
                }
                else if ( twixControl.getControlValue() == null )
                {
                    neutralControl = new AbstractMutableControlImpl()
                    {
                        public byte[] getEncodedValue()
                        {
                            return ( byte[] ) twixControl.getControlValue();
                        }
                    };

                    // Twix : boolean criticality -> Snickers : boolean
                    // m_isCritical
                    neutralControl.setCritical( twixControl.getCriticality() );

                    // Twix : OID controlType -> Snickers : String m_oid
                    neutralControl.setID( twixControl.getControlType() );
                }
                

                snickersMessage.add( neutralControl );
            }
        }

        return snickersMessage;
    }


    /**
     * Transform a Ldapresult part of a Snickers Response to a Twix LdapResult
     * 
     * @param snickersLdapResult the Snickers LdapResult to transform
     * @return A Twix LdapResult
     */
    private LdapResult transformLdapResult( LdapResultImpl snickersLdapResult )
    {
        LdapResult twixLdapResult = new LdapResult();

        // Snickers : ResultCodeEnum resultCode -> Twix : int resultCode
        twixLdapResult.setResultCode( snickersLdapResult.getResultCode() );

        // Snickers : String errorMessage -> Twix : LdapString errorMessage
        String errorMessage = snickersLdapResult.getErrorMessage();
        
        twixLdapResult.setErrorMessage( StringTools.isEmpty( errorMessage ) ? "" : errorMessage );

        // Snickers : String matchedDn -> Twix : LdapDN matchedDN
        twixLdapResult.setMatchedDN( snickersLdapResult.getMatchedDn() );

        // Snickers : Referral referral -> Twix : ArrayList referrals
        ReferralImpl snickersReferrals = ( ReferralImpl ) snickersLdapResult.getReferral();

        if ( snickersReferrals != null )
        {
            twixLdapResult.initReferrals();

            for ( String referral:snickersReferrals.getLdapUrls() )
            {
                try
                {
                    LdapURL ldapUrl = new LdapURL( referral.getBytes() );
                    twixLdapResult.addReferral( ldapUrl );
                }
                catch ( LdapURLEncodingException lude )
                {
                    log.warn( "The referral " + referral + " is invalid : " + lude.getMessage() );
                    twixLdapResult.addReferral( LdapURL.EMPTY_URL );
                }
            }
        }

        return twixLdapResult;
    }


    /**
     * Transform a Snickers AddResponse to a Twix AddResponse
     * 
     * @param twixMessage The Twix AddResponse to produce
     * @param snickersMessage The incoming Snickers AddResponse
     */
    private void transformAddResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        AddResponseImpl snickersAddResponse = ( AddResponseImpl ) snickersMessage;

        AddResponse addResponse = new AddResponse();

        // Transform the ldapResult
        addResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersAddResponse.getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( addResponse );
    }


    /**
     * Transform a Snickers BindResponse to a Twix BindResponse
     * 
     * @param twixMessage The Twix BindResponse to produce
     * @param snickersMessage The incoming Snickers BindResponse
     */
    private void transformBindResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        BindResponseImpl snickersBindResponse = ( BindResponseImpl ) snickersMessage;

        BindResponse bindResponse = new BindResponse();

        // Snickers : byte [] serverSaslCreds -> Twix : OctetString
        // serverSaslCreds
        byte[] serverSaslCreds = snickersBindResponse.getServerSaslCreds();

        if ( serverSaslCreds != null )
        {
            bindResponse.setServerSaslCreds( serverSaslCreds );
        }

        // Transform the ldapResult
        bindResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersBindResponse.getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( bindResponse );
    }


    /**
     * Transform a Snickers BindRequest to a Twix BindRequest
     * 
     * @param twixMessage The Twix BindRequest to produce
     * @param snickersMessage The incoming Snickers BindRequest
     */
    private void transformBindRequest( LdapMessage twixMessage, Message snickersMessage )
    {
        BindRequestImpl snickersBindRequest = ( BindRequestImpl ) snickersMessage;

        BindRequest bindRequest = new BindRequest();
        
        if ( snickersBindRequest.isSimple() )
        {
            SimpleAuthentication simple = new SimpleAuthentication();
            simple.setSimple( snickersBindRequest.getCredentials() );
            bindRequest.setAuthentication( simple );
        }
        else
        {
            SaslCredentials sasl = new SaslCredentials();
            sasl.setCredentials( snickersBindRequest.getCredentials() );
            sasl.setMechanism( snickersBindRequest.getSaslMechanism() );
            bindRequest.setAuthentication( sasl );
        }
        
        bindRequest.setMessageId( snickersBindRequest.getMessageId() );
        bindRequest.setName( snickersBindRequest.getName() );
        bindRequest.setVersion( snickersBindRequest.isVersion3() ? 3 : 2 );
        
        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( bindRequest );
    }


    /**
     * Transform a Snickers CompareResponse to a Twix CompareResponse
     * 
     * @param twixMessage The Twix CompareResponse to produce
     * @param snickersMessage The incoming Snickers CompareResponse
     */
    private void transformCompareResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        CompareResponseImpl snickersCompareResponse = ( CompareResponseImpl ) snickersMessage;

        CompareResponse compareResponse = new CompareResponse();

        // Transform the ldapResult
        compareResponse
            .setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersCompareResponse.getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( compareResponse );
    }


    /**
     * Transform a Snickers DelResponse to a Twix DelResponse
     * 
     * @param twixMessage The Twix DelResponse to produce
     * @param snickersMessage The incoming Snickers DelResponse
     */
    private void transformDelResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        DeleteResponseImpl snickersDelResponse = ( DeleteResponseImpl ) snickersMessage;

        DelResponse delResponse = new DelResponse();

        // Transform the ldapResult
        delResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersDelResponse.getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( delResponse );
    }


    /**
     * Transform a Snickers ExtendedResponse to a Twix ExtendedResponse
     * 
     * @param twixMessage The Twix ExtendedResponse to produce
     * @param snickersMessage The incoming Snickers ExtendedResponse
     */
    private void transformExtendedResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        ExtendedResponseImpl snickersExtendedResponse = ( ExtendedResponseImpl ) snickersMessage;
        ExtendedResponse extendedResponse = new ExtendedResponse();

        // Snickers : String oid -> Twix : OID responseName
        try
        {
            extendedResponse.setResponseName( new OID( snickersExtendedResponse.getResponseName() ) );
        }
        catch ( DecoderException de )
        {
            log.warn( "The OID " + snickersExtendedResponse.getResponseName() + " is invalid : " + de.getMessage() );
            extendedResponse.setResponseName( null );
        }

        // Snickers : byte [] value -> Twix : Object response
        extendedResponse.setResponse( snickersExtendedResponse.getResponse() );

        // Transform the ldapResult
        extendedResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersExtendedResponse
            .getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( extendedResponse );
    }


    /**
     * Transform a Snickers ModifyResponse to a Twix ModifyResponse
     * 
     * @param twixMessage The Twix ModifyResponse to produce
     * @param snickersMessage The incoming Snickers ModifyResponse
     */
    private void transformModifyResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        ModifyResponseImpl snickersModifyResponse = ( ModifyResponseImpl ) snickersMessage;

        ModifyResponse modifyResponse = new ModifyResponse();

        // Transform the ldapResult
        modifyResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersModifyResponse.getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( modifyResponse );
    }


    /**
     * Transform a Snickers ModifyDNResponse to a Twix ModifyDNResponse
     * 
     * @param twixMessage The Twix ModifyDNResponse to produce
     * @param snickersMessage The incoming Snickers ModifyDNResponse
     */
    private void transformModifyDNResponse( LdapMessage twixMessage, Message snickersMessage )
    {
        ModifyDnResponseImpl snickersModifyDNResponse = ( ModifyDnResponseImpl ) snickersMessage;

        ModifyDNResponse modifyDNResponse = new ModifyDNResponse();

        // Transform the ldapResult
        modifyDNResponse.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersModifyDNResponse
            .getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( modifyDNResponse );
    }


    /**
     * Transform a Snickers SearchResponseDone to a Twix SearchResultDone
     * 
     * @param twixMessage The Twix SearchResultDone to produce
     * @param snickersMessage The incoming Snickers SearchResponseDone
     */
    private void transformSearchResultDone( LdapMessage twixMessage, Message snickersMessage )
    {
        SearchResponseDoneImpl snickersSearchResponseDone = ( SearchResponseDoneImpl ) snickersMessage;
        SearchResultDone searchResultDone = new SearchResultDone();

        // Transform the ldapResult
        searchResultDone.setLdapResult( transformLdapResult( ( LdapResultImpl ) snickersSearchResponseDone
            .getLdapResult() ) );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( searchResultDone );
    }


    /**
     * Transform a Snickers SearchResponseEntry to a Twix SearchResultEntry
     * 
     * @param twixMessage The Twix SearchResultEntry to produce
     * @param snickersMessage The incoming Snickers SearchResponseEntry
     */
    private void transformSearchResultEntry( LdapMessage twixMessage, Message snickersMessage )
    {
        SearchResponseEntryImpl snickersSearchResultResponse = ( SearchResponseEntryImpl ) snickersMessage;
        SearchResultEntry searchResultEntry = new SearchResultEntry();

        // Snickers : LdapDN dn -> Twix : LdapDN objectName
        searchResultEntry.setObjectName( snickersSearchResultResponse.getObjectName() );

        // Snickers : Attributes attributes -> Twix : ArrayList
        // partialAttributeList
        searchResultEntry.setEntry( snickersSearchResultResponse.getEntry() );

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( searchResultEntry );
    }


    /**
     * Transform a Snickers SearchResponseReference to a Twix
     * SearchResultReference
     * 
     * @param twixMessage The Twix SearchResultReference to produce
     * @param snickersMessage The incoming Snickers SearchResponseReference
     */
    private void transformSearchResultReference( LdapMessage twixMessage, Message snickersMessage )
    {
        SearchResponseReferenceImpl snickersSearchResponseReference = ( SearchResponseReferenceImpl ) snickersMessage;
        SearchResultReference searchResultReference = new SearchResultReference();

        // Snickers : Referral m_referral -> Twix: ArrayList
        // searchResultReferences
        Referral referrals = snickersSearchResponseReference.getReferral();

        // Loop on all referals
        if ( referrals != null )
        {
            Collection<String> urls = referrals.getLdapUrls();

            if ( urls != null )
            {
                for ( String url:urls)
                {
                    try
                    {
                        searchResultReference.addSearchResultReference( new LdapURL( url ) );
                    }
                    catch ( LdapURLEncodingException luee )
                    {
                        log.warn( "The LdapURL " + url + " is incorrect : " + luee.getMessage() );
                    }
                }
            }
        }

        // Set the operation into the LdapMessage
        twixMessage.setProtocolOP( searchResultReference );
    }


    /**
     * Transform the Snickers message to a Twix message.
     * 
     * @param msg the message to transform
     * @return the msg transformed
     */
    public Object transform( Message msg )
    {
        if ( IS_DEBUG )
        {
            log.debug( "Transforming message type " + msg.getType() );
        }

        LdapMessage twixMessage = new LdapMessage();

        twixMessage.setMessageId( msg.getMessageId() );

        switch ( msg.getType() )
        {
            case SEARCH_RES_ENTRY :
                transformSearchResultEntry( twixMessage, msg );
                break;
                
            case SEARCH_RES_DONE :
                transformSearchResultDone( twixMessage, msg );
                break;
                
            case SEARCH_RES_REF :
                transformSearchResultReference( twixMessage, msg );
                break;
                
            case BIND_RESPONSE :
                transformBindResponse( twixMessage, msg );
                break;
                
            case BIND_REQUEST :
                transformBindRequest( twixMessage, msg );
                break;
                
            case ADD_RESPONSE :
                transformAddResponse( twixMessage, msg );
                break;
                
            case COMPARE_RESPONSE :
                transformCompareResponse( twixMessage, msg );
                break;
                
            case DEL_RESPONSE :
                transformDelResponse( twixMessage, msg );
                break;
         
            case MODIFY_RESPONSE :
                transformModifyResponse( twixMessage, msg );
                break;

            case MOD_DN_RESPONSE :
                transformModifyDNResponse( twixMessage, msg );
                break;
                
            case EXTENDED_RESP :
                transformExtendedResponse( twixMessage, msg );
                break;
                
        }

        // We also have to transform the controls...
        if ( !msg.getControls().isEmpty() )
        {
            transformControls( twixMessage, msg );
        }

        if ( IS_DEBUG )
        {
            log.debug( "Transformed message : " + twixMessage );
        }

        return twixMessage;
    }


    /**
     * TODO finish this implementation.  Takes Twix Controls, transforming 
     * them to Snickers Controls and populates the Snickers message with them.
     *
     * @param twixMessage the Twix message
     * @param msg the Snickers message
     */
    private void transformControlsTwixToSnickers( LdapMessage twixMessage, Message msg )
    {
        if ( twixMessage.getControls() == null )
        {
            return;
        }
        
        for ( Control control:twixMessage.getControls() )
        {
            log.debug( "Not decoding response control: {}", control );
        }
    }
    
    
    /**
     * Transforms the controls
     * @param twixMessage The Twix SearchResultReference to produce
     * @param msg The incoming Snickers SearchResponseReference
     */
    private void transformControls( LdapMessage twixMessage, Message msg )
    {
        for ( javax.naming.ldap.Control control:msg.getControls().values() )
        {
            org.apache.directory.shared.ldap.codec.Control twixControl = new org.apache.directory.shared.ldap.codec.Control();
            twixMessage.addControl( twixControl );
            twixControl.setCriticality( control.isCritical() );
            twixControl.setControlValue( control.getEncodedValue() );
            twixControl.setEncodedValue( control.getEncodedValue() );
            twixControl.setControlType( control.getID() );
            twixControl.setParent( twixMessage );
        }
    }
}
