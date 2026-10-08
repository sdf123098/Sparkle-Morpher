package com.micaftic.morpher.core.model.lifecycle;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic revision gate for asynchronous local-model directory scans. */
public final class ModelScanRevision {
    private final AtomicLong current = new AtomicLong();

    public long begin() {
        return current.incrementAndGet();
    }

    public long invalidate() {
        return current.incrementAndGet();
    }

    public boolean isCurrent(long revision) {
        return current.get() == revision;
    }
}
