package com.micaftic.morpher.core.model.lifecycle;

/** Prevents asynchronous UI completions from notifying a screen after its lifecycle generation ends. */
public final class ScreenGenerationGate {
    private long generation;

    public synchronized long capture() {
        return generation;
    }

    public synchronized long invalidate() {
        return ++generation;
    }

    public synchronized boolean isCurrent(long token) {
        return token == generation;
    }

    public synchronized boolean completeIfCurrent(long token, Runnable completion) {
        if (token != generation) return false;
        completion.run();
        return true;
    }
}
