package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudAssetCacheTest {
    @Test
    void verifiesHashAndPublishesAtomicallyNamedFile() throws Exception {
        byte[] bytes = "model-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        CloudAssetRef ref = new CloudAssetRef("model", 3, sha);
        var root = Files.createTempDirectory("spm-cloud-cache");
        var path = CloudAssetCache.writeVerified(root, ref, bytes);
        assertEquals(bytes.length, Files.size(path));
        assertArrayEquals(bytes, Files.readAllBytes(path));
        assertEquals(path, CloudAssetCache.writeVerified(root, ref, bytes));
    }

    @Test
    void rejectsUnexpectedHashBeforeWriting() throws Exception {
        CloudAssetRef ref = new CloudAssetRef("model", 1, "a".repeat(64));
        var root = Files.createTempDirectory("spm-cloud-cache");
        assertThrows(CloudHttpException.class, () -> CloudAssetCache.writeVerified(root, ref, new byte[]{1, 2, 3}));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
}
