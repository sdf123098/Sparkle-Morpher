package com.micaftic.morpher.cloud.client;

import java.util.List;

/** One bounded page of Cloud asset metadata. */
public record CloudAssetPage(List<CloudAssetSummary> entries, String nextCursor, boolean hasMore) {
    public CloudAssetPage {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (nextCursor != null && nextCursor.isBlank()) nextCursor = null;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
