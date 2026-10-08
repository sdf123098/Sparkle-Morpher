package com.micaftic.morpher.core.model.lifecycle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks one GPU-cache trim request per model and exact runtime assembly. */
public final class GpuCacheTrimTracker<T> {
    private final ConcurrentHashMap<String, State<T>> states = new ConcurrentHashMap<>();

    public boolean begin(String modelId, T assembly) {
        if (modelId == null || assembly == null) return false;
        return states.putIfAbsent(modelId, new State<>(assembly, Phase.PENDING)) == null;
    }

    public boolean complete(String modelId, T assembly) {
        AtomicBoolean completed = new AtomicBoolean();
        states.computeIfPresent(modelId, (key, state) -> {
            if (state.assembly() != assembly || state.phase() != Phase.PENDING) return state;
            completed.set(true);
            return new State<>(assembly, Phase.TRIMMED);
        });
        return completed.get();
    }

    public boolean cancel(String modelId, T assembly) {
        AtomicBoolean canceled = new AtomicBoolean();
        states.computeIfPresent(modelId, (key, state) -> {
            if (state.assembly() != assembly || state.phase() != Phase.PENDING) return state;
            canceled.set(true);
            return null;
        });
        return canceled.get();
    }

    public boolean isTrimmed(String modelId, T assembly) {
        State<T> state = states.get(modelId);
        return state != null && state.assembly() == assembly && state.phase() == Phase.TRIMMED;
    }

    public boolean isTrimmed(String modelId) {
        State<T> state = states.get(modelId);
        return state != null && state.phase() == Phase.TRIMMED;
    }

    public void clear(String modelId) {
        if (modelId != null) states.remove(modelId);
    }

    private enum Phase { PENDING, TRIMMED }

    private record State<T>(T assembly, Phase phase) { }
}
