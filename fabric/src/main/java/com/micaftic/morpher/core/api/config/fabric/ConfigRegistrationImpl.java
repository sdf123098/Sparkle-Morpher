package com.micaftic.morpher.core.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import com.micaftic.morpher.core.api.config.ConfigType;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ConfigType type, Object spec) {
        net.neoforged.fml.config.ModConfig.Type neoType = switch (type) {
            case CLIENT -> net.neoforged.fml.config.ModConfig.Type.CLIENT;
            case COMMON -> net.neoforged.fml.config.ModConfig.Type.COMMON;
            case SERVER -> net.neoforged.fml.config.ModConfig.Type.SERVER;
        };
        ConfigRegistry.INSTANCE.register(modId, neoType, (net.neoforged.fml.config.IConfigSpec) spec);
    }
}
