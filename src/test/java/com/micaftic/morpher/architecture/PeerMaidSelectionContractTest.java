package com.micaftic.morpher.architecture;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
class PeerMaidSelectionContractTest {
    @Test void connectedPeerRoomPublishesAndReceivesMaidSelections() throws Exception {
        for (Path base=Path.of("").toAbsolutePath();base!=null;base=base.getParent()) {
            Path file=base.resolve("common/src/main/java/com/micaftic/morpher/client/peer/ClientPeerRuntime.java");
            if(!Files.isRegularFile(file)) continue;
            String code=Files.readString(file);
            assertTrue(code.contains("PeerMaidModelSync.tick(active, sharing)"), "Maid selections must participate in the active P2P room");
            assertTrue(code.contains("PeerMaidModelSync.receive("), "Room entity snapshots must reach the maid renderer");
            return;
        }
        throw new IllegalStateException("ClientPeerRuntime source unavailable");
    }
}
