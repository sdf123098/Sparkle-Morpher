package com.micaftic.morpher.core.model.lifecycle;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks one in-flight request per key; only the request that owns a lease may settle it. */
public final class KeyedRequestLeaseRegistry<K> {
    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentHashMap<K, Lease<K>> inFlight = new ConcurrentHashMap<>();

    /** Returns {@code null} when another live request already owns {@code key}. */
    public Lease<K> begin(K key) {
        Objects.requireNonNull(key, "key");
        Lease<K> lease = new Lease<>(key, nextId.incrementAndGet());
        return inFlight.putIfAbsent(key, lease) == null ? lease : null;
    }

    public boolean isCurrent(Lease<K> lease) {
        return lease != null && inFlight.get(lease.key) == lease;
    }

    /** Removes the key only when {@code lease} is still its current owner. */
    public boolean complete(Lease<K> lease) {
        return isCurrent(lease) && inFlight.remove(lease.key, lease);
    }

    /** Invalidates the current owner so a later request can acquire the key. */
    public boolean invalidate(K key) {
        return inFlight.remove(Objects.requireNonNull(key, "key")) != null;
    }

    public boolean isInFlight(K key) {
        return inFlight.containsKey(Objects.requireNonNull(key, "key"));
    }

    /** Invalidates all request owners, for example when the client runtime is stopping. */
    public void clearAll() {
        inFlight.clear();
    }

    public static final class Lease<K> {
        private final K key;
        private final long id;

        private Lease(K key, long id) {
            this.key = key;
            this.id = id;
        }

        public K key() {
            return key;
        }

        public long id() {
            return id;
        }
    }
}
