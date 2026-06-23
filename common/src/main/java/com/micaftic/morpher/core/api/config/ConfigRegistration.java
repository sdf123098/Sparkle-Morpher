package com.micaftic.morpher.core.api.config;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistration {

    private ConfigRegistration() {
    }

    @ExpectPlatform
    public static void register(String modId, ModConfig.Type type, Object spec) {
        throw new AssertionError();
    }
}
