package com.micaftic.morpher.client.animation.molang;

/** Bounded 60 Hz clock. Drawing never determines how many program updates occur. */
public final class SimulationClock {
    public static final double STEP = 1.0 / 60.0;
    private static final int MAX_STEPS = 8;
    private final double period;
    public SimulationClock() { this(STEP); }
    public SimulationClock(double period) {
        if (!Double.isFinite(period) || period <= 0) throw new IllegalArgumentException("period");
        this.period = period;
    }
    public double period() { return period; }
    @FunctionalInterface public interface StepConsumer {
        void accept(long stepId, double time, double dt, boolean recovery);
    }
    private boolean initialized;
    private double origin, observed;
    private long localStep, stepId;
    private boolean recoveryRequested;
    public void recoverOnNextAdvance() { recoveryRequested = true; }

    /** Render passes may arrive late. Only explicit lifecycle recovery may rewind this instance. */
    public void advanceFrame(double time, StepConsumer consumer) {
        if (initialized && !recoveryRequested && time < observed) return;
        advance(time, consumer);
    }

    public void advance(double time, StepConsumer consumer) {
        if (!Double.isFinite(time)) return;
        if (!initialized || recoveryRequested || time < observed) { recoveryRequested=false; recover(time, consumer); return; }
        long target = (long)Math.floor((time - origin) / period + 1e-7);
        if (target - localStep > MAX_STEPS) { recover(time, consumer); return; }
        observed = time;
        while (localStep < target) {
            localStep++;
            consumer.accept(++stepId, origin + localStep * period, period, false);
        }
    }

    private void recover(double time, StepConsumer consumer) {
        if (initialized) stepId++;
        initialized = true; origin = observed = time; localStep = 0;
        consumer.accept(stepId, time, 0, true);
    }

    public float interpolation() {
        return (float)Math.max(0, Math.min(1, (observed - origin) / period - localStep));
    }
}
