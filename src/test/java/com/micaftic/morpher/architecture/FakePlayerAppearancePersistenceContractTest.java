package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerAppearancePersistenceContractTest {
    @Test
    void modelCatalogReloadDoesNotReintroduceServerModelSync() throws IOException {
        Path repository = locateRepository();
        String manager = Files.readString(
                repository.resolve("src/neoforge/java/com/micaftic/morpher/model/ServerModelManager.java"),
                StandardCharsets.UTF_8);
        assertTrue(manager.contains("loadModels("));
        assertTrue(!manager.contains("nativeSyncModels"));
        assertTrue(!manager.contains("LegacyModelSyncProtocol"));
    }

    @Test
    void levelChangeResynchronizesThePlayerAppearance() throws IOException {
        Path repository = locateRepository();
        String capabilityEvent = Files.readString(
                repository.resolve("src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                StandardCharsets.UTF_8);
        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS"));
        assertTrue(capabilityEvent.contains("LAST_PLAYER_LEVELS.remove("));
        assertTrue(capabilityEvent.contains("syncPlayerModelToSelf(serverPlayer)") || capabilityEvent.contains("syncPlayerModelToSelf(sp)"));
        assertTrue(capabilityEvent.contains("syncPlayerModelToTracking(serverPlayer, false)") || capabilityEvent.contains("syncPlayerModelToTracking(sp, false)"));
    }

    private static Path locateRepository() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve(".github/workflows/ci.yml"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Could not locate repository root");
        return current;
    }
}
