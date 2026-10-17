package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerModelEntrancesTest {
    @TempDir Path temporary;
    private static int port()throws IOException {try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    @Test void explicitIpv4Ipv6ListenersShareEpochRosterAndAssetAuthorization()throws Exception {
        int v4=port(),v6=port();UUID a=UUID.randomUUID(),b=UUID.randomUUID();var invited=new CompletableFuture<PeerInvitation>();var appearance=new CompletableFuture<PeerRoomCoordinator.Member>();
        var entrances=PeerModelEntrances.parse("127.0.0.1:"+v4+",[::1]:"+v6,"127.0.0.1:"+v4+",[::1]:"+v6);
        try(var runtime=new PeerRoomRuntime(temporary.resolve("host"),Runnable::run,ignored->{},ignored->{})) {
            runtime.host(entrances,a,List.of(a,b),invited::complete);var invite=invited.get(8,TimeUnit.SECONDS);
            assertEquals(2,invite.endpoints().size());assertTrue(runtime.listenerCoverage().stream().anyMatch(endpoint->endpoint.host().equals("::1")));
            var selected=new PeerInvitation(invite.roomId(),List.of(new PeerEndpoint("::1",v6)),invite.pin(),invite.expiresAt(),invite.secret());
            try(var guest=PeerRoomClient.join(selected,b,3000)) {
                assertEquals(invite.roomId(),guest.session().roomId());assertEquals(2,guest.poll().members().size());
                Path source=Files.createDirectory(temporary.resolve("local"));Files.writeString(source.resolve("m.bbmodel"),"{\"name\":\"entrances\"}");
                runtime.share(()->new LocalShareSourceResolver.Selection(source,"m.bbmodel","bbmodel-v1",Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL)),"default");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);PeerRoomCoordinator.Member member=null;
                while(System.nanoTime()<deadline){member=guest.poll().members().stream().filter(value->value.session().minecraftUuid().equals(a)&&value.appearance()!=null).findFirst().orElse(null);if(member!=null&&runtime.publishedAppearance()!=null)break;Thread.sleep(20);}
                assertNotNull(member);var received=guest.download(member,new PeerAssetStore(temporary.resolve("guest")),()->true);assertTrue(Files.readString(received.entrypoint()).contains("entrances"));
                assertEquals(guest.session().epoch(),guest.poll().epoch());
            }
        }
    }
    @Test void probeChecksInvitedModelPinWithoutAuthenticatingOrUsingTheGamePort()throws Exception {
        char[] password="test".toCharArray();var identity=PeerTlsIdentity.create(password);var expiry=Instant.now().plusSeconds(60);UUID room=UUID.randomUUID();byte[] secret=new byte[32];
        try(var server=new PeerRoomServer(PeerInvitation.single(room,"localhost:12345",identity.pin(),expiry,secret),3,identity,password,List.of())) {
            var bound=server.listen(InetAddress.getByName("127.0.0.1"),0);var invite=PeerInvitation.single(room,"127.0.0.1:"+bound.getPort(),identity.pin(),expiry,secret);
            assertTrue(PeerEndpointProbe.probe(invite,3000).connectedAuthority().endsWith(":"+bound.getPort()));
            assertThrows(IOException.class,()->PeerRoomClient.join(invite,UUID.randomUUID(),3000));
            var forged=PeerInvitation.single(room,"127.0.0.1:"+bound.getPort(),PeerContentRoot.digest(new byte[]{9}),expiry,secret);assertThrows(IOException.class,()->PeerEndpointProbe.probe(forged,3000));
        }
    }
    @Test void automaticRoutingUsesMeasuredFamilyAndRefusesAnUnspecifiedPublicMapping()throws Exception {
        var coverage=List.of(new PeerEndpoint("::1",22222),new PeerEndpoint("127.0.0.1",33333));
        assertEquals(33333,PeerModelEntrances.automaticRoute(InetAddress.getByName("192.168.1.20"),coverage).port());
        assertEquals(22222,PeerModelEntrances.automaticRoute(InetAddress.getByName("fd00::123"),coverage).port());
        assertThrows(IllegalArgumentException.class,()->PeerModelEntrances.automaticRoute(InetAddress.getByName("203.0.113.20"),coverage));
        assertThrows(IllegalArgumentException.class,()->PeerModelEntrances.parse("example.invalid:22222","model.invalid:30000"));
        assertThrows(IllegalArgumentException.class,()->PeerModelEntrances.parse("0.0.0.0:22222","[fe80::1%2]:30000"));
        var invite=PeerInvitation.single(UUID.randomUUID(),"model.invalid:30000",PeerContentRoot.digest(new byte[]{1}),Instant.now().plusSeconds(60),new byte[32]);
        var scoped=PeerModelEntrances.localRoute(invite,"[fe80::1%2]:30000");assertTrue(scoped.endpoints().getFirst().hasLocalScope());assertEquals(invite.pin(),scoped.pin());assertEquals(invite.roomId(),scoped.roomId());assertArrayEquals(invite.secret(),scoped.secret());assertThrows(IllegalArgumentException.class,scoped::encode);
    }
}
