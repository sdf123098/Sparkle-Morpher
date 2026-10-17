package com.micaftic.morpher.cloud.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Nonblocking admission for asynchronous Cloud work. Zero adds no concurrency cap. */
final class CloudConcurrencyQueue {
    private final ArrayDeque<Job<?>> waiting = new ArrayDeque<>();
    private int limit;
    private int active;

    void limit(int value) {
        if (value < 0 || value > 64) throw new IllegalArgumentException("Cloud concurrency must be 0..64");
        synchronized (this) { limit = value; }
        drain();
    }

    <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> operation) {
        Job<T> job = new Job<>(Objects.requireNonNull(operation));
        synchronized (this) {
            if (waiting.size() >= 256)
                return CompletableFuture.failedFuture(new RejectedExecutionException("Cloud work queue is full"));
            waiting.addLast(job);
        }
        job.result.whenComplete((value, error) -> {
            if (job.result.isCancelled()) {
                synchronized (this) { waiting.remove(job); }
                drain();
            }
        });
        drain();
        return job.result;
    }

    private void drain() {
        List<Job<?>> ready = new ArrayList<>();
        synchronized (this) {
            while (!waiting.isEmpty() && (limit == 0 || active < limit)) {
                Job<?> job = waiting.removeFirst();
                if (!job.result.isCancelled()) { active++; ready.add(job); }
            }
        }
        // Suppliers and completion handlers must never run under the admission lock.
        ready.forEach(this::start);
    }

    private <T> void start(Job<T> job) {
        CompletableFuture<T> work;
        try {
            work = job.result.isCancelled() ? CompletableFuture.failedFuture(new java.util.concurrent.CancellationException())
                    : Objects.requireNonNull(job.operation.get(), "Cloud operation returned null");
        } catch (Throwable error) {
            work = CompletableFuture.failedFuture(error);
        }
        work.whenComplete((value, error) -> {
            synchronized (this) { active--; }
            // Cancelling the public future does not release a still-running operation's slot.
            drain();
            if (error == null) job.result.complete(value); else job.result.completeExceptionally(error);
        });
    }

    private static final class Job<T> {
        final Supplier<CompletableFuture<T>> operation;
        final CompletableFuture<T> result = new CompletableFuture<>();
        Job(Supplier<CompletableFuture<T>> operation) { this.operation = operation; }
    }
}
