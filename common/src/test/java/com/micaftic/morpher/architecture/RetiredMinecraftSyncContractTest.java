package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetiredMinecraftSyncContractTest {
    @Test
    void retiredServerProtocolAndConfigurationSourcesStayOutOfRuntime() {
        List<String> retiredSources = List.of(
                "common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java",
                "src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java",
                "common/src/main/java/com/micaftic/morpher/config/ServerConfig.java",
                "src/neoforge/java/com/micaftic/morpher/config/ServerConfig.java",
                "common/src/main/java/com/micaftic/morpher/model/ServerModelManager.java",
                "src/neoforge/java/com/micaftic/morpher/model/ServerModelManager.java",
                "common/src/main/java/com/micaftic/morpher/network/NetworkHandler.java",
                "src/neoforge/java/com/micaftic/morpher/network/NetworkHandler.java",
                "common/src/main/java/com/micaftic/morpher/core/api/network/PacketContext.java",
                "common/src/main/java/com/micaftic/morpher/capability/ModelInfoCapability.java",
                "src/neoforge/java/com/micaftic/morpher/capability/ModelInfoCapability.java",
                "common/src/main/java/com/micaftic/morpher/network/message/C2SModelSyncPayload.java",
                "common/src/main/java/com/micaftic/morpher/network/message/S2CModelSyncPayload.java",
                "common/src/main/java/com/micaftic/morpher/network/message/S2CSyncPlayerStatePacket.java",
                "common/src/main/java/com/micaftic/morpher/network/message/S2CSyncVehicleModelPacket.java",
                "src/neoforge/java/com/micaftic/morpher/network/message/S2CSyncStarModelsPacket.java");

        for (String source : retiredSources) {
            assertFalse(existsInAnyAncestor(Path.of(source)), source);
        }
    }

    @Test
    void clientModelManagerHasNoRetiredServerModelContextOrConfigRegistration() throws IOException {
        String manager = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));
        assertFalse(manager.contains("ServerModelContext"));
        assertFalse(manager.contains("serverModels"));
        assertFalse(manager.contains("isServerModel("));

        String registration = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/YesSteveModel.java"),
                Path.of("fabric/src/main/java/com/micaftic/morpher/YesSteveModel.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/YesSteveModel.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/YesSteveModel.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/YesSteveModel.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/YesSteveModel.java"));
        assertFalse(registration.contains("ModConfig.Type.SERVER"));
        assertFalse(registration.contains("ModConfig.Type.SYNCED"));
    }

    @Test
    void cloudEntityAppearanceRemainsTheClientSyncPath() throws IOException {
        String source = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"));
        assertTrue(source.contains("refreshFakeTargets"));
        assertTrue(source.contains("applySelection"));
        assertTrue(source.contains("CloudEntityPresenceClient"));
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

    private static boolean existsInAnyAncestor(Path relative) {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            if (Files.exists(base.resolve(relative).normalize())) {
                return true;
            }
        }
        return false;
    }
}
