package com.micaftic.morpher.client.core;

/** Lane-owned sampled position history; rates derive from active elapsed time, never entity velocity. */
public final class CoreMotionSnapshot {
    public record Sample(double dx, double dy, double dz, double verticalSpeed) { }
    private final CoreActiveClock clock = new CoreActiveClock();
    private long slot = -1, activeNanos;
    private double x, y, z;
    private Sample published = new Sample(0, 0, 0, 0);
    public Sample sample(long nowNanos, boolean paused, double nextX, double nextY, double nextZ) {
        var time = clock.advance(nowNanos, paused);
        if (paused) {
            x = nextX; y = nextY; z = nextZ;
            activeNanos = time.activeNanos();
            return published = new Sample(0, 0, 0, 0);
        }
        if (slot == time.slot()) return published;
        if (slot >= 0 && time.activeNanos() > activeNanos) {
            double dx = nextX - x, dy = nextY - y, dz = nextZ - z;
            published = new Sample(dx, dy, dz, dy * 1_000_000_000.0 / (time.activeNanos() - activeNanos));
        }
        x = nextX; y = nextY; z = nextZ;
        slot = time.slot(); activeNanos = time.activeNanos();
        return published;
    }
}
