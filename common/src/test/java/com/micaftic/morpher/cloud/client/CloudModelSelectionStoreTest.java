package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CloudModelSelectionStoreTest {
    @TempDir Path root;
    private CloudModelSelectionStore.Index store() { return new CloudModelSelectionStore.Index(root.resolve("selection.json")); }
    private CloudAssetSummary asset(String id) { return new CloudAssetSummary(new CloudAssetRef(id, 1, "a".repeat(64)), id, "ysm", 20, "PRIVATE"); }

    @Test void applyingDoesNotFavoriteAndFavoritingDoesNotApply() {
        var store = store();
        store.recordApplied("official", asset("used"));
        store.setFavorite("official", asset("star"), true);
        assertFalse(store.isFavorite("official", "used"));
        assertEquals(List.of(asset("used")), store.read("official", false));
        assertEquals(List.of(asset("star")), store.read("official", true));
    }
    @Test void favoritesSurviveHistoryLimitAndRepeatedBatchOperations() {
        var store = store();
        store.setFavorite("official", asset("old"), true);
        store.recordApplied("official", asset("old"));
        for (int i = 0; i < 105; i++) store.recordApplied("official", asset("used-" + i));
        assertEquals(100, store.read("official", false).size());
        assertTrue(store.isFavorite("official", "old"));
        store.setFavorite("official", asset("old"), true);
        assertTrue(store.isFavorite("official", "old"));
        var reopened = store();
        assertTrue(reopened.isFavorite("official", "old"));
        assertFalse(reopened.isFavorite("community", "old"));
        reopened.setFavorite("official", asset("old"), false);
        assertTrue(reopened.read("official", true).isEmpty());
    }
    @Test void refreshedFavoriteUsesLatestRevisionButRecoveryKeepsAppliedBytes() {
        var store = store();
        var original = asset("skin");
        var updated = new CloudAssetSummary(new CloudAssetRef("skin", 2, "b".repeat(64)), "New skin", "ysm", 40, "PUBLIC");
        store.recordApplied("official", original);
        store.setFavorite("official", original, true);
        store.refreshSummaries("official", List.of(updated));
        assertEquals(updated, store.read("official", true).getFirst());
        var recent = store.read("official", false).getFirst();
        assertEquals(original.ref(), recent.ref());
        assertEquals("PUBLIC", recent.visibility());
        assertEquals("New skin", recent.name());
        store.setFavorite("official", original, true);
        assertEquals(updated, store.read("official", true).getFirst());
        assertEquals(original.ref(), store().read("official", false).getFirst().ref());
    }
    @Test void legacyFlagsArePreservedAndDamagedEntriesDoNotHideValidFavorites() throws Exception {
        Files.writeString(root.resolve("selection.json"), """
                {"official":[{"asset_id":"bad","favorite":{},"last_used_at":{}},
                  {"asset_id":"legacy","revision":1,"raw_sha256":"%s","name":"legacy","format":"ysm","byte_length":20,"favorite":true,"last_used_at":1}]}
                """.formatted("a".repeat(64)));
        var store = store();
        assertEquals("legacy", store.read("official", true).getFirst().ref().assetId());
        store.recordApplied("official", asset("used"));
        assertTrue(store().isFavorite("official", "legacy"));
    }
    @Test void failedWriteDoesNotPretendFavoriteWasSaved() throws Exception {
        var store = store();
        assertFalse(store.isFavorite("official", "skin"));
        Files.createDirectory(root.resolve("selection.json"));
        Files.writeString(root.resolve("selection.json/blocker"), "do not replace");
        assertThrows(java.io.UncheckedIOException.class, () -> store.setFavorite("official", asset("skin"), true));
        assertFalse(store.isFavorite("official", "skin"));
        assertEquals("do not replace", Files.readString(root.resolve("selection.json/blocker")));
    }
    @Test void corruptedStoreIsNeverOverwrittenAsAnEmptyIndex() throws Exception {
        Files.writeString(root.resolve("selection.json"), "invalid json");
        assertThrows(IllegalStateException.class, () -> store().setFavorite("official", asset("skin"), true));
        assertEquals("invalid json", Files.readString(root.resolve("selection.json")));
    }
    @Test void starLookupUsesCachedIndex() throws Exception {
        var store = store();
        store.setFavorite("official", asset("skin"), true);
        Files.delete(root.resolve("selection.json"));
        assertTrue(store.isFavorite("official", "skin"));
    }
    @Test void mixedBatchFavoritesSetAnExplicitValueForEveryTarget() {
        var store = store();
        var assets = List.of(asset("first"), asset("second"));
        store.setFavorite("official", assets.getFirst(), true);
        store.setFavorites("official", assets, true);
        store.setFavorites("official", assets, true);
        assertEquals(2, store.read("official", true).size());
        assertTrue(store.read("official", false).isEmpty());
        store.setFavorites("official", assets, false);
        assertTrue(store().read("official", true).isEmpty());
    }
    @Test void ApplyingACachedSummaryDoesNotRevertNewVisibilityAtTheSameRevision() {
        var store = store(); var original = asset("skin");
        store.recordApplied("official", original); store.setFavorite("official", original, true);
        var publicSummary = new CloudAssetSummary(original.ref(), "skin", "ysm", 20, "PUBLIC");
        store.refreshSummaries("official", List.of(publicSummary));
        store.recordApplied("official", original); store.setFavorite("official", original, true);
        assertEquals("PUBLIC", store.read("official", true).getFirst().visibility());
        assertEquals("PUBLIC", store.read("official", false).getFirst().visibility());
    }
}
