package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CloudAssetRefTest {

    @Test
    void preservesUnicodeCaseAndEscapesOneAssetPathSegment() {
        String id = "芙宁娜V3.14 100%+日语";
        CloudAssetRef ref = new CloudAssetRef(id, 2, "a".repeat(64));
        assertEquals(id, ref.assetId());
        assertEquals("/v1/assets/%E8%8A%99%E5%AE%81%E5%A8%9CV3%2E14%20100%25%2B%E6%97%A5%E8%AF%AD/revisions/2/content", ref.contentPath());
    }

    @Test
    void enforcesUtf8ByteLengthAndRejectsUnsafeCacheNames() {
        String boundary = "模".repeat(42) + "Ab";
        assertEquals(boundary, new CloudAssetRef(boundary, 1, "a".repeat(64)).assetId());
        assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef("模".repeat(43), 1, "a".repeat(64)));
        for (String invalid : new String[]{"", "   ", "bad\nname", "bad\u0000name", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b"}) {
            assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef(invalid, 1, "a".repeat(64)));
        }
    }

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
