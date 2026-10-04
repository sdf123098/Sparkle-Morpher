package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudAssetSummary;
import java.util.List;
import java.util.Locale;

/** Filtering is shared by cards, classic rows, pagination and bulk selection. */
public final class CloudModelBrowserFilter {
    private CloudModelBrowserFilter() {}
    public static List<CloudAssetSummary> filter(List<CloudAssetSummary> entries, String query, String visibility) {
        String text = query.trim().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> visibility.isEmpty() || visibility.equals(entry.visibility()))
                .filter(entry -> text.isEmpty() || (entry.name() + "\n" + entry.ref().assetId()).toLowerCase(Locale.ROOT).contains(text)).toList();
    }
}
