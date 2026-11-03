package com.micaftic.morpher.cloud.client;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Content-addressed byte store for Cloud display payloads.
 *
 * <p>This is the only place the client writes Cloud bytes to disk. The store is
 * deliberately dumb: a file name <em>is</em> the SHA-256 of its content, so a
 * cached object is self-verifying and a revision change is automatically a
 * different file. Nothing here parses a model, and no index file is kept, which
 * keeps the "one asset, one revision, one blob" rule impossible to violate.</p>
 *
 * <p>Two namespaces share one byte budget: {@code objects/} holds original model
 * bytes and {@code thumbs/} holds card images keyed by the hash of their public
 * path. Both are pruned least-recently-used first, and a file's modification
 * time is the only recency metadata, so the cache never holds model content in
 * a second file.</p>
 *
 * <p>Every method is safe to call from any thread; I/O is serialized on the
 * instance so two concurrent downloads cannot overshoot the budget.</p>
 */
public final class CloudDiskCache {

    /** Default budget; the configuration file may raise or lower it. */
    public static final long DEFAULT_MAX_BYTES = 512L * 1024L * 1024L;
    public static final long MIN_MAX_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_MAX_BYTES = 8L * 1024L * 1024L * 1024L;
    /** One object is never larger than the display ceiling; keeps a single write bounded. */
    public static final long MAX_OBJECT_BYTES = 256L * 1024L * 1024L;

    static final String OBJECTS = "objects";
    static final String THUMBS = "thumbs";

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{64}");

    private final Path root;
    private final long maxBytes;
    private long totalBytes;

    public CloudDiskCache(Path root, long maxBytes) {
        if (root == null) throw new IllegalArgumentException("Cache root is required");
        if (maxBytes < 1L) throw new IllegalArgumentException("Cache budget must be positive");
        this.root = root.toAbsolutePath().normalize();
        // The configuration range is enforced by fromConfig; a direct instance keeps the exact budget
        // it was given so tests and diagnostics can drive the eviction path with small payloads.
        this.maxBytes = maxBytes;
        this.totalBytes = rescan();
    }

    /**
     * The runtime cache, or {@code null} when the feature is switched off. A
     * disabled cache must behave exactly like the previous memory-only build.
     */
    public static CloudDiskCache fromConfig(Path cacheRoot) {
        if (cacheRoot == null) return null;
        if (!com.micaftic.morpher.config.GeneralConfig.safeGet(
                com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE, true)) return null;
        long megabytes = DEFAULT_MAX_BYTES / (1024L * 1024L);
        try {
            // The int config type differs between the supported loaders, so ask it for its value
            // instead of naming its class; an unloaded config falls back to the documented default.
            var configured = com.micaftic.morpher.config.GeneralConfig.CLOUD_DISK_CACHE_MEGABYTES;
            if (configured != null) megabytes = configured.get();
        } catch (RuntimeException unavailable) {
            megabytes = DEFAULT_MAX_BYTES / (1024L * 1024L);
        }
        long bound = Math.max(MIN_MAX_BYTES, Math.min(MAX_MAX_BYTES, megabytes * 1024L * 1024L));
        try {
            return new CloudDiskCache(cacheRoot, bound);
        } catch (RuntimeException unusable) {
            return null;
        }
    }

    public Path root() { return root; }

    public long maxBytes() { return maxBytes; }

    public synchronized long residentBytes() { return totalBytes; }

    public synchronized int objectCount() { return count(OBJECTS); }

    /** Verified original model bytes, or {@code null} when absent or unusable. */
    public byte[] readObject(String sha256) {
        return read(OBJECTS, sha256, maxBytes);
    }

    /** Card image bytes keyed by the hash of their public path. */
    public byte[] readThumbnail(String key) {
        return read(THUMBS, key, CloudThumbnailPolicy.MAX_BYTES);
    }

    public void writeObject(String sha256, byte[] bytes) {
        if (bytes == null || bytes.length > MAX_OBJECT_BYTES) return;
        write(OBJECTS, sha256, bytes);
    }

    public void writeThumbnail(String key, byte[] bytes) {
        if (bytes == null || bytes.length > CloudThumbnailPolicy.MAX_BYTES) return;
        write(THUMBS, key, bytes);
    }

