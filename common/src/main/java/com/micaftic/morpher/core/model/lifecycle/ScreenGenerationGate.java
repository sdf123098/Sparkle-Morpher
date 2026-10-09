package com.micaftic.morpher.core.model.lifecycle;

/** Prevents asynchronous UI completions from notifying a screen after its lifecycle generation ends. */
public final class ScreenGenerationGate {
    private long generation;
    private boolean importInProgress;

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

    /** Starts one screen-owned import if another is not active. */
    public synchronized boolean beginImport() {
        if (this.importInProgress) return false;
        this.importInProgress = true;
        return true;
    }

    /** Settles the screen's busy state on every result, but only notifies the matching screen generation. */
    public synchronized boolean completeImport(long token, Runnable notification) {
        this.importInProgress = false;
        if (token != this.generation) return false;
        notification.run();
        return true;
    }

    public synchronized boolean importInProgress() {
        return this.importInProgress;
    }
}
