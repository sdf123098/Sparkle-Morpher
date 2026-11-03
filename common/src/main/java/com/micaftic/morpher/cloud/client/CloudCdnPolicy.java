package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.config.GeneralConfig;

/** Client preference for the immutable public Cloud asset route. */
public final class CloudCdnPolicy {
    private CloudCdnPolicy() {}

    public static boolean enabled() {
        return GeneralConfig.safeGet(GeneralConfig.CLOUD_CDN_CACHE, true);
    }
}
