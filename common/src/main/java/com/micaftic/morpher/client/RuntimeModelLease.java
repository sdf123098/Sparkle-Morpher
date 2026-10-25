package com.micaftic.morpher.client;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** One consumer share of a resident runtime model. Closing more than once has no effect. */
public final class RuntimeModelLease implements AutoCloseable {
    private final String runtimeModelId;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimeModelLease(String runtimeModelId, Runnable release) {
        this.runtimeModelId = Objects.requireNonNull(runtimeModelId);
        this.release = Objects.requireNonNull(release);
    }

    public String runtimeModelId() { return runtimeModelId; }
    public boolean isValid() { return !closed.get(); }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }
}
