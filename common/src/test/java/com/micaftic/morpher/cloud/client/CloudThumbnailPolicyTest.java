package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudThumbnailPolicyTest {
    private static final CloudAssetRef REF = new CloudAssetRef("model_01", 2, "a".repeat(64));

    private static CloudAssetSummary summary(String thumbnail, String status, String visibility) {
        return new CloudAssetSummary(REF, "Model", "ysm", 1024, visibility, "", false, thumbnail, status);
    }

    @Test
    void readyPublicThumbnailReplacesTheLivePreview() {
        var ready = summary("/v1/public/assets/model_01/revisions/2/thumbnail", "ready", "PUBLIC");
        assertTrue(ready.thumbnailReady());
        assertTrue(CloudThumbnailPolicy.usesThumbnail(ready));
        assertTrue(CloudThumbnailPolicy.legacyPreviews(List.of(ready)).isEmpty());
    }

    @Test
    void pendingFailedPrivateAndDisabledRowsKeepTheLegacyPreview() {
        CloudAssetSummary pending = summary("/v1/public/assets/model_01/revisions/2/thumbnail", "pending", "PUBLIC");
        CloudAssetSummary failed = summary("/v1/public/assets/model_01/revisions/2/thumbnail", "failed", "PUBLIC");
        CloudAssetSummary privateRow = summary("/v1/public/assets/model_01/revisions/2/thumbnail", "ready", "PRIVATE");
        CloudAssetSummary absent = summary("", "", "PUBLIC");
        for (CloudAssetSummary entry : List.of(pending, failed, privateRow, absent)) {
            assertFalse(CloudThumbnailPolicy.usesThumbnail(entry));
            assertEquals(List.of(entry), CloudThumbnailPolicy.legacyPreviews(List.of(entry)));
        }
        assertFalse(CloudThumbnailPolicy.usesThumbnail(
                summary("/v1/public/assets/model_01/revisions/2/thumbnail", "ready", "PUBLIC"), false),
                "the greyed-out public route keeps today's live preview");
    }

    @Test
    void onlyTheAnonymousThumbnailRouteIsAccepted() {
        assertTrue(CloudThumbnailPolicy.isPublicThumbnailPath("/v1/public/assets/model_01/revisions/2/thumbnail"));
        for (String rejected : new String[]{
                null, "", " ", "/v1/assets/model_01/revisions/2/content",
                "/v1/public/assets/model_01/revisions/2/content",
                "https://cloud.example.test/v1/public/assets/m/revisions/1/thumbnail",
                "/v1/public/assets/../../etc/thumbnail",
                "/v1/public/assets/m/revisions/1/thumbnail\u0000",
                "/v1/public/assets/m with space/revisions/1/thumbnail",
                "/v1/public/assets/" + "a".repeat(600) + "/thumbnail"}) {
            assertFalse(CloudThumbnailPolicy.isPublicThumbnailPath(rejected), String.valueOf(rejected));
        }
    }

    @Test
    void hostileCatalogValuesDegradeToNoThumbnailInsteadOfAForeignOrigin() {
        CloudAssetSummary absolute = summary("https://evil.example.test/v1/public/assets/m/revisions/1/thumbnail", "ready", "PUBLIC");
        CloudAssetSummary traversal = summary("/v1/public/assets/../private/thumbnail", "ready", "PUBLIC");
        CloudAssetSummary unknownStatus = summary("/v1/public/assets/m/revisions/1/thumbnail", "processing", "PUBLIC");
        assertEquals("", absolute.thumbnail());
        assertEquals("", traversal.thumbnail());
        assertEquals("", unknownStatus.thumbnailStatus());
        assertFalse(absolute.thumbnailReady());
        assertFalse(unknownStatus.thumbnailReady());
    }

    @Test
    void anEncodedServerSuppliedPathSurvivesTheCatalogRoundTrip() {
        CloudAssetSummary encoded = summary("/v1/public/assets/gooner%2Factual%20gooner%20shit%2F%E7%AC%99%E9%BB%91%2026%203%2028"
                + "/revisions/3/thumbnail", "ready", "PUBLIC");
        assertTrue(encoded.thumbnailReady());
        assertTrue(CloudThumbnailPolicy.usesThumbnail(encoded));
        assertEquals(List.of(), CloudThumbnailPolicy.legacyPreviews(List.of(encoded)));
    }

    @Test
    void onlyASingleInBudgetPngIsAccepted() {
        assertTrue(CloudThumbnailPolicy.isThumbnailPng(png(64, 64)));
        assertFalse(CloudThumbnailPolicy.isThumbnailPng(null));
        assertFalse(CloudThumbnailPolicy.isThumbnailPng(new byte[0]));
        assertFalse(CloudThumbnailPolicy.isThumbnailPng("not a png".getBytes(StandardCharsets.UTF_8)));
        assertFalse(CloudThumbnailPolicy.isThumbnailPng(png(0, 64)));
        assertFalse(CloudThumbnailPolicy.isThumbnailPng(png(CloudThumbnailPolicy.MAX_DIMENSION + 1, 64)));
    }

    private static byte[] png(int width, int height) {
        byte[] bytes = new byte[24];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[12] = 'I';
        bytes[13] = 'H';
        bytes[14] = 'D';
        bytes[15] = 'R';
        bytes[16] = (byte) (width >>> 24);
        bytes[17] = (byte) (width >>> 16);
        bytes[18] = (byte) (width >>> 8);
        bytes[19] = (byte) width;
        bytes[20] = (byte) (height >>> 24);
        bytes[21] = (byte) (height >>> 16);
        bytes[22] = (byte) (height >>> 8);
        bytes[23] = (byte) height;
        return bytes;
    }
}
