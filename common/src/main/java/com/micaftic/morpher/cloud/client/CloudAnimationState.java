package com.micaftic.morpher.cloud.client;

/** Latest target animation state received from Cloud realtime. */
public record CloudAnimationState(
        String targetId,
        long revision,
        String channel,
        String action,
        String animationKey,
        long expiresAtUnixMs
) {
    public CloudAnimationState {
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel must not be blank");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        if (animationKey == null) throw new IllegalArgumentException("animationKey must not be null");
    }
}
