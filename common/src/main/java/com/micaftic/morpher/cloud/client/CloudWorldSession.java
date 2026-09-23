package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Tracks the current client connection without treating server/world names as identity. */
public final class CloudWorldSession {
    private Object connectionToken;
    private long generation;

    public synchronized long enter(Object nextConnectionToken) {
        Objects.requireNonNull(nextConnectionToken, "connectionToken");
        if (connectionToken != nextConnectionToken) {
            connectionToken = nextConnectionToken;
            generation++;
        }
        return generation;
    }

    /** Returns false for a delayed disconnect callback from an older connection. */
    public synchronized boolean leave(Object leavingConnectionToken) {
        if (connectionToken == null || connectionToken != leavingConnectionToken) return false;
        connectionToken = null;
        generation++;
        return true;
    }

    public synchronized boolean leaveCurrent() {
        if (connectionToken == null) return false;
        connectionToken = null;
        generation++;
        return true;
    }

    public synchronized long currentGeneration() {
        if (connectionToken == null) throw new IllegalStateException("No Minecraft world connection is active");
        return generation;
    }

    public synchronized boolean isActive() {
        return connectionToken != null;
    }

    public synchronized boolean isCurrent(long expectedGeneration) {
        return connectionToken != null && generation == expectedGeneration;
    }

    public <T> CompletableFuture<T> guard(
            long expectedGeneration,
            CompletableFuture<T> response,
            Consumer<T> applyIfCurrent
    ) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(applyIfCurrent, "applyIfCurrent");
        return response.thenApply(value -> {
            if (!isCurrent(expectedGeneration)) {
                throw new CancellationException("Minecraft world changed before the Cloud response completed");
            }
            applyIfCurrent.accept(value);
            return value;
        });
    }
}
