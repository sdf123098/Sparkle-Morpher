package com.micaftic.morpher.client.gui;
import com.micaftic.morpher.cloud.client.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CloudModelBrowserFilterTest {
    @Test void favoritesCanBeSearchedByChineseNameOrStableIdAndVisibility() {
        var privateModel = new CloudAssetSummary(new CloudAssetRef("Furina", 1, "a".repeat(64)), "芙宁娜日语配音", "ysm", 10, "PRIVATE");
        var publicModel = new CloudAssetSummary(new CloudAssetRef("public", 1, "b".repeat(64)), "芙宁娜中文配音", "ysm", 10, "PUBLIC");
        var entries = List.of(privateModel, publicModel);
        assertEquals(List.of(privateModel), CloudModelBrowserFilter.filter(entries, "  fUrInA  ", ""));
        assertEquals(List.of(publicModel), CloudModelBrowserFilter.filter(entries, "芙宁娜", "PUBLIC"));
        assertTrue(CloudModelBrowserFilter.filter(entries, "日语", "PUBLIC").isEmpty());
        assertEquals(2, entries.size());
    }
}
