package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudAppearanceStoreTest {
    @Test
    void appliesOnlyNewerAppearanceRevisions() {
        CloudAppearanceStore store = new CloudAppearanceStore();
        CloudScopeClient.CloudAppearance first = new CloudScopeClient.CloudAppearance("target", 3, "asset-a", 1L, "a", null, 1.0f, false);
        CloudScopeClient.CloudAppearance stale = new CloudScopeClient.CloudAppearance("target", 2, "asset-old", 1L, "b", null, 1.0f, false);
        CloudScopeClient.CloudAppearance next = new CloudScopeClient.CloudAppearance("target", 4, "asset-b", 2L, "c", null, 1.0f, false);

        assertTrue(store.apply("scope", first));
        assertFalse(store.apply("scope", stale));
        assertTrue(store.apply("scope", next));
        assertEquals("asset-b", store.get("scope", "target").assetId());
    }

    @Test
    void recoveryAppliesAppearancePayloads() {
        CloudAppearanceStore store = new CloudAppearanceStore();
        CloudScopeClient.CloudAppearance appearance = new CloudScopeClient.CloudAppearance("target", 7, null, null, null, "texture", 1.25f, false);
        CloudScopeClient.CloudEventRecovery recovery = new CloudScopeClient.CloudEventRecovery(4, 5, false,
                List.of(new CloudScopeClient.CloudRecoveredEvent(5, "event", "AppearanceState", appearance)));

        store.applyRecovery("scope", recovery);

        assertEquals(appearance, store.get("scope", "target"));
    }
}
