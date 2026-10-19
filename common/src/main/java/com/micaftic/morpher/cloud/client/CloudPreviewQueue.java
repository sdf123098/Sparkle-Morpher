package com.micaftic.morpher.cloud.client;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** A bounded viewport queue. Browsing never implies applying, favoriting or importing a local copy. */
public final class CloudPreviewQueue<T> implements AutoCloseable {
    private final int concurrency;
    private final Function<T, CompletableFuture<?>> loader;
    private final Set<T> active = new HashSet<>(), attempted = new HashSet<>();
    private final LinkedHashSet<T> desired = new LinkedHashSet<>();
    private boolean closed, pumping;

    public CloudPreviewQueue(int concurrency, Function<T, CompletableFuture<?>> loader) {
        if (concurrency < 1) throw new IllegalArgumentException("concurrency");
        this.concurrency = concurrency; this.loader = Objects.requireNonNull(loader);
    }
    public synchronized void update(Collection<T> visible) {
        if (closed) return;
        desired.clear(); desired.addAll(visible);
        attempted.retainAll(desired);
        pump();
    }
    public synchronized void retry() {
        attempted.clear(); attempted.addAll(active); pump();
    }
    private void pump() {
        if (closed || pumping) return;
        pumping = true;
        try {
            while (!closed && active.size() < concurrency) {
                T item = desired.stream().filter(x -> !attempted.contains(x) && !active.contains(x)).findFirst().orElse(null);
                if (item == null) break;
                attempted.add(item); active.add(item);
                CompletableFuture<?> future;
                try { future = Objects.requireNonNull(loader.apply(item)); }
                catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
                future.whenComplete((ignored, failure) -> {
                    synchronized (CloudPreviewQueue.this) { active.remove(item); pump(); }
                });
            }
        } finally { pumping = false; }
    }
    @Override public synchronized void close() { closed = true; desired.clear(); attempted.clear(); }
}
