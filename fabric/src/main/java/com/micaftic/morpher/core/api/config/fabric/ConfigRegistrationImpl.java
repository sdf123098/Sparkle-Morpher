package com.micaftic.morpher.core.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        ForgeConfigRegistry.INSTANCE.register(modId, type, (net.minecraftforge.fml.config.IConfigSpec<?>) spec);
    }
}
