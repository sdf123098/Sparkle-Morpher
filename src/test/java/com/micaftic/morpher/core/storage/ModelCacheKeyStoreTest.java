package com.micaftic.morpher.core.storage;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModelCacheKeyStoreTest {
    @TempDir Path directory;
    @Test void legacyIndexIsReadWithoutChangingItsContents() throws Exception {
        byte[] key = new byte[56]; Arrays.fill(key, (byte) 7);
        Path index = directory.resolve("server.json");
        String json = "{\"server_key\":\"" + Base64.getEncoder().encodeToString(key) + "\",\"old_metadata\":{\"keep\":true}}";
        Files.writeString(index, json);
        assertArrayEquals(key, ModelCacheKeyStore.loadOrCreate(index));
        assertEquals(json, Files.readString(index));
    }
    @Test void malformedOrMissingLegacyKeyNeverOverwritesTheIndex() throws Exception {
        Path index = directory.resolve("server.json");
        for (String json : List.of("broken", "{}", "{\"server_key\":null}", "{\"server_key\":\"abc\"}", "{\"server_key\":\"abc\",\"server_key\":\"def\"}")) {
            Files.writeString(index, json);
            assertThrows(IOException.class, () -> ModelCacheKeyStore.loadOrCreate(index));
            assertEquals(json, Files.readString(index));
        }
    }
    @Test void concurrentInitializationPersistsOneKeyForLaterReads() throws Exception {
        Path index = directory.resolve("server.json");
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> ModelCacheKeyStore.loadOrCreate(index));
            var second = pool.submit(() -> ModelCacheKeyStore.loadOrCreate(index));
            assertArrayEquals(first.get(), second.get());
            assertArrayEquals(first.get(), ModelCacheKeyStore.loadOrCreate(index));
            assertEquals(56, first.get().length);
        }
    }
}
