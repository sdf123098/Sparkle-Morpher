package com.micaftic.morpher.client.core;

/**
 * Host animation controller state & play decision enums for SPM controllers.
 */
public final class HostPlayState {
    private HostPlayState() {
    }

    public enum Decision {
        CONTINUE,
        STOP,
        PAUSE,
        BYPASS
    }

    public enum State {
        RUNNING,
        BEGINNING_TRANSITION,
        ENDING_TRANSITION,
        IDLE
    }
}
