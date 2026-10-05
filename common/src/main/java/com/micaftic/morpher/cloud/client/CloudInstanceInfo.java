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
        AuthCapabilities auth
) {
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

