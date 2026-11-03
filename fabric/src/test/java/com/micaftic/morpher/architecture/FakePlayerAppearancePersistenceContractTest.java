package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerAppearancePersistenceContractTest {
    @Test
    void localModelReloadUsesTheDedicatedModelPipeline() throws IOException {
        String service = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/model/LocalModelService.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/model/LocalModelService.java"));
        String manager = readFirstExisting(
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));

        assertTrue(service.contains("LocalModelDefinitionCatalog"));
        assertTrue(service.contains("scanDirectoryModels"));
        assertTrue(manager.contains("reloadLocalModels("));
        assertFalse(manager.contains("ServerModelManager"));
    }

    @Test
    void fakePlayerTargetsUseCloudClientState() throws IOException {
        String sync = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"));

        assertTrue(sync.contains("refreshFakeTargets"));
        assertTrue(sync.contains("FakePlayerListCache.replace(rows)"));
        assertTrue(sync.contains("applySelection"));
        assertTrue(sync.contains("CloudEntityPresenceClient"));
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path source = base.resolve(candidate).normalize();
                if (Files.isRegularFile(source)) {
                    return Files.readString(source, StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Required source file not found");
    }
}
