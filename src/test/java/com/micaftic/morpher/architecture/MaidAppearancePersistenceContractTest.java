package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidAppearancePersistenceContractTest {
    @Test
    void baseModelCallbackDoesNotClearPersistedStateBeforeMaidIsLoadedIntoLevel() throws IOException {
        String source = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"),
                Path.of("../../common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"));
        assertTrue(source.contains("maid.level().getEntity(maid.getId()) != maid"));
    }

    @Test
    void loadedMaidStateIsResentToTrackingClients() throws IOException {
        String sync = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"),
                Path.of("../../common/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidModelSync.java"));
        String capabilityEvent = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../../src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"));
        assertTrue(sync.contains("onEntityLoaded"));
        assertTrue(sync.contains("sendToTrackingEntity"));
        assertTrue(capabilityEvent.contains("MaidModelSync.onEntityLoaded("));
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return Files.readString(candidate, StandardCharsets.UTF_8);
        }
        throw new IOException("Required source file not found");
    }
}
