package com.micaftic.morpher.core.model.lifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Retry admission for resident models, independent of request lease ownership. */
public final class ModelReloadBackoff<K> {
    private static final long INITIAL_DELAY_MILLIS = 2_000L;
    private static final long MAX_DELAY_MILLIS = 60_000L;
    private final Map<K, Failure> failures = new HashMap<>();

    private record Failure(Object source, long failedAtMillis, long delayMillis) { }

    /** Source identity prevents an obsolete failure from delaying a replacement model. */
    public synchronized boolean allows(K key, Object source, long nowMillis) {
        Failure failure = failures.get(key);
        return failure == null || failure.source != source
                || nowMillis - failure.failedAtMillis >= failure.delayMillis;
    }

    /** Call only after the owning request settles without a resident publication. */
    public synchronized void failed(K key, Object source, long nowMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Failure previous = failures.get(key);
        long delay = previous != null && previous.source == source
                ? Math.min(MAX_DELAY_MILLIS, previous.delayMillis * 2L) : INITIAL_DELAY_MILLIS;
        failures.put(key, new Failure(source, nowMillis, delay));
    }

    /** Successful publication or explicit user retry starts a fresh attempt sequence. */
    public synchronized void clear(K key) {
        failures.remove(key);
    }

    public synchronized void clearAll() {
        failures.clear();
    }
}
