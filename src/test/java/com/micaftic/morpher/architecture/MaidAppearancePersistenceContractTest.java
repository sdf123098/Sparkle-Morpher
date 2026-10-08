package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidAppearancePersistenceContractTest {
    @Test
    void maidAppearanceUsesTheCloudClientPathAndKeepsClientCompat() throws IOException {
        String sync = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java"));
        String compat = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/compat/touhoulittlemaid/TouhouLittleMaidClientCompat.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/compat/touhoulittlemaid/TouhouLittleMaidClientCompat.java"));

        assertTrue(sync.contains("MaidCapability.get(entity).ifPresent(cap -> cap.applyCloudState"));
        assertTrue(sync.contains("MaidCapability.get(entity).ifPresent(cap -> cap.clearCloudState()"));
        assertTrue(compat.contains("handleMaidInteraction"));
        assertTrue(compat.contains("registerMaidAnimStates"));
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
