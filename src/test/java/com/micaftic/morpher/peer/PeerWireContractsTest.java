package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerWireContractsTest {
    @Test void invitationRoundTripsIpv4Ipv6DnsAndRedactsSecret() {
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)7);
        var invite=new PeerInvitation(UUID.randomUUID(),List.of(PeerEndpoint.parse("127.0.0.1:12345"),PeerEndpoint.parse("[2001:db8::1]:12345"),PeerEndpoint.parse("Example.com:443")),new PeerContentRoot("a".repeat(64)),Instant.ofEpochSecond(2_000_000_000L),secret);
        secret[0]=0;assertEquals(7,invite.secret()[0]);
        var copy=PeerInvitation.decode(invite.encode());assertEquals(invite.roomId(),copy.roomId());
        assertEquals(invite.endpoints(),copy.endpoints());assertArrayEquals(invite.secret(),copy.secret());
        assertFalse(invite.toString().contains(invite.encode()));assertFalse(copy.validAt(copy.expiresAt()));
        assertThrows(IllegalArgumentException.class,()->PeerInvitation.decode(invite.encode()+"A"));
        assertThrows(IllegalArgumentException.class,()->PeerInvitation.decode("spm-peer:"+"A".repeat(10000)));
    }
    @Test void ipv6BracketsRequiredAndScopesStayLocal() {
        for(String endpoint:List.of("2001:db8::1:1234","http://host:1234","host:0","host:65536","host:","user@host:1","[notipv6]:1","[::1]","host:1/path"))
            assertThrows(IllegalArgumentException.class,()->PeerEndpoint.parse(endpoint));
        var local=PeerEndpoint.parse("[fe80::1%12]:1234");assertTrue(local.hasLocalScope());
        var invite=new PeerInvitation(UUID.randomUUID(),List.of(local),new PeerContentRoot("b".repeat(64)),Instant.now().plusSeconds(60),new byte[32]);
        assertThrows(IllegalArgumentException.class,invite::encode);
        assertEquals(1,PeerInvitation.single(UUID.randomUUID(),"localhost:1234",new PeerContentRoot("b".repeat(64)),Instant.now(),new byte[32]).endpoints().size());
    }
    @Test void framesRoundTripWithoutExposingPayloadOrMutableArrays()throws Exception {
        byte[] data={1,2,3};var frame=new PeerFrame(PeerFrame.Type.AUTH,12,data);data[0]=0;
        var bytes=new ByteArrayOutputStream();frame.write(new DataOutputStream(bytes));
        var read=PeerFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),false);
        assertArrayEquals(new byte[]{1,2,3},read.payload());assertEquals(12,read.requestId());
        read.payload()[0]=0;assertEquals(1,read.payload()[0]);assertFalse(read.toString().contains("[1, 2, 3]"));
    }
    @Test void untrustedLengthIsRejectedBeforeReadingOrAllocatingPayload()throws Exception {
        for(int size:new int[]{-1,Integer.MAX_VALUE,PeerLimits.CONTROL_BYTES+1}) {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(0x53504D31);out.writeByte(PeerLimits.PROTOCOL_MAJOR);out.writeByte(PeerLimits.PROTOCOL_MINOR);out.writeShort(1);out.writeLong(0);out.writeInt(size);
            var in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            var e=assertThrows(IOException.class,()->PeerFrame.read(in,false));assertFalse(e instanceof EOFException);
        }
        var bytes=new ByteArrayOutputStream();new PeerFrame(PeerFrame.Type.ASSET_BLOCK,1,new byte[]{1}).write(new DataOutputStream(bytes));
        assertThrows(IOException.class,()->PeerFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),false));
    }
}
