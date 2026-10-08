package com.micaftic.morpher.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/** Keeps cache key identities across upgrades without deleting any encrypted cache. */
public final class CacheIdentityHistory {
    private static final String PREFIX = "sparkle_morpher:model_cache\nmodVersion=";
    private static final int MAX_BYTES = 16384;
    private CacheIdentityHistory() {}

    public static List<String> retain(Path cache, String current) throws IOException {
        if (!valid(current)) throw new IOException("Invalid model cache identity");
        Files.createDirectories(cache);
        LinkedHashSet<String> identities = new LinkedHashSet<>(read(cache));
        Path marker = cache.resolve("version.txt");
        if (Files.exists(marker)) {
            String old = readBounded(marker);
            if (valid(old)) identities.add(old);
        }
        identities.add(current);
        if (identities.size() > 64) throw new IOException("Too many cache identities; existing cache preserved");
        // History is committed before the active marker, so an interrupted upgrade remains readable.
        writeAtomic(cache.resolve("identities.txt"), String.join("\n", identities.stream()
                .map(s -> s.substring(PREFIX.length())).toList()));
        writeAtomic(marker, current);
        return List.copyOf(identities);
    }

    public static List<String> read(Path cache) throws IOException {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        Path history = cache.resolve("identities.txt");
        if (Files.exists(history)) {
            for (String version : readBounded(history).split("\n")) {
                if (version.isEmpty()) continue;
                String identity = PREFIX + version;
                if (!valid(identity)) throw new IOException("Invalid cached identity history");
                identities.add(identity);
                if (identities.size() > 64) throw new IOException("Too many cache identities");
            }
        }
        return List.copyOf(identities);
    }

    private static boolean valid(String identity) {
        return identity != null && identity.startsWith(PREFIX)
                && identity.substring(PREFIX.length()).matches("[A-Za-z0-9._+\\-]{1,128}");
    }

    private static String readBounded(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Cache identity file too large");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void writeAtomic(Path target, String text) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".identity-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            AtomicFileMover.moveWithRetry(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }
}
