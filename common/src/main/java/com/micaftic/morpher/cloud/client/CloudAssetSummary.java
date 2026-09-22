package com.micaftic.morpher.cloud.client;

/** Directory metadata; file bytes are fetched separately. */
public record CloudAssetSummary(
        CloudAssetRef ref,
        String name,
        String format,
        long byteLength
) {
}

