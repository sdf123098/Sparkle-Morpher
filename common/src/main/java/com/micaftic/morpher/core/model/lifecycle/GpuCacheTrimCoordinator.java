package com.micaftic.morpher.core.model.lifecycle;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Schedules GPU trimming on the render thread and rechecks the target before release. */
public final class GpuCacheTrimCoordinator<T> {
    private final GpuCacheTrimTracker<T> tracker = new GpuCacheTrimTracker<>();
    private final ConcurrentHashMap<String, AtomicLong> requestGenerations = new ConcurrentHashMap<>();

    public void request(String modelId, T assembly, BooleanSupplier isOnRenderThread,
                        Executor renderExecutor, Predicate<T> isStillApplicable, Consumer<T> release) {
        if (modelId == null || assembly == null) return;
        Objects.requireNonNull(isOnRenderThread, "isOnRenderThread");
        Objects.requireNonNull(renderExecutor, "renderExecutor");
        Objects.requireNonNull(isStillApplicable, "isStillApplicable");
        Objects.requireNonNull(release, "release");
        AtomicLong generation = requestGenerations.computeIfAbsent(modelId, ignored -> new AtomicLong());
        request(modelId, assembly, isOnRenderThread, renderExecutor, isStillApplicable, release,
                generation, generation.get());
    }

    private void request(String modelId, T assembly, BooleanSupplier isOnRenderThread,
                         Executor renderExecutor, Predicate<T> isStillApplicable, Consumer<T> release,
                         AtomicLong generation, long expectedGeneration) {
        if (!isCurrent(modelId, generation, expectedGeneration)) return;
        if (!isOnRenderThread.getAsBoolean()) {
            renderExecutor.execute(() -> request(modelId, assembly, isOnRenderThread,
                    renderExecutor, isStillApplicable, release, generation, expectedGeneration));
            return;
        }
        if (!isCurrent(modelId, generation, expectedGeneration)) return;
        if (!isStillApplicable.test(assembly) || !tracker.begin(modelId, assembly)) return;
        boolean completed = false;
        try {
            if (!isCurrent(modelId, generation, expectedGeneration) || !isStillApplicable.test(assembly)) return;
            release.accept(assembly);
            completed = tracker.complete(modelId, assembly);
        } finally {
            if (!completed) tracker.cancel(modelId, assembly);
        }
    }

    public boolean isTrimmed(String modelId, T assembly) {
        return tracker.isTrimmed(modelId, assembly);
    }

    public boolean isTrimmed(String modelId) {
        return tracker.isTrimmed(modelId);
    }

    public void clear(String modelId) {
        if (modelId == null) return;
        AtomicLong generation = requestGenerations.remove(modelId);
        if (generation != null) generation.incrementAndGet();
        tracker.clear(modelId);
    }

    /** Cancels queued trims and clears their per-model completion state during client shutdown. */
    public void clearAll() {
        requestGenerations.values().forEach(AtomicLong::incrementAndGet);
        requestGenerations.clear();
        tracker.clearAll();
    }

    private boolean isCurrent(String modelId, AtomicLong generation, long expectedGeneration) {
        return requestGenerations.get(modelId) == generation && generation.get() == expectedGeneration;
    }
}
