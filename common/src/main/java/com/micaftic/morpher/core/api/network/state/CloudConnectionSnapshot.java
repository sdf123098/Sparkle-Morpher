package com.micaftic.morpher.core.api.network.state;

import java.util.Objects;

/** Immutable, generation-stamped Cloud connection observation. */
public record CloudConnectionSnapshot(
        CloudConnectionStatus status,
        CloudErrorCode error,
        String instanceId,
        long generation
) {

    public CloudConnectionSnapshot {
        status = Objects.requireNonNull(status, "status");
        error = Objects.requireNonNull(error, "error");
        if (instanceId != null && instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must be null or non-blank");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        if (status == CloudConnectionStatus.READY && error != CloudErrorCode.NONE) {
            throw new IllegalArgumentException("READY cannot carry an error");
        }
    }

    public boolean isAvailable() {
        return status.isAvailable();
    }

    public boolean allowsMutations() {
        return status.allowsMutations();
    }
}
