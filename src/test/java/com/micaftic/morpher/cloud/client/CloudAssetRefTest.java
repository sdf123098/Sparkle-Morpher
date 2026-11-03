package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CloudAssetRefTest {

    @Test
    void preservesUnicodeCaseAndEscapesOneAssetPathSegment() {
        String id = "gooner/actual gooner shit/笙黑 26 3 28+100%";
        CloudAssetRef ref = new CloudAssetRef(id, 2, "a".repeat(64));
        assertEquals(id, ref.assetId());
        assertEquals("/v1/assets/gooner%2Factual%20gooner%20shit%2F%E7%AC%99%E9%BB%91%2026%203%2028%2B100%25/revisions/2/content", ref.contentPath());
        assertEquals("/v1/public/assets/gooner%2Factual%20gooner%20shit%2F%E7%AC%99%E9%BB%91%2026%203%2028%2B100%25/revisions/2/content",
                ref.contentPath("PUBLIC", true));
        assertEquals(ref.contentPath(), ref.contentPath("PRIVATE", true));
        assertEquals(ref.contentPath(), ref.contentPath(null, true));
        assertEquals(ref.contentPath(), ref.contentPath("PUBLIC", false));
    }

    @Test
    void enforcesUtf8ByteLengthAndRejectsUnsafeCacheNames() {
        String boundary = "模".repeat(42) + "Ab";
        assertEquals(boundary, new CloudAssetRef(boundary, 1, "a".repeat(64)).assetId());
        assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef("模".repeat(43), 1, "a".repeat(64)));
        for (String invalid : new String[]{"", "   ", "bad\nname", "bad\u0000name"}) {
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
    void pathLikeIdentityStaysInsideOneEncodedSegmentAndNonShaIsRejected() {
        CloudAssetRef pathLike = new CloudAssetRef("../secret", 1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertEquals("/v1/assets/%2E%2E%2Fsecret/revisions/1/content", pathLike.contentPath());
        assertThrows(IllegalArgumentException.class, () -> new CloudAssetRef(
                "model", 1, "not-a-sha"));
    }
}
