package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Invalidates scope-bound asynchronous results before they can mutate shared state. */
final class CloudRequestGeneration {
    private final AtomicLong generation = new AtomicLong();

    synchronized long current() {
        return generation.get();
    }

    synchronized long advance() {
        return generation.incrementAndGet();
    }

    <T> CompletableFuture<T> guard(long expected, CompletableFuture<T> source, Consumer<T> apply) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(apply, "apply");
        return source.thenApply(value -> {
            synchronized (this) {
                if (generation.get() != expected) {
                    throw new CancellationException("Cloud selection changed before the response completed");
                }
                apply.accept(value);
            }
            return value;
        });
    }
}
