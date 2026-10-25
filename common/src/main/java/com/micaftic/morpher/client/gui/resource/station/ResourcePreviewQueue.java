package com.micaftic.morpher.client.gui.resource.station;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * One physical preview worker at a time, limited to the current viewport.
 * Loaders must return promptly and complete their future only after their worker has exited.
 * A loader can run on the caller or previous future's completion thread; UI work belongs on its owner thread.
 * Successful images belong to the caller's cache, which should filter cache hits before update().
 */
public final class ResourcePreviewQueue<R> implements AutoCloseable {
    private final Function<R, String> identity;
    private final BiFunction<R, DownloadCancellation, CompletableFuture<Void>> loader;
    private final Map<String, R> desired = new LinkedHashMap<>();
    private final Set<String> attempted = new HashSet<>();
    private Attempt active;
    private boolean closed;
    private boolean pumping;

    private static final class Attempt {
        final String key;
        final DownloadCancellation cancellation = new DownloadCancellation();
        boolean obsolete;
        Attempt(String key) { this.key = key; }
    }

    public ResourcePreviewQueue(Function<R, String> identity,
                                BiFunction<R, DownloadCancellation, CompletableFuture<Void>> loader) {
        this.identity = Objects.requireNonNull(identity);
        this.loader = Objects.requireNonNull(loader);
    }

    public synchronized void update(Collection<R> requests) {
        if (closed) return;
        Map<String, R> next = new LinkedHashMap<>();
        for (R request : requests) next.putIfAbsent(Objects.requireNonNull(identity.apply(request)), request);
        desired.clear();
        desired.putAll(next);
        attempted.retainAll(desired.keySet());
        if (active != null && !desired.containsKey(active.key)) {
            active.obsolete = true;
            active.cancellation.cancel();
        }
        pump();
    }

    public void pause() { update(List.of()); }

    /** Called only while holding this queue's monitor; the guard also handles inline completion. */
    private void pump() {
        if (closed || pumping) return;
        pumping = true;
        try {
            while (!closed && active == null) {
                var next = desired.entrySet().stream().filter(entry -> !attempted.contains(entry.getKey()))
                        .findFirst().orElse(null);
                if (next == null) return;
                Attempt attempt = new Attempt(next.getKey());
                R request = next.getValue();
                attempted.add(attempt.key);
                active = attempt;
                CompletableFuture<Void> future;
                try { future = Objects.requireNonNull(loader.apply(request, attempt.cancellation)); }
                catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
                future.whenComplete((ignored, failure) -> finish(attempt));
            }
        } finally { pumping = false; }
    }

    private synchronized void finish(Attempt attempt) {
        if (active != attempt) return;
        active = null;
        // Leaving and immediately returning may request this identity again with a fresh token.
        // A worker's own budget cancellation remains attempted to prevent an immediate retry loop.
        if (attempt.obsolete) attempted.remove(attempt.key);
        pump();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        desired.clear();
        attempted.clear();
        if (active != null) {
            active.obsolete = true;
            active.cancellation.cancel();
        }
    }
}
