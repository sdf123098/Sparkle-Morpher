package com.micaftic.morpher.core.architectury.event;

/**
 * Minimal event result compatible with the parts of Architectury used here.
 */
public class EventResult {
    private static final EventResult PASS = new EventResult(false, true);
    private final boolean interrupted;
    private final boolean value;

    private EventResult(boolean interrupted, boolean value) {
        this.interrupted = interrupted;
        this.value = value;
    }

    public static EventResult pass() {
        return PASS;
    }

    public static EventResult interrupt(String reason) {
        return new EventResult(true, true);
    }

    public static EventResult interruptFalse() {
        return new EventResult(true, false);
    }

    public boolean isFalse() {
        return interrupted && !value;
    }

    public boolean isTrue() {
        return interrupted && value;
    }
}
