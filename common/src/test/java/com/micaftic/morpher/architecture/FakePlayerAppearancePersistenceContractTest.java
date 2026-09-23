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
        String manager = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/model/ServerModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/model/ServerModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/model/ServerModelManager.java"));

        assertTrue(manager.contains("loadModels("));
        assertTrue(!manager.contains("nativeSyncModels"));
        assertTrue(!manager.contains("LegacyModelSyncProtocol"));
    }

    @Test
    void levelChangeResynchronizesThePlayerAppearance() throws IOException {
        String capabilityEvent = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"));

        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS"));
        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS.remove("));
        assertTrue(capabilityEvent.contains("syncPlayerModelToSelf(serverPlayer)") || capabilityEvent.contains("syncPlayerModelToSelf(sp)"));
        assertTrue(capabilityEvent.contains("syncPlayerModelToTracking(serverPlayer, false)") || capabilityEvent.contains("syncPlayerModelToTracking(sp, false)"));
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Required source file not found");
    }
}
