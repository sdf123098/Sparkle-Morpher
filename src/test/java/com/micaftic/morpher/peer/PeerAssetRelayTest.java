package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerAssetRelayTest {
    @TempDir Path temporary;
    @Test void guestUploadsOnceAndOtherGuestsReceiveThroughRelayWithCurrentAuthorization() throws Exception {
        char[] password = "test".toCharArray(); var key = PeerTlsIdentity.create(password); byte[] secret = new byte[32];
        UUID roomId = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var invitation = PeerInvitation.single(roomId, "localhost:1234", key.pin(), Instant.now().plusSeconds(60), secret);
        var relay = new PeerAssetStore(temporary.resolve("relay"));
        Path source = Files.createDirectory(temporary.resolve("local")); byte[] bytes = new byte[600_000]; new Random(1).nextBytes(bytes); Files.write(source.resolve("guest.ysm"), bytes);
        try(var snapshot = new PeerAssetSnapshotBuilder(temporary.resolve("snapshots")).freeze(source, "guest.ysm", Map.of("guest.ysm", PeerAssetManifest.Role.MODEL), () -> true);
            var host = new PeerRoomServer(invitation, 3, key, password, List.of(a, b, c), relay)) {
            var bind = host.listen(InetAddress.getByName("127.0.0.1"), 0);
            var invite = PeerInvitation.single(roomId, "127.0.0.1:" + bind.getPort(), key.pin(), invitation.expiresAt(), secret);
            var bStore = new PeerAssetStore(temporary.resolve("b")); var cStore = new PeerAssetStore(temporary.resolve("c"));
            try(var owner = PeerRoomClient.join(invite, b, 3000); var observerA = PeerRoomClient.join(invite, a, 3000); var observerC = PeerRoomClient.join(invite, c, 3000)) {
                var appearance = new PeerAppearance(1, new PeerModelIdentity(snapshot.manifest().contentRoot(), "ysm-v1"), "default");
                owner.publish(appearance); var verified = bStore.copy(snapshot, () -> true); owner.upload(appearance, verified, () -> true);
                var member = observerC.poll().members().stream().filter(m -> m.session().minecraftUuid().equals(b)).findFirst().orElseThrow();
                assertArrayEquals(bytes, Files.readAllBytes(observerC.download(member, cStore, () -> true).entrypoint()));
                assertArrayEquals(bytes, Files.readAllBytes(observerA.download(member, new PeerAssetStore(temporary.resolve("a")), () -> true).entrypoint()));
                owner.publish(new PeerAppearance(2, null, null));
                assertThrows(IOException.class, () -> observerC.download(member, cStore, () -> true));
            }
        }
    }
    @Test void corruptedOrIncompleteReceiveDoesNotPublishAndReservationsAreReturned() throws Exception {
        var store = new PeerAssetStore(temporary.resolve("cache"), 1000);
        var bytes = new byte[]{1, 2, 3}; var manifest = new PeerAssetManifest(1, "m.ysm", List.of(new PeerAssetManifest.Resource("m.ysm", PeerAssetManifest.Role.MODEL, 3, PeerContentRoot.digest(bytes))));
        try(var receive = store.begin(manifest, () -> true)) { receive.append(0, 0, new byte[]{3, 2, 1}); assertThrows(IOException.class, () -> receive.finishFile(0)); }
        assertNull(store.find(manifest.contentRoot()));
        try(var receive = store.begin(manifest, () -> true)) { receive.append(0, 0, bytes); receive.finishFile(0); assertArrayEquals(bytes, Files.readAllBytes(receive.commit().entrypoint())); }
        assertNotNull(store.find(manifest.contentRoot()));
    }
    @Test void newlyVerifiedTransferRepairsTamperedPeerCache() throws Exception {
        var store = new PeerAssetStore(temporary.resolve("cache")); byte[] bytes = {1, 2, 3};
        var manifest = new PeerAssetManifest(1, "m.ysm", List.of(new PeerAssetManifest.Resource("m.ysm", PeerAssetManifest.Role.MODEL, 3, PeerContentRoot.digest(bytes))));
        PeerAssetStore.Verified first;
        try(var receive = store.begin(manifest, () -> true)) { receive.append(0, 0, bytes); receive.finishFile(0); first = receive.commit(); }
        Files.write(first.entrypoint(), new byte[]{9, 9, 9}); assertThrows(IOException.class, () -> store.find(manifest.contentRoot()));
        try(var receive = store.begin(manifest, () -> true)) { receive.append(0, 0, bytes); receive.finishFile(0); assertArrayEquals(bytes, Files.readAllBytes(receive.commit().entrypoint())); }
        assertNotNull(store.find(manifest.contentRoot()));
    }
}
