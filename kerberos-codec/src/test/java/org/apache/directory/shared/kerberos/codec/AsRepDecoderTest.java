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
package org.apache.directory.shared.kerberos.codec;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

import org.apache.directory.junit.tools.Concurrent;
import org.apache.directory.junit.tools.ConcurrentJunitRunner;
import org.apache.directory.shared.asn1.DecoderException;
import org.apache.directory.shared.asn1.EncoderException;
import org.apache.directory.shared.asn1.ber.Asn1Container;
import org.apache.directory.shared.asn1.ber.Asn1Decoder;
import org.apache.directory.shared.kerberos.codec.asRep.AsRepContainer;
import org.apache.directory.shared.kerberos.messages.AsRep;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test the decoder for a AS-REP
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(ConcurrentJunitRunner.class)
@Concurrent()
public class AsRepDecoderTest
{
    /**
     * Test the decoding of a AS-REP message
     */
    @Test
    public void testDecodeFullAsRep() throws Exception
    {
        Asn1Decoder kerberosDecoder = new Asn1Decoder();

        ByteBuffer stream = ByteBuffer.allocate( 0xAC );
        
        stream.put( new byte[]
        {
          0x6B, (byte)0x81, (byte)0xA9,
            0x30, (byte)0x81, (byte)0xA6,
              (byte)0xA0, 0x03,                         // PVNO
                0x02, 0x01, 0x05,
              (byte)0xA1, 0x03,                         // msg-type
                0x02, 0x01, 0x0B,
              (byte)0xA2, 0x20,                         // PA-DATA
                0x30, 0x1E,
                  0x30, 0x0D,
                    (byte)0xA1,0x03,
                      0x02, 0x01, 01,
                    (byte)0xA2, 0x06,
                      0x04, 0x04, 'a', 'b', 'c', 'd',
                  0x30, 0x0D,
                    (byte)0xA1,0x03,
                      0x02, 0x01, 01,
                    (byte)0xA2, 0x06,
                      0x04, 0x04, 'e', 'f', 'g', 'h',
              (byte)0xA3, 0x0D,                         // crealm
                0x1B, 0x0B, 'E', 'X', 'A', 'M', 'P', 'L', 'E', '.', 'C', 'O', 'M',
              (byte)0xA4, 0x14,                         // cname
                0x30, 0x12,
                  (byte)0xA0, 0x03,                     // name-type
                    0x02, 0x01, 0x01,
                  (byte)0xA1, 0x0B,                     // name-string
                    0x30, 0x09,
                      0x1B, 0x07, 'h', 'n', 'e', 'l', 's', 'o', 'n',
              (byte)0xA5, 0x40,                         // Ticket
                0x61, 0x3E, 
                  0x30, 0x3C, 
                    (byte)0xA0, 0x03, 
                      0x02, 0x01, 0x05, 
                    (byte)0xA1, 0x0D, 
                      0x1B, 0x0B, 
                        'E', 'X', 'A', 'M', 'P', 'L', 'E', '.', 'C', 'O', 'M', 
                    (byte)0xA2, 0x13, 
                      0x30, 0x11, 
                        (byte)0xA0, 0x03, 
                          0x02, 0x01, 0x01, 
                        (byte)0xA1, 0x0A, 
                          0x30, 0x08, 
                            0x1B, 0x06, 
                              'c', 'l', 'i', 'e', 'n', 't', 
                    (byte)0xA3, 0x11, 
                      0x30, 0x0F, 
                        (byte)0xA0, 0x03, 
                          0x02, 0x01, 0x11, 
                        (byte)0xA2, 0x08, 
                          0x04, 0x06, 
                            'a', 'b', 'c', 'd', 'e', 'f', 
              (byte)0xA6, 0x11,                         // enc-part
                0x30, 0x0F, 
                  (byte)0xA0, 0x03, 
                    0x02, 0x01, 0x11, 
                  (byte)0xA2, 0x08, 
                    0x04, 0x06, 
                      'a', 'b', 'c', 'd', 'e', 'f', 
        });

        stream.flip();

        // Allocate a AsRep Container
        AsRepContainer asRepContainer = new AsRepContainer( stream );
        
        // Decode the AsRep PDU
        try
        {
            kerberosDecoder.decode( stream, asRepContainer );
        }
        catch ( DecoderException de )
        {
            fail( de.getMessage() );
        }

        AsRep asRep = asRepContainer.getAsRep();
        
        // Check the encoding
        int length = asRep.computeLength();

        // Check the length
        assertEquals( 0xAC, length );
        
        // Check the encoding
        ByteBuffer encodedPdu = ByteBuffer.allocate( length );
        
        try
        {
            encodedPdu = asRep.encode( encodedPdu );
            
            // Check the length
            assertEquals( 0xAC, encodedPdu.limit() );
        }
        catch ( EncoderException ee )
        {
            fail();
        }
    }
    
    
    /**
     * Test the decoding of a AS-REP with nothing in it
     */
    @Test( expected = DecoderException.class)
    public void testAsRepEmpty() throws DecoderException
    {
        Asn1Decoder kerberosDecoder = new Asn1Decoder();

        ByteBuffer stream = ByteBuffer.allocate( 0x02 );
        
        stream.put( new byte[]
            { 0x30, 0x00 } );

        stream.flip();

        // Allocate a AS-REP Container
        Asn1Container asRepContainer = new AsRepContainer( stream );

        // Decode the AS-REP PDU
        kerberosDecoder.decode( stream, asRepContainer );
        fail();
    }
    
    
    /**
     * Test the decoding of a AS-REP with empty Pvno tag
     */
    @Test( expected = DecoderException.class)
    public void testAsRepEmptyPvnoTag() throws DecoderException
    {
        Asn1Decoder kerberosDecoder = new Asn1Decoder();

        ByteBuffer stream = ByteBuffer.allocate( 0x04 );
        
        stream.put( new byte[]
            { 
                0x30, 0x02,
                  (byte)0xA0, 0x00
            } );

        stream.flip();

        // Allocate a AS-REP Container
        Asn1Container asRepContainer = new AsRepContainer( stream );

        // Decode the AS-REP PDU
        kerberosDecoder.decode( stream, asRepContainer );
        fail();
    }
    
    
    /**
     * Test the decoding of a AS-REP with empty Pvno value
     */
    @Test( expected = DecoderException.class)
    public void testAsRepEmptyPvnoValue() throws DecoderException
    {
        Asn1Decoder kerberosDecoder = new Asn1Decoder();

        ByteBuffer stream = ByteBuffer.allocate( 0x06 );
        
        stream.put( new byte[]
            { 
                0x30, 0x04,
                  (byte)0xA0, 0x02,
                    0x02, 0x00
            } );

        stream.flip();

        // Allocate a AS-REP Container
        Asn1Container asRepContainer = new AsRepContainer( stream );

        // Decode the AS-REP PDU
        kerberosDecoder.decode( stream, asRepContainer );
        fail();
    }
}
