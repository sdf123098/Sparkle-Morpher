package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** One attempt's cancellation scope, also usable directly as an HTTP progress listener. */
public final class DownloadCancellation implements ModelRepoClient.ProgressListener {
    private final Set<Runnable> callbacks = new LinkedHashSet<>();
    private volatile boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void onProgress(int downloaded, int total) { checkCancelled(); }

    public void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException("cancelled");
    }

    /** Registers a nonblocking abort callback and returns its unregister action. */
    @Override public Runnable onCancel(Runnable action) {
        Objects.requireNonNull(action);
        synchronized (callbacks) {
            if (!cancelled) {
                callbacks.add(action);
                return () -> { synchronized (callbacks) { callbacks.remove(action); } };
            }
        }
        action.run();
        return () -> {};
    }

    public void cancel() {
        ArrayList<Runnable> pending;
        synchronized (callbacks) {
            if (cancelled) return;
            cancelled = true;
            pending = new ArrayList<>(callbacks);
            callbacks.clear();
        }
        // One failed abort must not prevent the other resources from being cancelled.
        for (Runnable callback : pending) {
            try { callback.run(); } catch (RuntimeException ignored) {}
        }
    }
}
