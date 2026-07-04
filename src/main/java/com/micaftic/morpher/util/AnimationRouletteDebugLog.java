package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;

public final class AnimationRouletteDebugLog {

    private AnimationRouletteDebugLog() {
    }

    public static boolean enabled() {
        return GeneralConfig.safeGet(GeneralConfig.ANIMATION_ROULETTE_DEBUG_LOG, false);
    }

    public static void info(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.info("[SM-ROULETTE] " + message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.warn("[SM-ROULETTE] " + message, args);
        }
    }
}
