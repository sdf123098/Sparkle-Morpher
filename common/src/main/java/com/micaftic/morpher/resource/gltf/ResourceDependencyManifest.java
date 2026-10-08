package com.micaftic.morpher.resource.gltf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable record of external resources consumed while parsing one glTF source. */
public final class ResourceDependencyManifest {
    public enum Role { BUFFER, IMAGE }

    public record Entry(String relativePath, Set<Role> roles, long sizeBytes,
                        long lastModifiedMillis, String sha256) {
        public Entry {
            if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("relativePath is required");
            roles = Set.copyOf(roles);
            if (roles.isEmpty()) throw new IllegalArgumentException("at least one dependency role is required");
            if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must not be negative");
            if (sha256 == null || sha256.length() != 64) throw new IllegalArgumentException("sha256 must be a hex SHA-256 digest");
            try {
                HexFormat.of().parseHex(sha256);
            } catch (IllegalArgumentException malformedDigest) {
                throw new IllegalArgumentException("sha256 must be a hex SHA-256 digest", malformedDigest);
            }
        }
    }

    private final List<Entry> entries;

    private ResourceDependencyManifest(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Content identity for the exact source bytes and external bytes parsed in the same load. */
    public long contentFingerprint(byte[] sourceBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sourceBytes);
            for (Entry entry : entries) {
                digest.update(entry.relativePath().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.sha256().getBytes(StandardCharsets.US_ASCII));
            }
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static Builder builder(Path root) {
        return new Builder(root);
    }

    static final class Builder {
        private final Path root;
        private final Map<String, MutableEntry> entries = new LinkedHashMap<>();

        private Builder(Path root) {
            this.root = root == null ? null : root.toAbsolutePath().normalize();
        }

        void add(Path resolved, Role role, byte[] bytes, long lastModifiedMillis) throws IOException {
            if (root == null) throw new IOException("External glTF dependency has no authorized root: " + resolved);
            Path normalized = resolved.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) throw new IOException("glTF dependency escapes its source root: " + resolved);
            String relative = root.relativize(normalized).toString().replace('\\', '/');
            String digest = sha256(bytes);
            MutableEntry existing = entries.get(relative);
            if (existing == null) {
                entries.put(relative, new MutableEntry(relative, role, bytes.length, lastModifiedMillis, digest));
            } else {
                if (!existing.sha256.equals(digest) || existing.sizeBytes != bytes.length) {
                    throw new IOException("glTF dependency changed while parsing: " + relative);
                }
                existing.roles.add(role);
                existing.lastModifiedMillis = Math.max(existing.lastModifiedMillis, lastModifiedMillis);
            }
        }

        ResourceDependencyManifest build() {
            List<MutableEntry> ordered = new ArrayList<>(entries.values());
            ordered.sort(Comparator.comparing(entry -> entry.relativePath));
            List<Entry> result = new ArrayList<>(ordered.size());
            for (MutableEntry entry : ordered) {
                result.add(new Entry(entry.relativePath, entry.roles, entry.sizeBytes,
                        entry.lastModifiedMillis, entry.sha256));
            }
            return new ResourceDependencyManifest(result);
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }
    }

    private static final class MutableEntry {
        private final String relativePath;
        private final EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        private final long sizeBytes;
        private long lastModifiedMillis;
        private final String sha256;

        private MutableEntry(String relativePath, Role role, long sizeBytes, long lastModifiedMillis, String sha256) {
            this.relativePath = relativePath;
            this.roles.add(role);
            this.sizeBytes = sizeBytes;
            this.lastModifiedMillis = lastModifiedMillis;
            this.sha256 = sha256;
        }
    }
}
