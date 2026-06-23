package com.micaftic.morpher.core.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.neoforged.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        ConfigRegistry.INSTANCE.register(modId, type, (net.neoforged.fml.config.IConfigSpec) spec);
    }
}
