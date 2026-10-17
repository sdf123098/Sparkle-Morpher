package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** TCP/TLS protocol and byte convergence evidence only; never labelled real-game visibility. */
class PeerObservationAndCacheTest {
    @TempDir Path temporary;
    @Test void threeFiveTenMembersConvergeEveryDirectedAssetAndReport() throws Exception {
        for(int size : List.of(3, 5, 10)) matrix(size);
    }
    private void matrix(int size) throws Exception {
        char[] password = "test".toCharArray(); var key = PeerTlsIdentity.create(password);
        List<UUID> players = new ArrayList<>(); for(int i=0;i<size;i++) players.add(UUID.randomUUID());
        // Include an offline-name UUID in the same room as arbitrary online UUIDs.
        players.set(1, UUID.nameUUIDFromBytes("OfflinePlayer:PeerGuest".getBytes(StandardCharsets.UTF_8)));
        var invite = PeerInvitation.single(UUID.randomUUID(), "localhost:1234", key.pin(), Instant.now().plusSeconds(120), new byte[32]);
        var relay = new PeerAssetStore(temporary.resolve("relay" + size));
        List<PeerRoomClient> clients = new ArrayList<>(); List<PeerAssetStore> caches = new ArrayList<>();
        try(var server = new PeerRoomServer(invite, size, key, password, players, relay)) {
            server.requireConnectionBindings();
            var address = server.listen(InetAddress.getByName("127.0.0.1"), 0);
            var base = new PeerInvitation(invite.roomId(), List.of(new PeerEndpoint("127.0.0.1", address.getPort())), invite.pin(), invite.expiresAt(), invite.secret());
            for(int i=0;i<size;i++) {
                clients.add(PeerRoomClient.join(server.invitationFor(base, players.get(i)), players.get(i), 3000));
                caches.add(new PeerAssetStore(temporary.resolve("member" + size + "-" + i)));
            }
            // A cannot authenticate using B's individually bound credential.
            assertThrows(IOException.class, () -> PeerRoomClient.join(server.invitationFor(base, players.get(1)), players.get(0), 3000));
            for(int i=0;i<size;i++) {
                Path source = Files.createDirectory(temporary.resolve("source" + size + "-" + i));
                Files.writeString(source.resolve("m.bbmodel"), "{\"name\":\"owner" + i + "\"}");
                try(var snapshot = new PeerAssetSnapshotBuilder(temporary.resolve("frozen" + size)).freeze(source, "m.bbmodel", Map.of("m.bbmodel", PeerAssetManifest.Role.MODEL), () -> true)) {
                    var appearance = new PeerAppearance(1, new PeerModelIdentity(snapshot.manifest().contentRoot(), "bbmodel-v1"), "texture" + i);
                    clients.get(i).publish(appearance); clients.get(i).upload(appearance, caches.get(i).copy(snapshot, () -> true), () -> true);
                }
            }
            int directions = 0;
            for(int observer=0;observer<size;observer++) for(var owner : clients.get(observer).poll().members()) {
                if(owner.session().equals(clients.get(observer).session())) continue;
                int index = players.indexOf(owner.session().minecraftUuid());
                var received = clients.get(observer).download(owner, caches.get(observer), () -> true);
                assertEquals("{\"name\":\"owner" + index + "\"}", Files.readString(received.entrypoint()));
                assertTrue(clients.get(observer).observe(new PeerObservation.Report(owner.session(), 1, PeerObservation.State.VERIFIED, null))); directions++;
            }
            assertEquals(size * (size - 1), directions);
            for(var publisher : clients) {
                assertEquals(size - 1, publisher.observations().size());
                assertTrue(publisher.observations().stream().allMatch(receipt -> receipt.report().state() == PeerObservation.State.VERIFIED));
            }
            var old = clients.get(1).poll().members().stream().filter(m -> m.session().equals(clients.get(0).session())).findFirst().orElseThrow();
            clients.get(0).publish(new PeerAppearance(2, null, null));
            assertEquals(0, clients.get(0).observations().size());
            assertFalse(clients.get(1).observe(new PeerObservation.Report(old.session(), 1, PeerObservation.State.APPLIED, null)));
            assertThrows(IOException.class, () -> clients.get(1).download(old, caches.get(1), () -> true));
            System.out.println("Core byte/report convergence members=" + size + " directions=" + directions + " realGameVisibility=NOT_RUN");
        } finally { clients.forEach(PeerRoomClient::close); }
    }

    @Test void hotVerifiedCacheNeedsFreshAuthorizationButNoRelayBytes() throws Exception {
        char[] password="test".toCharArray(); var key=PeerTlsIdentity.create(password); UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        var invitation=PeerInvitation.single(UUID.randomUUID(), "localhost:1234", key.pin(), Instant.now().plusSeconds(60), new byte[32]);
        var relay=new PeerAssetStore(temporary.resolve("relay")); var cache=new PeerAssetStore(temporary.resolve("receiver"));
        Path source=Files.createDirectory(temporary.resolve("source")); Files.writeString(source.resolve("m.bbmodel"), "{\"name\":\"hot\"}");
        try(var server=new PeerRoomServer(invitation, 2, key, password, List.of(a,b), relay);
            var frozen=new PeerAssetSnapshotBuilder(temporary.resolve("frozen")).freeze(source,"m.bbmodel", Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL),()->true)) {
            var address=server.listen(InetAddress.getByName("127.0.0.1"),0);
            var invite=new PeerInvitation(invitation.roomId(),List.of(new PeerEndpoint("127.0.0.1",address.getPort())),invitation.pin(),invitation.expiresAt(),invitation.secret());
            try(var owner=PeerRoomClient.join(invite,a,3000);var observer=PeerRoomClient.join(invite,b,3000)) {
                var appearance=new PeerAppearance(1,new PeerModelIdentity(frozen.manifest().contentRoot(),"bbmodel-v1"),"default");
                owner.publish(appearance); owner.upload(appearance, new PeerAssetStore(temporary.resolve("sender")).copy(frozen,()->true),()->true);
                var member=observer.poll().members().stream().filter(m->m.session().equals(owner.session())).findFirst().orElseThrow();
                var received=observer.download(member,cache,()->true);
                // If a hot download still requested bytes, this deliberate missing relay file would fail it.
                Files.delete(relay.find(appearance.model().contentRoot()).entrypoint());
                assertEquals(received.root(), observer.download(member,cache,()->true).root());
                owner.publish(new PeerAppearance(2,null,null));
                assertThrows(IOException.class,()->observer.download(member,cache,()->true));
            }
        }
    }
}
