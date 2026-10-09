package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelResidencyOwnershipContractTest {
    @Test
    void managerDelegatesResidencyStateToTheSingleOwner() throws IOException {
        String manager = readFirstExisting(
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"));
        String owner = readFirstExisting(Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelResidency.java"));

        for (String retiredField : new String[]{"modelAssemblyMap", "modelLastUsedAt", "gpuCacheTrimCoordinator",
                "cpuReloadRequests", "deferredAssemblyReleases", "lastLazyModelLoading", "lastModelTrimMillis"}) {
            assertFalse(manager.contains(retiredField), "manager must not own duplicate " + retiredField);
        }
        assertTrue(manager.contains("ClientModelResidency RESIDENCY"));
        assertTrue(manager.contains("RESIDENCY.publishAssemblies("));
        for (String ownerField : new String[]{"assemblies", "lastUsedAt", "gpuTrim", "cpuReloads",
                "deferredReleases", "lastLazyLoadingMode", "lastTrimMillis"}) {
            assertTrue(owner.contains(ownerField), "owner must retain " + ownerField);
        }
        assertTrue(manager.contains("RESIDENCY.shouldTrimAt("));
        assertTrue(manager.contains("RESIDENCY.updateLazyLoadingMode("));
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path source = base.resolve(candidate).normalize();
                if (Files.isRegularFile(source)) return Files.readString(source, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Required ClientModelManager/ClientModelResidency source file not found");
    }
}
