package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelManagerImportWiringContractTest {
    @Test
    void pickedImportUsesTheTestedCoordinatorAndRuntimeTransactionBoundary() throws IOException {
        String manager = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));

        assertTrue(manager.contains("ImportCoordinator.importPrepared("), "picked import must use the coordinator transaction");
        assertTrue(manager.contains("ImportCoordinator.parsePickedBytes("), "picked bytes must pass through typed parsing");
        assertTrue(manager.contains("ClientModelBundleAssembler.buildParsedBundle("), "legacy payload must use the legacy assembler");
        assertTrue(manager.contains("buildGltfAssembly(gltfResult.model(), modelKey)"), "glTF payload must use the glTF assembler");
        assertTrue(manager.contains("synchronized (MODEL_RUNTIME_STOP_LOCK)"), "runtime publication must use the stop lock");
        assertTrue(manager.contains("ImportCoordinator.commitBuiltCandidate("), "candidate commit and publish must use the shared boundary");
        assertTrue(manager.contains("localImportRequests.isCurrent(importLease)"), "runtime publication must recheck the active request lease");
        assertTrue(manager.contains("prepared::commit"), "source persistence must be the transaction commit operation");
        assertTrue(manager.contains("publishImportedAssembly("), "the committed candidate must enter the normal publication path");
        assertTrue(manager.contains("assembly -> releaseModelAssembly(modelKey, assembly)"), "failed candidates must be released by the runtime owner");
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path source = base.resolve(candidate).normalize();
                if (Files.isRegularFile(source)) return Files.readString(source, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Required ClientModelManager source file not found");
    }
}
