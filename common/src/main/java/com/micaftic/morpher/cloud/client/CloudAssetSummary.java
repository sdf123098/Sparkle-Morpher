package com.micaftic.morpher.cloud.client;

import java.util.Objects;

/** Directory metadata; file bytes are fetched separately. */
public record CloudAssetSummary(
        CloudAssetRef ref,
        String name,
        String format,
        long byteLength,
        String visibility
) {
    public CloudAssetSummary(CloudAssetRef ref, String name, String format, long byteLength) {
        this(ref, name, format, byteLength, "PRIVATE");
    }

    public CloudAssetSummary {
        Objects.requireNonNull(ref, "ref");
        name = Objects.requireNonNull(name, "name");
        format = Objects.requireNonNull(format, "format");
        visibility = visibility == null || visibility.isBlank() ? "PRIVATE" : visibility.trim().toUpperCase(java.util.Locale.ROOT);
        if (!visibility.equals("PRIVATE") && !visibility.equals("PUBLIC")) {
            throw new IllegalArgumentException("Cloud asset visibility must be PRIVATE or PUBLIC");
        }
        if (byteLength < 0) throw new IllegalArgumentException("byteLength must not be negative");
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }
}

