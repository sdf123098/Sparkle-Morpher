package com.micaftic.morpher.cloud.client;

import java.util.Locale;
import java.util.Objects;

/**
 * Directory metadata; file bytes are fetched separately.
 *
 * <p>The optional {@code thumbnail} pair is served by the anonymous immutable
 * public route. It is only accepted as a relative {@code /v1/public/assets/...}
 * path, so a catalog row can never redirect the card image to another origin.</p>
 */
public record CloudAssetSummary(
        CloudAssetRef ref,
        String name,
        String format,
        long byteLength,
        String visibility,
        String ownerAccountId,
        boolean owned,
        String thumbnail,
        String thumbnailStatus
) {
    /** Values defined by the Cloud catalog; anything else is treated as unsupported. */
    private static final java.util.Set<String> THUMBNAIL_STATUSES = java.util.Set.of("ready", "pending", "failed");
    private static final int MAX_THUMBNAIL_PATH_LENGTH = 512;

    public CloudAssetSummary(CloudAssetRef ref, String name, String format, long byteLength, String visibility,
                             String ownerAccountId, boolean owned) {
        this(ref, name, format, byteLength, visibility, ownerAccountId, owned, "", "");
    }

    public CloudAssetSummary(CloudAssetRef ref, String name, String format, long byteLength, String visibility) {
        this(ref, name, format, byteLength, visibility, "", false, "", "");
    }

    public CloudAssetSummary(CloudAssetRef ref, String name, String format, long byteLength) {
        this(ref, name, format, byteLength, "PRIVATE");
    }

    public CloudAssetSummary {
        Objects.requireNonNull(ref, "ref");
        name = Objects.requireNonNull(name, "name");
        format = Objects.requireNonNull(format, "format");
        ownerAccountId = ownerAccountId == null ? "" : ownerAccountId;
        visibility = visibility == null || visibility.isBlank() ? "PRIVATE" : visibility.trim().toUpperCase(Locale.ROOT);
        if (!visibility.equals("PRIVATE") && !visibility.equals("PUBLIC")) {
            throw new IllegalArgumentException("Cloud asset visibility must be PRIVATE or PUBLIC");
        }
        if (byteLength < 0) throw new IllegalArgumentException("byteLength must not be negative");
        thumbnail = publicThumbnailPath(thumbnail);
        thumbnailStatus = thumbnailStatus == null ? "" : thumbnailStatus.trim().toLowerCase(Locale.ROOT);
        if (!THUMBNAIL_STATUSES.contains(thumbnailStatus)) thumbnailStatus = "";
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }

    /** A ready static image is advertised, so the card never needs model bytes to render. */
    public boolean thumbnailReady() {
        return "ready".equals(thumbnailStatus) && !thumbnail.isEmpty();
    }

    /**
     * Keeps only the immutable anonymous thumbnail route; an absent, absolute,
     * traversal or otherwise unexpected value degrades to "no thumbnail".
     */
    private static String publicThumbnailPath(String value) {
        if (value == null) return "";
        String path = value.trim();
        return CloudThumbnailPolicy.isPublicThumbnailPath(path) ? path : "";
    }
}
