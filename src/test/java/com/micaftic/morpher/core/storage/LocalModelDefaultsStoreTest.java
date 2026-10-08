package com.micaftic.morpher.core.storage;

import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalModelDefaultsStoreTest {
    @TempDir Path directory;
    @Test void seedsOnceAndPreservesLaterClientPreferences() throws Exception {
        Path file = directory.resolve("local-defaults.json");
        var old = new LocalModelDefaultsStore.Defaults("legacy/芙宁娜", "dress");
        assertEquals(old, LocalModelDefaultsStore.loadOrSeed(file, old));
        var selected = new LocalModelDefaultsStore.Defaults("local/new", "default");
        LocalModelDefaultsStore.save(file, selected);
        assertEquals(selected, LocalModelDefaultsStore.loadOrSeed(file, old));
    }
    @Test void corruptSettingsRemainRecoverable() throws Exception {
        Path file = directory.resolve("local-defaults.json"); Files.writeString(file, "broken");
        assertThrows(IOException.class, () -> LocalModelDefaultsStore.loadOrSeed(file, LocalModelDefaultsStore.FALLBACK));
        assertEquals("broken", Files.readString(file));
    }
}
