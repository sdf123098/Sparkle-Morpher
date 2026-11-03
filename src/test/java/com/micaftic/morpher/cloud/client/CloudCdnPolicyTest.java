package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.config.GeneralConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudCdnPolicyTest {
    @Test
    void missingConfigurationDefaultsToCdn() {
        var previous = GeneralConfig.CLOUD_CDN_CACHE;
        try {
            GeneralConfig.CLOUD_CDN_CACHE = null;
            assertTrue(CloudCdnPolicy.enabled());
        } finally {
            GeneralConfig.CLOUD_CDN_CACHE = previous;
        }
    }
}
