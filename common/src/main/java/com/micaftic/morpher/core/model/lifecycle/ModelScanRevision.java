package com.micaftic.morpher.core.model.lifecycle;

/** Monotonic revision gate for asynchronous local-model directory scans. */
public final class ModelScanRevision {
    private long current;

    public long begin() {
        synchronized (this) {
            return ++current;
        }
    }

    public long invalidate() {
        synchronized (this) {
            return ++current;
        }
    }

    public boolean isCurrent(long revision) {
        synchronized (this) {
            return current == revision;
        }
    }

    /**
     * Applies a scan candidate atomically with respect to beginning or invalidating scans.
     * A stale worker can never pass a check and then overwrite a newer scan's catalog.
     */
    public boolean runIfCurrent(long revision, Runnable apply) {
        synchronized (this) {
            if (current != revision) return false;
            apply.run();
            return true;
        }
    }
}
