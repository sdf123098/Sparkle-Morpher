package com.micaftic.morpher.cloud.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable-in-public-view Cloud asset directory for the current instance.
 * Content bytes remain in {@link CloudAssetCache} and are never embedded in
 * the catalog state.
 */
public final class CloudAssetCatalogStore {
    private final AtomicLong generation = new AtomicLong();
    private volatile Map<String, CloudAssetSummary> byId = Map.of();

    public synchronized long replace(List<CloudAssetSummary> entries) {
        Objects.requireNonNull(entries, "entries");
        Map<String, CloudAssetSummary> next = new HashMap<>();
        for (CloudAssetSummary entry : entries) {
            Objects.requireNonNull(entry, "asset entry");
            Objects.requireNonNull(entry.ref(), "asset ref");
            if (entry.byteLength() < 0) throw new IllegalArgumentException("asset byteLength must not be negative");
            CloudAssetSummary previous = next.put(entry.ref().assetId(), entry);
            if (previous != null && !previous.ref().equals(entry.ref())) {
                throw new IllegalArgumentException("Cloud asset catalog contains duplicate asset ids");
            }
        }
        byId = Map.copyOf(next);
        return generation.incrementAndGet();
    }

    public synchronized long upsert(CloudAssetSummary entry) {
        Objects.requireNonNull(entry, "asset entry");
        Objects.requireNonNull(entry.ref(), "asset ref");
        if (entry.byteLength() < 0) throw new IllegalArgumentException("asset byteLength must not be negative");
        Map<String, CloudAssetSummary> next = new HashMap<>(byId);
        CloudAssetSummary previous = next.put(entry.ref().assetId(), entry);
        if (previous != null && !previous.ref().equals(entry.ref()) && entry.ref().revision() < previous.ref().revision()) {
            next.put(entry.ref().assetId(), previous);
        }
        byId = Map.copyOf(next);
        return generation.incrementAndGet();
    }

    public CloudAssetSummary get(String assetId) {
        return byId.get(assetId);
    }

    public Map<String, CloudAssetSummary> snapshot() {
        return byId;
    }

    public long generation() {
        return generation.get();
    }

    public synchronized void clear() {
        byId = Map.of();
        generation.incrementAndGet();
    }
}
