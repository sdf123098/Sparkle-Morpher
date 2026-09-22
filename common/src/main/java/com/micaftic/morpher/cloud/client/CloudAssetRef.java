package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable cache identity for one original Cloud asset revision. */
public record CloudAssetRef(String assetId, long revision, String rawSha256) {

    private static final Pattern ASSET_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public CloudAssetRef {
        assetId = Objects.requireNonNull(assetId, "assetId").trim().toLowerCase(java.util.Locale.ROOT);
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256").trim().toLowerCase(java.util.Locale.ROOT);
        if (!ASSET_ID.matcher(assetId).matches()) {
            throw new IllegalArgumentException("assetId must be a lowercase ASCII Cloud slug");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (!SHA256.matcher(rawSha256).matches()) {
            throw new IllegalArgumentException("rawSha256 must be a lowercase SHA-256 hex string");
        }
    }

    public String contentPath() {
        return "/v1/assets/" + assetId + "/revisions/" + revision + "/content";
    }
}

