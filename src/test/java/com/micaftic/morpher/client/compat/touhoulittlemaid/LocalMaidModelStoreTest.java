package com.micaftic.morpher.client.compat.touhoulittlemaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalMaidModelStoreTest {
    @TempDir Path directory;
    private final UUID maid = UUID.randomUUID();
    private LocalMaidModelStore store() { return new LocalMaidModelStore(directory.resolve("local_maids.json")); }

    @Test void restoresLocalModelAndTextureAfterRestartWithoutCloudState() throws Exception {
        var selection = new LocalMaidModelStore.Selection("custom/alice", "blue");
        store().save("world-a", maid, selection);
        assertEquals(selection, store().load("world-a", maid));
        assertNull(store().load("world-b", maid));
        assertNull(store().load("world-a", UUID.randomUUID()));
    }

    @Test void explicitDefaultSurvivesRestartAndCanYieldToCloudLater() throws Exception {
        var selection = new LocalMaidModelStore.Selection("default", "default");
        store().save("world-a", maid, selection);
        assertEquals(selection, store().load("world-a", maid));
        store().remove("world-a", maid);
        assertNull(store().load("world-a", maid));
    }

    @Test void changingTexturePreservesOtherMaidsAndWorlds() throws Exception {
        UUID other = UUID.randomUUID();
        store().save("world-a", maid, new LocalMaidModelStore.Selection("alice", "red"));
        store().save("world-a", other, new LocalMaidModelStore.Selection("bob", "default"));
        store().save("world-b", maid, new LocalMaidModelStore.Selection("carol", "default"));
        store().save("world-a", maid, new LocalMaidModelStore.Selection("alice", "blue"));
        assertEquals("blue", store().load("world-a", maid).textureId());
        assertEquals("bob", store().load("world-a", other).modelId());
        assertEquals("carol", store().load("world-b", maid).modelId());
    }

    @Test void refusesCloudRuntimeModelsAndPreservesCorruptPreferences() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                new LocalMaidModelStore.Selection("cloud_" + "a".repeat(64), "default"));
        Path file = directory.resolve("local_maids.json");
        Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, () -> store().save("world-a", maid,
                new LocalMaidModelStore.Selection("alice", "default")));
        assertEquals("{broken", Files.readString(file));
    }
}
