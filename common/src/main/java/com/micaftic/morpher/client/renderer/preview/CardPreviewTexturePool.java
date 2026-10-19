package com.micaftic.morpher.client.renderer.preview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded native texture ownership. A submitted texture remains alive until the next frame. */
public final class CardPreviewTexturePool<T extends AutoCloseable> {
    private final int maxEntries;
    private final long maxPixels;
    private final Map<Object, Entry<T>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long frame;
    private long pixels;

    public CardPreviewTexturePool(int maxEntries, long maxPixels) {
        if (maxEntries <= 0 || maxPixels <= 0) throw new IllegalArgumentException("Positive cache limits required");
        this.maxEntries = maxEntries;
        this.maxPixels = maxPixels;
    }

    public void beginFrame() { frame++; }

    public T get(Object key) {
        Entry<T> entry = entries.get(key);
        if (entry == null) return null;
        entry.usedFrame = frame;
        return entry.value;
    }

    public T acquire(Object key, long requestedPixels, Supplier<T> factory) {
        T cached = get(key);
        if (cached != null) return cached;
        if (requestedPixels <= 0 || requestedPixels > maxPixels) return null;
        while (entries.size() >= maxEntries || pixels > maxPixels - requestedPixels) {
            var iterator = entries.entrySet().iterator();
            Entry<T> victim = null;
            while (iterator.hasNext()) {
                Entry<T> candidate = iterator.next().getValue();
                if (candidate.usedFrame != frame) {
                    victim = candidate;
                    iterator.remove();
                    break;
                }
            }
            if (victim == null) return null;
            pixels -= victim.pixels;
            close(victim.value);
        }
        T value = factory.get();
        entries.put(key, new Entry<>(value, requestedPixels, frame));
        pixels += requestedPixels;
        return value;
    }

    public void clear() {
        for (Entry<T> entry : entries.values()) close(entry.value);
        entries.clear();
        pixels = 0;
    }

    private static void close(AutoCloseable value) {
        try { value.close(); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Unable to release preview texture", failure); }
    }

    private static final class Entry<T> {
        final T value;
        final long pixels;
        long usedFrame;
        Entry(T value, long pixels, long usedFrame) {
            this.value = value;
            this.pixels = pixels;
            this.usedFrame = usedFrame;
        }
    }
}
