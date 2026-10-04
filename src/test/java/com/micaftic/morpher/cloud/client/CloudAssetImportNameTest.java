package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudAssetImportNameTest {
    @Test
    void everyUploadFormatKeepsItsParserAfterRecovery() {
        for (String format : new String[] {"ysm", "zip", "bbmodel", "gltf", "glb"}) {
            CloudAssetSummary summary = new CloudAssetSummary(
                    new CloudAssetRef("asset", 1, "a".repeat(64)), "model", format, 8);
            assertEquals("model." + format, CloudAssetImportName.fileName(summary));
        }
    }

    @Test
    void formatWinsWhenDisplayNameHasAStaleExtension() {
        CloudAssetSummary summary = new CloudAssetSummary(
                new CloudAssetRef("asset", 1, "a".repeat(64)), "model.gltf", "glb", 8);
        assertEquals("model.glb", CloudAssetImportName.fileName(summary));
    }
}
