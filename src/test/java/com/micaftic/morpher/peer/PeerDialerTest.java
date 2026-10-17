package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerDialerTest {
    @Test void fallbackUsesOnePinnedWinnerAcrossInvitedAddresses()throws Exception {
        char[] password="test".toCharArray();var identity=PeerTlsIdentity.create(password);
        try(var host=new PeerTlsTransport(identity.serverContext(password));var dialer=new PeerDialer()) {
            var endpoint=host.listen(InetAddress.getByName("::1"),0,socket->new PeerFrame(PeerFrame.Type.WELCOME,1,new byte[0]).write(new DataOutputStream(socket.getOutputStream())));
            int unavailable;try(var closed=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){unavailable=closed.getLocalPort();}
            var invite=new PeerInvitation(UUID.randomUUID(),List.of(new PeerEndpoint("127.0.0.1",unavailable),new PeerEndpoint("::1",endpoint.getPort())),identity.pin(),Instant.now().plusSeconds(60),new byte[32]);
            try(var winner=dialer.connect(invite,3000)){assertEquals(PeerFrame.Type.WELCOME,PeerFrame.read(new DataInputStream(winner.socket().getInputStream()),false).type());}
        }
    }
    @Test void wrongServiceNeverReceivesInviteSecretAndIsNotAccepted()throws Exception {
        char[] password="test".toCharArray();var identity=PeerTlsIdentity.create(password);var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var host=new PeerTlsTransport(identity.serverContext(password));var dialer=new PeerDialer()) {
            var address=host.listen(InetAddress.getByName("127.0.0.1"),0,socket->calls.incrementAndGet());
            var invite=PeerInvitation.single(UUID.randomUUID(),"127.0.0.1:"+address.getPort(),new PeerContentRoot("0".repeat(64)),Instant.now().plusSeconds(60),new byte[32]);
            assertThrows(IOException.class,()->dialer.connect(invite,1000));assertEquals(0,calls.get());
        }
    }
    @Test void resolverFailureAndClosedDialerAreBounded() {
        try(var dialer=new PeerDialer(host->{throw new UnknownHostException("test");})) {
            var invite=PeerInvitation.single(UUID.randomUUID(),"invalid.example:1234",new PeerContentRoot("a".repeat(64)),Instant.now().plusSeconds(60),new byte[32]);
            assertThrows(IOException.class,()->dialer.connect(invite,100));dialer.close();assertThrows(IOException.class,()->dialer.connect(invite,100));
        }
    }
    @Test void dnsCompletingDuringPollStillSubmitsItsCandidate()throws Exception {
        char[] password="test".toCharArray();var key=PeerTlsIdentity.create(password);
        try(var server=new PeerTlsTransport(key.serverContext(password));var dialer=new PeerDialer(host->{
            try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new UnknownHostException();}
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        })) {
            var address=server.listen(InetAddress.getByName("127.0.0.1"),0,socket->new PeerFrame(PeerFrame.Type.PONG,0,new byte[0]).write(new DataOutputStream(socket.getOutputStream())));
            var invite=PeerInvitation.single(UUID.randomUUID(),"test.example:"+address.getPort(),key.pin(),Instant.now().plusSeconds(60),new byte[32]);
            try(var connection=dialer.connect(invite,2000)){assertEquals(PeerFrame.Type.PONG,PeerFrame.read(new DataInputStream(connection.socket().getInputStream()),false).type());}
        }
    }
}
