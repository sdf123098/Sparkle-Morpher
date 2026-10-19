package com.micaftic.morpher.client.renderer.preview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Weighted LRU; the owner supplies GPU disposal, keeping the lifetime policy testable without GL. */
final class PreviewTexturePool<K, V> {
    private record Entry<V>(V value, long bytes) {}
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxEntries;
    private final long maxBytes;
    private final Consumer<V> dispose;
    private long bytes;

    PreviewTexturePool(int maxEntries, long maxBytes, Consumer<V> dispose) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.dispose = dispose;
    }

    V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value();
    }

    void put(K key, V value, long cost) {
        Entry<V> replaced = entries.remove(key);
        if (replaced != null) release(replaced);
        if (cost < 0 || cost > maxBytes || maxEntries <= 0) {
            dispose.accept(value);
            return;
        }
        while (!entries.isEmpty() && (entries.size() >= maxEntries || bytes + cost > maxBytes)) {
            K oldest = entries.keySet().iterator().next();
            release(entries.remove(oldest));
        }
        entries.put(key, new Entry<>(value, cost));
        bytes += cost;
    }

    void clear() {
        entries.values().forEach(entry -> dispose.accept(entry.value()));
        entries.clear();
        bytes = 0;
    }

    private void release(Entry<V> entry) {
        bytes -= entry.bytes();
        dispose.accept(entry.value());
    }

    int size() { return entries.size(); }
    long bytes() { return bytes; }
}
