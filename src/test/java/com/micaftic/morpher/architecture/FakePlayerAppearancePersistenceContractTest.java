package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerAppearancePersistenceContractTest {
    @Test
    void modelCatalogReloadDoesNotReintroduceServerModelSync() throws IOException {
        String manager = Files.readString(
                Path.of("common/src/main/java/com/micaftic/morpher/model/ServerModelManager.java"),
                StandardCharsets.UTF_8);
        assertTrue(manager.contains("loadModels("));
        assertTrue(!manager.contains("nativeSyncModels"));
        assertTrue(!manager.contains("LegacyModelSyncProtocol"));
    }

    @Test
    void levelChangeResynchronizesThePlayerAppearance() throws IOException {
        String capabilityEvent = Files.readString(
                Path.of("common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                StandardCharsets.UTF_8);
        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS"));
        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS.remove("));
        assertTrue(capabilityEvent.contains("syncPlayerModelToSelf(serverPlayer)"));
        assertTrue(capabilityEvent.contains("syncPlayerModelToTracking(serverPlayer, false)"));
    }
}
