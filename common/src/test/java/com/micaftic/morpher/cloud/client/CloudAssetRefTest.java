package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CloudAssetRefTest {

    @Test
    void contentPathUsesOnlyValidatedAssetIdentity() {
        CloudAssetRef ref = new CloudAssetRef(
                "model_01", 7,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertEquals("/v1/assets/model_01/revisions/7/content", ref.contentPath());
    }

    @Test
    void rejectsPathTraversalAndNonShaIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef(
                "../secret", 1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef(
                "model", 1, "not-a-sha"));
    }
}
