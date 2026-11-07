package com.micaftic.morpher.client.core;

/** Host active time shared by tick/render drivers. Pause entry and resume rebase wall time. */
public final class CoreActiveClock {
    public record Sample(long activeNanos, long slot) { }
    private boolean started;
    private boolean paused;
    private long previous;
    private long active;

    public synchronized Sample advance(long nowNanos, boolean isPaused) {
        if (started && nowNanos < previous) throw new IllegalArgumentException("Host clock cannot regress");
        if (started && !paused && !isPaused) active = Math.addExact(active, Math.subtractExact(nowNanos, previous));
        previous = nowNanos;
        paused = isPaused;
        started = true;
        long slot = Math.addExact(Math.multiplyExact(active / 1_000_000_000L, 60), (active % 1_000_000_000L) * 60 / 1_000_000_000L);
        return new Sample(active, slot);
    }
}
