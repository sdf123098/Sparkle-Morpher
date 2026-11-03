package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Immutable cache identity for one original Cloud asset revision. */
public record CloudAssetRef(String assetId, long revision, String rawSha256) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public CloudAssetRef {
        assetId = Objects.requireNonNull(assetId, "assetId");
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256").trim().toLowerCase(java.util.Locale.ROOT);
        if (assetId.isBlank() || assetId.getBytes(StandardCharsets.UTF_8).length > 128
                || assetId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("assetId must be a non-empty identifier of at most 128 UTF-8 bytes without control characters");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (!SHA256.matcher(rawSha256).matches()) {
            throw new IllegalArgumentException("rawSha256 must be a lowercase SHA-256 hex string");
        }
    }

    public String contentPath() {
        return contentPath(null, false);
    }

    public String contentPath(String visibility, boolean cdnEnabled) {
        String prefix = cdnEnabled && "PUBLIC".equals(visibility) ? "/v1/public/assets/" : "/v1/assets/";
        return prefix + pathSegment() + "/revisions/" + revision + "/content";
    }

    private String pathSegment() {
        return URLEncoder.encode(assetId, StandardCharsets.UTF_8).replace("+", "%20").replace(".", "%2E");
    }
}

