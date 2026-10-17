package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerFailureDiagnosisTest {
    @TempDir Path temporary;

    @Test void unapprovedWorldUuidIsReportedInsteadOfEof() throws Exception {
        char[] password = "test".toCharArray(); var key = PeerTlsIdentity.create(password);
        var invite = PeerInvitation.single(UUID.randomUUID(), "localhost:1234", key.pin(), Instant.now().plusSeconds(60), new byte[32]);
        try (var server = new PeerRoomServer(invite, 3, key, password, List.of())) {
            var address = server.listen(InetAddress.getByName("127.0.0.1"), 0);
            var offered = new PeerInvitation(invite.roomId(), List.of(new PeerEndpoint("127.0.0.1", address.getPort())), invite.pin(), invite.expiresAt(), invite.secret());
            var failure = assertThrows(IOException.class, () -> PeerRoomClient.join(offered, UUID.randomUUID(), 3000));
            assertTrue(Objects.toString(failure.getMessage(), "").contains("UNAPPROVED_UUID"), failure.toString());
        }
    }

    @Test void rejectedPinSurvivesCandidateRace() throws Exception {
        char[] password = "test".toCharArray(); var key = PeerTlsIdentity.create(password);
        try (var server = new PeerTlsTransport(key.serverContext(password)); var dialer = new PeerDialer()) {
            var address = server.listen(InetAddress.getByName("127.0.0.1"), 0, socket -> fail("Wrong pin must not receive AUTH"));
            var invite = PeerInvitation.single(UUID.randomUUID(), "127.0.0.1:" + address.getPort(), new PeerContentRoot("0".repeat(64)), Instant.now().plusSeconds(60), new byte[32]);
            var failure = assertThrows(IOException.class, () -> dialer.connect(invite, 2000));
            assertTrue(failure.getMessage().contains("PIN_MISMATCH"), failure.toString());
        }
    }

    @Test void runtimeRetainsListenFailureAfterClosingResources() throws Exception {
        var states = new LinkedBlockingQueue<String>();
        try (var occupied = new ServerSocket(0, 1, InetAddress.getByName("0.0.0.0"));
             var runtime = new PeerRoomRuntime(temporary, Runnable::run, ignored -> {}, states::add)) {
            UUID player = UUID.randomUUID();
            runtime.host(new PeerEndpoint("127.0.0.1", occupied.getLocalPort()), player, List.of(player), ignored -> fail("Occupied listener cannot advertise"));
            String status = states.poll(8, TimeUnit.SECONDS);
            assertNotNull(status);
            assertTrue(status.contains("LISTEN"), status);
        }
    }
}
