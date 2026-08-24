package com.micaftic.morpher.audio;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java atomic pending-key registry used to deduplicate concurrent cache builds.
 */
final class PendingTrackClaims<K> {

    private static final Object CLAIMED = new Object();

    private final ConcurrentHashMap<K, Object> claims = new ConcurrentHashMap<>();

    boolean tryClaim(K key) {
        return claims.putIfAbsent(key, CLAIMED) == null;
    }

    void release(K key) {
        claims.remove(key, CLAIMED);
    }

    void clear() {
        claims.clear();
    }
}
