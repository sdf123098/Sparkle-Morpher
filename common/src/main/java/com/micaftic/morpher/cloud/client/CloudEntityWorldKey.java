package com.micaftic.morpher.cloud.client;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Stable client-only namespace; no server payload or local path is transmitted. */
public final class CloudEntityWorldKey {
    private CloudEntityWorldKey() {}
    public static String remote(String address) {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("Missing server address");
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565")) normalized = normalized.substring(0, normalized.length() - 6);
        return hash("minecraft-server:" + normalized);
    }
    public static String local(String worldPath) {
        if (worldPath == null || worldPath.isBlank()) throw new IllegalArgumentException("Missing local world");
        return hash("minecraft-local:" + worldPath.replace('\\', '/'));
    }
    private static String hash(String value) { return CloudAssetCache.sha256(value.getBytes(StandardCharsets.UTF_8)); }
}