    /** Drops one object from both namespaces; used when the server revokes a revision. */
    public synchronized void remove(String key) {
        if (!KEY.matcher(key == null ? "" : key).matches()) return;
        for (String namespace : List.of(OBJECTS, THUMBS)) {
            Path file = pathFor(namespace, key);
            try {
                long size = Files.size(file);
                if (Files.deleteIfExists(file)) totalBytes = Math.max(0L, totalBytes - size);
            } catch (IOException ignored) {
                // A missing or locked entry simply stays out of the accounting.
            }
        }
    }

    /** Empties the store; used when the account or instance changes identity. */
    public synchronized void clear() {
        deleteTree(root.resolve(OBJECTS));
        deleteTree(root.resolve(THUMBS));
        totalBytes = 0L;
    }

    private byte[] read(String namespace, String key, long limit) {
        if (!KEY.matcher(key == null ? "" : key).matches()) return null;
        Path file = pathFor(namespace, key);
        try {
            if (!Files.isRegularFile(file)) return null;
            long size = Files.size(file);
            if (size <= 0L || size > limit) {
                // A truncated or oversized blob is never served; drop it and refetch.
                synchronized (this) {
                    if (Files.deleteIfExists(file)) totalBytes = Math.max(0L, totalBytes - size);
                }
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            touch(file);
            return bytes;
        } catch (IOException unreadable) {
            return null;
        }
    }

    private void write(String namespace, String key, byte[] bytes) {
        if (!KEY.matcher(key == null ? "" : key).matches()) return;
        synchronized (this) {
            Path file = pathFor(namespace, key);
            if (Files.isRegularFile(file)) {
                // A content-addressed file is already complete by definition.
                touch(file);
                return;
            }
            evictToFit(bytes.length);
            Path directory = file.getParent();
            Path staging = null;
            try {
                Files.createDirectories(directory);
                staging = Files.createTempFile(directory, key.substring(0, 12) + "-", ".tmp");
                Files.write(staging, bytes);
                move(staging, file);
                staging = null;
                totalBytes += bytes.length;
                touch(file);
            } catch (IOException unwritable) {
                // A read-only or full disk must degrade to network-only, never fail a display.
            } finally {
                if (staging != null) {
                    try { Files.deleteIfExists(staging); } catch (IOException ignored) { }
                }
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException fallback) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
            // Recency is an optimisation; a lost timestamp only changes eviction order.
        }
    }

    private Path pathFor(String namespace, String key) {
        return root.resolve(namespace).resolve(key.substring(0, 2)).resolve(key + ".bin");
    }

    /** Deletes least-recently-modified blobs until the incoming write fits. */
    private void evictToFit(long incoming) {
        if (totalBytes + incoming <= maxBytes) return;
        List<Candidate> candidates = new ArrayList<>();
        for (String namespace : List.of(OBJECTS, THUMBS)) {
            try (Stream<Path> paths = Files.walk(root.resolve(namespace))) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    try {
                        candidates.add(new Candidate(path, Files.size(path), Files.getLastModifiedTime(path)));
                    } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }
        candidates.sort((left, right) -> left.modified.compareTo(right.modified));
        for (Candidate candidate : candidates) {
            if (totalBytes + incoming <= maxBytes) return;
            try {
                if (Files.deleteIfExists(candidate.path)) totalBytes = Math.max(0L, totalBytes - candidate.size);
            } catch (IOException ignored) { }
        }
    }

    private long rescan() {
        long total = 0L;
        for (String namespace : List.of(OBJECTS, THUMBS)) {
            try (Stream<Path> paths = Files.walk(root.resolve(namespace))) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    try { total += Files.size(path); } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }
        return total;
    }

    private int count(String namespace) {
        try (Stream<Path> paths = Files.walk(root.resolve(namespace))) {
            return (int) paths.filter(path -> path.getFileName().toString().endsWith(".bin")).count();
        } catch (IOException empty) {
            return 0;
        }
    }

    private static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    /** Stable key for a public thumbnail path, so a lookup precedes the download. */
    public static String thumbnailKey(String path) {
        return CloudAssetCache.sha256(String.valueOf(path).toLowerCase(Locale.ROOT)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final class Candidate {
        final Path path;
        final long size;
        final FileTime modified;

        Candidate(Path path, long size, FileTime modified) {
            this.path = path;
            this.size = size;
            this.modified = modified;
        }
    }
}
