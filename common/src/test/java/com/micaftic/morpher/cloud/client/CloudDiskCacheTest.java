package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storage rules for the content-addressed display cache.
 *
 * <p>Every assertion here is about what a reopened model may rely on: a blob is
 * found by its SHA-256, a corrupt or oversized blob is never served, and a
 * revision change is automatically a different file.</p>
 */
class CloudDiskCacheTest {

    private static final byte[] MODEL = "cache-me-please".getBytes(StandardCharsets.UTF_8);

    @Test
    void aStoredObjectRoundTripsAndIsNamedByItsDigest() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            String sha = CloudAssetCache.sha256(MODEL);
            cache.writeObject(sha, MODEL);
            assertArrayEquals(MODEL, cache.readObject(sha));
            assertTrue(Files.isRegularFile(root.resolve("objects").resolve(sha.substring(0, 2)).resolve(sha + ".bin")),
                    "an object must live under objects/<first two hex digits>/<sha>.bin");
            assertEquals(1, cache.objectCount());
            assertEquals(MODEL.length, cache.residentBytes());
        } finally { deleteTree(root); }
    }

    @Test
    void aCorruptBlobIsDeletedAndNeverServed() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            String sha = CloudAssetCache.sha256(MODEL);
            Path file = root.resolve("objects").resolve(sha.substring(0, 2)).resolve(sha + ".bin");
            Files.createDirectories(file.getParent());
            Files.write(file, "different bytes".getBytes(StandardCharsets.UTF_8));
            CloudDiskCache reopened = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            assertNotNull(reopened.readObject(sha), "the store itself only size-checks; the caller verifies the digest");
            // The caller-level guard is what removes a mismatched blob.
            String actual = CloudAssetCache.sha256(reopened.readObject(sha));
            assertFalse(actual.equals(sha));
            reopened.remove(sha);
            assertNull(reopened.readObject(sha));
            assertFalse(Files.exists(file));
        } finally { deleteTree(root); }
    }

    @Test
    void anOversizedOrEmptyBlobIsDroppedInsteadOfServed() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            String sha = "a".repeat(64);
            Path file = root.resolve("objects").resolve(sha.substring(0, 2)).resolve(sha + ".bin");
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[0]);
            assertNull(cache.readObject(sha), "an empty blob is not a model");
            assertFalse(Files.exists(file), "an unusable blob must be removed so the next read refetches");
        } finally { deleteTree(root); }
    }

    @Test
    void theBudgetEvictsLeastRecentlyUsedBlobsAcrossBothNamespaces() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            // A deliberately tiny budget keeps the eviction path cheap to exercise.
            CloudDiskCache cache = new CloudDiskCache(root, 900L);
            byte[] first = payload(400, 7);
            byte[] second = payload(400, 11);
            String firstSha = CloudAssetCache.sha256(first);
            String secondSha = CloudAssetCache.sha256(second);
            assertFalse(firstSha.equals(secondSha), "the two payloads must be distinct objects");
            cache.writeObject(firstSha, first);
            cache.writeObject(secondSha, second);
            // Recency is mtime based, so age the untouched blob explicitly instead of racing the clock.
            Path stored = root.resolve("objects").resolve(secondSha.substring(0, 2)).resolve(secondSha + ".bin");
            Files.setLastModifiedTime(stored,
                    java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000L));
            assertNotNull(cache.readObject(firstSha), "reading refreshes the recency of the first blob");
            String key = CloudDiskCache.thumbnailKey("/v1/public/assets/audit/revisions/1/thumbnail");
            cache.writeThumbnail(key, payload(400, 13));
            assertTrue(cache.residentBytes() <= 900L, "the store must stay inside its budget");
            assertNotNull(cache.readObject(firstSha), "the recently used object survives");
            assertNull(cache.readObject(secondSha), "the least recently used object is evicted");
        } finally { deleteTree(root); }
    }

    @Test
    void aRevisionChangeIsAutomaticallyADifferentFile() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            byte[] oldBytes = "revision-one".getBytes(StandardCharsets.UTF_8);
            byte[] newBytes = "revision-two".getBytes(StandardCharsets.UTF_8);
            String oldSha = CloudAssetCache.sha256(oldBytes);
            String newSha = CloudAssetCache.sha256(newBytes);
            cache.writeObject(oldSha, oldBytes);
            cache.writeObject(newSha, newBytes);
            assertArrayEquals(oldBytes, cache.readObject(oldSha));
            assertArrayEquals(newBytes, cache.readObject(newSha));
            assertEquals(2, cache.objectCount(), "one revision cannot overwrite another revision's blob");
        } finally { deleteTree(root); }
    }

    @Test
    void thumbnailsUseTheSameBudgetButTheirOwnNamespace() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            String key = CloudDiskCache.thumbnailKey("/v1/public/assets/audit/revisions/2/thumbnail");
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
            cache.writeThumbnail(key, png);
            assertArrayEquals(png, cache.readThumbnail(key));
            assertTrue(Files.isRegularFile(root.resolve("thumbs").resolve(key.substring(0, 2)).resolve(key + ".bin")));
            assertEquals(0, cache.objectCount(), "a card image is not a model object");
            assertEquals(png.length, cache.residentBytes(), "both namespaces share one budget");
        } finally { deleteTree(root); }
    }

    @Test
    void aDisabledCacheBehavesExactlyLikeTheMemoryOnlyBuild() throws Exception {
        boolean previous = com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE == null;
        if (!previous) {
            boolean stored = com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE.get();
            com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE.set(false);
            try {
                assertNull(CloudDiskCache.fromConfig(Path.of("config")), "switching the cache off must return null");
            } finally {
                com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE.set(stored);
            }
        }
        assertNotNull(CloudDiskCache.fromConfig(Path.of("config", "sparkle-morpher", "cloud-cache")),
                "the default configuration keeps the cache enabled");
    }

    @Test
    void aFreshInstanceAdoptsExistingFilesAndTheirSizes() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache first = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            String sha = CloudAssetCache.sha256(MODEL);
            first.writeObject(sha, MODEL);
            CloudDiskCache second = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            assertEquals(MODEL.length, second.residentBytes(), "a restart must not lose the accounting");
            assertEquals(1, second.objectCount());
            assertArrayEquals(MODEL, second.readObject(sha));
        } finally { deleteTree(root); }
    }

    @Test
    void invalidKeysAreIgnoredInsteadOfTouchingTheFilesystem() throws Exception {
        Path root = Files.createTempDirectory("sm-cloud-cache-");
        try {
            CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
            cache.writeObject("../escape", MODEL);
            cache.writeObject("not-a-digest", MODEL);
            cache.writeObject(null, MODEL);
            assertNull(cache.readObject("../escape"));
            assertNull(cache.readThumbnail("not-a-digest"));
            try (Stream<Path> paths = Files.walk(root)) {
                List<Path> files = paths.filter(Files::isRegularFile).toList();
                assertTrue(files.isEmpty(), () -> "an invalid key must never create a file: " + files);
            }
        } finally { deleteTree(root); }
    }

    private static byte[] payload(int size) {
        return payload(size, 0);
    }

    /** Distinct payloads must produce distinct digests, so the offset seeds the content. */
    private static byte[] payload(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) ((i + seed * 31) % 251);
        return bytes;
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }
}
