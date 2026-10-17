package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerTlsTransportTest {
    private static final char[] PASSWORD="test-key-not-a-user-credential".toCharArray();
    @Test void keyStoreRoundTripAndPrivateContextsDoNotChangeCloudDefault()throws Exception {
        var before=SSLContext.getDefault();var identity=PeerTlsIdentity.create(PASSWORD);
        var bytes=new ByteArrayOutputStream();identity.save(bytes,PASSWORD);
        var copy=PeerTlsIdentity.load(new ByteArrayInputStream(bytes.toByteArray()),PASSWORD);
        assertEquals(identity.pin(),copy.pin());identity.serverContext(PASSWORD);PeerPinnedTls.context(identity.pin());
        assertSame(before,SSLContext.getDefault());
    }
    @Test void threeIndependentPinnedClientsOnOnePortAndWrongPinFailsBeforeAuth()throws Exception {
        var identity=PeerTlsIdentity.create(PASSWORD);var count=new java.util.concurrent.atomic.AtomicInteger();
        try(var host=new PeerTlsTransport(identity.serverContext(PASSWORD))) {
            var address=host.listen(InetAddress.getByName("127.0.0.1"),0,socket->{
                var frame=PeerFrame.read(new DataInputStream(socket.getInputStream()),false);
                assertEquals(PeerFrame.Type.HELLO,frame.type());count.incrementAndGet();
                new PeerFrame(PeerFrame.Type.WELCOME,frame.requestId(),new byte[]{42}).write(new DataOutputStream(socket.getOutputStream()));
            });
            for(int i=0;i<3;i++)try(var guest=new PeerTlsTransport(PeerPinnedTls.context(identity.pin()))) {
                var socket=guest.connect(address,2000);
                new PeerFrame(PeerFrame.Type.HELLO,i,new byte[0]).write(new DataOutputStream(socket.getOutputStream()));
                assertEquals(42,PeerFrame.read(new DataInputStream(socket.getInputStream()),false).payload()[0]);
            }
            try(var wrong=new PeerTlsTransport(PeerPinnedTls.context(new PeerContentRoot("0".repeat(64))))) {
                assertThrows(IOException.class,()->wrong.connect(address,1000));assertEquals(0,wrong.activeConnections());
            }
            assertEquals(3,count.get());
        }
    }
    @Test void blockedReadIsCancelledAndSlowAssetDoesNotOccupyControl()throws Exception {
        var identity=PeerTlsIdentity.create(PASSWORD);var entered=new CountDownLatch(1);var exited=new CountDownLatch(1);
        try(var host=new PeerTlsTransport(identity.serverContext(PASSWORD));var guest=new PeerTlsTransport(PeerPinnedTls.context(identity.pin()))) {
            var endpoint=host.listen(InetAddress.getByName("127.0.0.1"),0,socket->{
                var frame=PeerFrame.read(new DataInputStream(socket.getInputStream()),true);
                if(frame.type()==PeerFrame.Type.ASSET_AUTH) {
                    entered.countDown();try{socket.getInputStream().read();}finally{exited.countDown();}
                } else new PeerFrame(PeerFrame.Type.PONG,1,new byte[]{1}).write(new DataOutputStream(socket.getOutputStream()));
            });
            var slow=guest.connect(endpoint,2000);new PeerFrame(PeerFrame.Type.ASSET_AUTH,1,new byte[0]).write(new DataOutputStream(slow.getOutputStream()));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var control=guest.connect(endpoint,2000);new PeerFrame(PeerFrame.Type.PING,1,new byte[0]).write(new DataOutputStream(control.getOutputStream()));
            assertEquals(PeerFrame.Type.PONG,PeerFrame.read(new DataInputStream(control.getInputStream()),false).type());
            host.close();assertTrue(exited.await(2,TimeUnit.SECONDS));assertEquals(0,host.activeConnections());
            host.close();assertThrows(IOException.class,()->host.connect(endpoint,100));
        }
    }
    @Test void ipv6LoopbackUsesActualListenerAndPin()throws Exception {
        var identity=PeerTlsIdentity.create(PASSWORD);
        try(var host=new PeerTlsTransport(identity.serverContext(PASSWORD));var guest=new PeerTlsTransport(PeerPinnedTls.context(identity.pin()))) {
            var address=host.listen(InetAddress.getByName("::1"),0,socket->new PeerFrame(PeerFrame.Type.WELCOME,0,new byte[0]).write(new DataOutputStream(socket.getOutputStream())));
            var socket=guest.connect(address,2000);assertEquals(PeerFrame.Type.WELCOME,PeerFrame.read(new DataInputStream(socket.getInputStream()),false).type());
        }
    }
    @Test void roomPortCanBeReusedAfterActiveCloseButCannotHaveTwoLiveListeners() throws Exception {
        var identity = PeerTlsIdentity.create(PASSWORD); InetSocketAddress address;
        try(var first = new PeerTlsTransport(identity.serverContext(PASSWORD)); var guest = new PeerTlsTransport(PeerPinnedTls.context(identity.pin()))) {
            address = first.listen(InetAddress.getByName("127.0.0.1"), 0, socket -> {
                new PeerFrame(PeerFrame.Type.PONG, 0, new byte[0]).write(new DataOutputStream(socket.getOutputStream())); socket.getInputStream().read();
            });
            var socket = guest.connect(address, 2000); PeerFrame.read(new DataInputStream(socket.getInputStream()), false);
            try(var duplicate = new PeerTlsTransport(identity.serverContext(PASSWORD))) {
                var fixed = address; assertThrows(IOException.class, () -> duplicate.listen(fixed.getAddress(), fixed.getPort(), ignored -> {}));
            }
            first.close();
        }
        try(var replacement = new PeerTlsTransport(identity.serverContext(PASSWORD))) {
            assertEquals(address.getPort(), replacement.listen(address.getAddress(), address.getPort(), ignored -> {}).getPort());
        }
    }
}
