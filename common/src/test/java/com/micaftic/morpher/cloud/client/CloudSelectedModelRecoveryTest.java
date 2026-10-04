package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CloudSelectedModelRecoveryTest {
    @Test
    void selectedRevisionIsRecoveredOnlyFromItsOwnInstance() {
        CloudAssetSummary older = new CloudAssetSummary(new CloudAssetRef("skin", 1, "a".repeat(64)), "skin", "ysm", 8);
        CloudAssetSummary selected = new CloudAssetSummary(new CloudAssetRef("skin", 2, "b".repeat(64)), "skin", "glb", 8);
        String selectedId = new CloudAssetIdentity("official", "catalog", "skin", "2", "b".repeat(64)).runtimeModelId();

        assertEquals(selected, CloudSelectedModelRecovery.findAsset("official", selectedId, List.of(older, selected)));
        assertNull(CloudSelectedModelRecovery.findAsset("other", selectedId, List.of(selected)));
    }
}
