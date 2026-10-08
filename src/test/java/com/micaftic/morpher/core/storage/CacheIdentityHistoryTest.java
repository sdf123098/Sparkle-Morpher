package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CacheIdentityHistoryTest {
    @TempDir Path cache;
    private static String identity(String version) {
        return "sparkle_morpher:model_cache\nmodVersion=" + version;
    }
    @Test void upgradePreservesEncryptedFilesAndOldIdentities() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        Files.createDirectories(cache.resolve("client"));
        Path file = cache.resolve("client/old-encrypted-model");
        Files.write(file, bytes);
        Files.writeString(cache.resolve("version.txt"), identity("2.0.0"));
        assertEquals(java.util.List.of(identity("2.0.0"), identity("2.1.0")),
                CacheIdentityHistory.retain(cache, identity("2.1.0")));
        CacheIdentityHistory.retain(cache, identity("2.1.0"));
        assertEquals(2, CacheIdentityHistory.read(cache).size());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertEquals(identity("2.1.0"), Files.readString(cache.resolve("version.txt")));
    }
    @Test void corruptHistoryDoesNotReplaceTheLegacyMarker() throws Exception {
        Files.writeString(cache.resolve("version.txt"), identity("2.0.0"));
        Files.writeString(cache.resolve("identities.txt"), "bad/version");
        assertThrows(java.io.IOException.class, () -> CacheIdentityHistory.retain(cache, identity("2.1.0")));
        assertEquals(identity("2.0.0"), Files.readString(cache.resolve("version.txt")));
    }
}
