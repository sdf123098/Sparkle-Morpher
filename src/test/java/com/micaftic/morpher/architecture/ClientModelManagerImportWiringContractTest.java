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

    @Test
    void cloudMemoryDisplayNeverEntersThePersistenceStore() throws IOException {
        String manager = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));

        assertTrue(manager.contains("importCloudModelInMemory("), "Cloud display must have a memory-only entry point");
        assertTrue(manager.contains("publishMemoryAssembly("), "memory publication must be a distinct runtime path");
        String publication = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/CloudMemoryPublication.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/CloudMemoryPublication.java"));
        assertTrue(manager.contains("CloudMemoryPublication.publish("), "runtime must execute the audited publication seam");
        assertTrue(publication.contains("LocalModelCatalog.Entry.memory("), "memory sources must use the Path-free catalog entry");
        assertTrue(publication.contains("library.importedMemory("), "Cloud bytes must still enter the restricted digest table");
        assertTrue(manager.contains("loadMemoryModelSource("), "CPU trim must rebuild from retained bytes instead of disk");

        int start = manager.indexOf("private static void importMemoryModel(");
        int end = manager.indexOf("public static void loadPeerModel(", start);
        assertTrue(start >= 0 && end > start, "memory import method must exist");
        String memoryImport = manager.substring(start, end);
        assertTrue(!memoryImport.contains(".prepare("), "memory import must not prepare a persisted source");
        assertTrue(!memoryImport.contains("CLOUD_IMPORT_STORE"), "memory import must not touch the Cloud model store");

        int publishStart = manager.indexOf("private static void publishMemoryAssembly(");
        int publishEnd = manager.indexOf("private static void publishImportedAssembly(", publishStart);
        assertTrue(publishStart >= 0 && publishEnd > publishStart, "memory publication method must exist");
        String memoryPublish = manager.substring(publishStart, publishEnd);
        assertTrue(!memoryPublish.contains("LocalModelImportStore"), "memory publication must not persist a model file");
        assertTrue(!memoryPublish.contains("fingerprint(persisted)"), "memory publication must fingerprint bytes, not a path");
    }

    @Test
    void legacyCloudDiskCopiesAreNeverRestoredOrScanned() throws IOException {
        String manager = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));
        String controller = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreenController.java"));

        assertTrue(!manager.contains("CloudModelStorageMigration"), "old Cloud copies must not be migrated or restored");
        assertTrue(!manager.contains("scanLocalModelSources(ModelStoragePaths.cloudModels()"),
                "legacy cloud_models files must not enter the local catalog");
        assertTrue(!manager.contains("public static void importCloudModel("),
                "Cloud display must have no disk import entry point");
        assertTrue(manager.contains("importLocalModel(String modelId")
                        && manager.contains("if (com.micaftic.morpher.core.model.CloudAssetIdentity.isRuntimeModelId(modelId))"),
                "the local import entry point must reject Cloud runtime identities");
        assertTrue(!controller.contains("openCloudModelFolder"),
                "the UI must not offer a legacy Cloud-copy folder");
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
