package com.micaftic.morpher.core.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        net.neoforged.fml.config.ModConfig.Type neoType = net.neoforged.fml.config.ModConfig.Type.valueOf(type.name());
        ConfigRegistry.INSTANCE.register(modId, neoType, (net.neoforged.fml.config.IConfigSpec) spec);
    }
}
