package com.micaftic.morpher.core.api.config;

import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistration {

    private ConfigRegistration() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        com.micaftic.morpher.core.api.config.fabric.ConfigRegistrationImpl.register(modId, type, spec);
    }
}
