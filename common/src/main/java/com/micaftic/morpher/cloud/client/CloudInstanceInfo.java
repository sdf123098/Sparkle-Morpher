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
        long heartbeatTtlSeconds
) {
}

