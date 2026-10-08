package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudVisualCapabilitiesTest {
    private static final CloudInstanceConfig INSTANCE = CloudInstanceConfig.v1("test", URI.create("https://cloud.example"));
    private static String response(String fields) {
        return "{\"instance_id\":\"test\",\"origin\":\"https://cloud.example\",\"protocol\":\"spm.cloud.v1\"" + fields + "}";
    }
    @Test void legacyServiceUsesKnownBatchAndDoesNotInventCapabilities() {
        var info = CloudHttpClient.parseInstanceResponse(INSTANCE, response(""));
        assertEquals(64, info.maxEntityQueryCount());
        assertFalse(info.supports("projectile_snapshot_v1"));
    }
    @Test void negotiatedBudgetsAndCapabilitiesRemainIndependent() {
        var info = CloudHttpClient.parseInstanceResponse(INSTANCE, response(",\"capabilities\":[\"projectile_snapshot_v1\"],\"limits\":{\"max_entity_query_count\":3,\"max_visual_variables\":0}"));
        assertEquals(3, info.maxEntityQueryCount());
        assertEquals(0, info.maxVisualVariables());
        assertTrue(info.supports("projectile_snapshot_v1"));
        assertFalse(info.playerMotionSupported());
    }
    @Test void malformedBudgetsCannotBeTruncatedOrSilentlyDefaulted() {
        for (String limit : new String[]{"0", "257", "1.5", "\"3\"", "true", "null", "9999999999999999999999999"})
            assertThrows(CloudHttpException.class, () -> CloudHttpClient.parseInstanceResponse(INSTANCE, response(",\"limits\":{\"max_entity_query_count\":"+limit+"}")));
    }
}
