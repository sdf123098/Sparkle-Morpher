package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;

public final class NetworkOnlineDebugLog {

    private NetworkOnlineDebugLog() {
    }

    public static boolean enabled() {
        return GeneralConfig.safeGet(GeneralConfig.NETWORK_ONLINE_DEBUG_LOG, false);
    }

    public static void info(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.info("[SM-NetDebug] " + message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.warn("[SM-NetDebug] " + message, args);
        }
    }
}
