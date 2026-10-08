package com.micaftic.morpher.core.model.lifecycle;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Schedules GPU trimming on the render thread and rechecks the target before release. */
public final class GpuCacheTrimCoordinator<T> {
    private final GpuCacheTrimTracker<T> tracker = new GpuCacheTrimTracker<>();

    public void request(String modelId, T assembly, BooleanSupplier isOnRenderThread,
                        Executor renderExecutor, Predicate<T> isStillApplicable, Consumer<T> release) {
        if (modelId == null || assembly == null) return;
        Objects.requireNonNull(isOnRenderThread, "isOnRenderThread");
        Objects.requireNonNull(renderExecutor, "renderExecutor");
        Objects.requireNonNull(isStillApplicable, "isStillApplicable");
        Objects.requireNonNull(release, "release");
        if (!isOnRenderThread.getAsBoolean()) {
            renderExecutor.execute(() -> request(modelId, assembly, isOnRenderThread,
                    renderExecutor, isStillApplicable, release));
            return;
        }
        if (!isStillApplicable.test(assembly) || !tracker.begin(modelId, assembly)) return;
        boolean completed = false;
        try {
            if (!isStillApplicable.test(assembly)) return;
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
        tracker.clear(modelId);
    }
}
