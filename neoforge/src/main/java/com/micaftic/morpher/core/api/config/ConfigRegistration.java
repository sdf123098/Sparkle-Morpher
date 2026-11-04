package com.micaftic.morpher.core.api.config;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;

public final class ConfigRegistration {
    private static ModContainer container;

    private ConfigRegistration() {
    }

    public static void setContainer(ModContainer modContainer) {
        container = modContainer;
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        if (container != null && spec instanceof IConfigSpec configSpec) {
            container.registerConfig(type, configSpec);
        }
    }
}
