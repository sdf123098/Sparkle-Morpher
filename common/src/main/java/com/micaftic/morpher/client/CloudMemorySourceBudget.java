package com.micaftic.morpher.client;

import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Byte-bounded LRU for verified Cloud sources; no paths, files, or fallback storage. */
public final class CloudMemorySourceBudget {
    public static final long DEFAULT_TOTAL_BYTES = 256L * 1024 * 1024;
    public static final long MAX_ASSET_BYTES = 128L * 1024 * 1024;
    private final long maximumBytes;
    private final LinkedHashMap<String, LocalModelCatalog.Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long bytes, hits, misses, evictions, parses;

    public CloudMemorySourceBudget(long maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("Invalid memory source budget");
        this.maximumBytes = maximumBytes;
    }

    /** Rejects retention when all older sources are pinned; resident assemblies remain usable. */
    public synchronized boolean retain(String id, LocalModelCatalog.Entry source, Predicate<String> pinned,
                                        BiConsumer<String, LocalModelCatalog.Entry> evicted) {
        byte[] data = source.memoryBytes;
        if (!source.isMemory() || data == null || data.length > MAX_ASSET_BYTES || data.length > maximumBytes) return false;
        LocalModelCatalog.Entry old = entries.remove(id);
        if (old != null && old.memoryBytes != null) bytes -= old.memoryBytes.length;
        var iterator = entries.entrySet().iterator();
        while (bytes + data.length > maximumBytes && iterator.hasNext()) {
            Map.Entry<String, LocalModelCatalog.Entry> victim = iterator.next();
            if (pinned.test(victim.getKey())) continue;
            iterator.remove();
            bytes -= victim.getValue().memoryBytes.length;
            evictions++;
            evicted.accept(victim.getKey(), victim.getValue());
            victim.getValue().discardMemoryBytes();
        }
        if (bytes + data.length > maximumBytes) {
            if (old != null) { entries.put(id, old); bytes += old.memoryBytes.length; }
            return false;
        }
        if (old != null && old != source) old.discardMemoryBytes();
        entries.put(id, source); bytes += data.length;
        return true;
    }

    public synchronized void accessed(String id, boolean found) {
        if (found) { hits++; entries.get(id); } else misses++;
    }
    public synchronized void parsed() { parses++; }
    public synchronized void touch(String id) { entries.get(id); }
    public synchronized void clear() {
        entries.values().forEach(LocalModelCatalog.Entry::discardMemoryBytes);
        entries.clear(); bytes = 0;
    }
    public synchronized void remove(String id) {
        LocalModelCatalog.Entry old = entries.remove(id);
        if (old != null && old.memoryBytes != null) { bytes -= old.memoryBytes.length; old.discardMemoryBytes(); }
    }
    public record Stats(long bytes, long hits, long misses, long evictions, long parses, long downloads) {}
    public synchronized Stats stats() { return new Stats(bytes, hits, misses, evictions, parses, com.micaftic.morpher.cloud.client.CloudAssetClient.displayDownloadCount()); }
}
