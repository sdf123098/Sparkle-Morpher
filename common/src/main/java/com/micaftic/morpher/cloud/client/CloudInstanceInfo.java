package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;

/** Immutable capability response returned by a Cloud instance. */
public record CloudInstanceInfo(
        CloudInstanceConfig config,
        long maxMessageBytes,
        long maxSnapshotBytes,
        long maxSnapshotChunkBytes,
        long maxAssetBytes,
        long maxSubscriptions,
        long heartbeatIntervalSeconds,
        long heartbeatTtlSeconds,
        boolean playerMotionSupported,
        AuthCapabilities auth,
        long maxEntityQueryCount,
        java.util.Set<String> capabilities,
        long maxVisualStateBytes,
        long maxVisualVariables
) {
    public CloudInstanceInfo {
        if (maxEntityQueryCount < 1 || maxEntityQueryCount > 256) throw new IllegalArgumentException("Invalid entity query limit");
        capabilities = java.util.Set.copyOf(capabilities);
    }
    public CloudInstanceInfo(CloudInstanceConfig config, long message, long snapshot, long chunk, long asset,
                             long subscriptions, long interval, long ttl, boolean motion, AuthCapabilities auth) {
        this(config, message, snapshot, chunk, asset, subscriptions, interval, ttl, motion, auth,
                64, motion ? java.util.Set.of("player_motion_v1") : java.util.Set.of(), 8192, 32);
    }
    public boolean supports(String capability) { return capabilities.contains(capability); }
    public CloudInstanceInfo(CloudInstanceConfig config, long message, long snapshot, long chunk, long asset,
                             long subscriptions, long interval, long ttl) {
        this(config, message, snapshot, chunk, asset, subscriptions, interval, ttl, false, null);
    }
    public CloudInstanceInfo(CloudInstanceConfig config, long message, long snapshot, long chunk, long asset,
                             long subscriptions, long interval, long ttl, boolean motion) {
        this(config, message, snapshot, chunk, asset, subscriptions, interval, ttl, motion, null);
    }

    /** Null metadata keeps older instances compatible; explicit false is authoritative. */
    public record AuthCapabilities(boolean passwordLogin, boolean gameIdentityLogin, boolean gameIdentityLink, boolean selfRegistration) {}
}

