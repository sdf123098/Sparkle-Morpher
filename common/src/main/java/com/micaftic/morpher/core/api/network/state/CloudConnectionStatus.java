package com.micaftic.morpher.core.api.network.state;

/**
 * Observable lifecycle of the selected Cloud instance.
 *
 * <p>The status is deliberately independent from the legacy Minecraft
 * transport. A disconnected Cloud instance never authorizes an automatic
 * fallback to that transport.</p>
 */
public enum CloudConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    DEGRADED,
    REVOKED;

    public boolean isAvailable() {
        return this == READY || this == DEGRADED;
    }

    public boolean allowsMutations() {
        return this == READY;
    }
}
