package com.micaftic.morpher.core.model;

import java.util.Locale;
import java.util.Objects;

/** Immutable identity for a Cloud asset and its cacheable content revision. */
public record CloudAssetIdentity(String instance, String tenant, String assetId,
                                 String revision, String contentHash) {
    public CloudAssetIdentity {
        instance = segment(instance, "instance");
        tenant = segment(tenant, "tenant");
        assetId = segment(assetId, "assetId");
        revision = segment(revision, "revision");
        contentHash = Objects.requireNonNull(contentHash, "contentHash").trim().toLowerCase(Locale.ROOT);
        if (!contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a SHA-256 hex digest");
        }
    }

    public String cacheKey() {
        return instance + "/" + tenant + "/" + assetId + "/" + revision + "/" + contentHash;
    }

    public ModelRef modelRef() {
        return ModelRef.of(ModelSourceType.CLOUD, instance + "/" + tenant,
                assetId + "@" + revision + "#" + contentHash);
    }

    private static String segment(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0 || normalized.indexOf('/') >= 0) {
            throw new IllegalArgumentException(name + " must be a non-empty path-safe segment");
        }
        return normalized;
    }
}
