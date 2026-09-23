package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudAssetCatalogStoreTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void publishesAnImmutableRevisionAwareDirectory() {
        CloudAssetCatalogStore store = new CloudAssetCatalogStore();
        CloudAssetSummary entry = new CloudAssetSummary(new CloudAssetRef("model", 1, SHA), "Model", "ysm", 12);

        assertEquals(1L, store.replace(List.of(entry)));
        assertEquals(entry, store.get("model"));
        assertEquals(1L, store.generation());
        assertThrows(UnsupportedOperationException.class, () -> store.snapshot().clear());
    }

    @Test
    void rejectsConflictingDuplicateAssetIds() {
        CloudAssetCatalogStore store = new CloudAssetCatalogStore();
        CloudAssetSummary first = new CloudAssetSummary(new CloudAssetRef("model", 1, SHA), "Model", "ysm", 12);
        CloudAssetSummary second = new CloudAssetSummary(new CloudAssetRef("model", 2, SHA), "Model v2", "ysm", 13);

        assertThrows(IllegalArgumentException.class, () -> store.replace(List.of(first, second)));
    }
}
