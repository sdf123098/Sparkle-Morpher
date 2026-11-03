package com.micaftic.morpher.core.api.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ConfigRegistration {
    private ConfigRegistration() {}
    public static void register(String modId, ModConfig.Type type, Object spec) {
        ModContainer container = ModList.get().getModContainerById(modId).orElseThrow();
        container.registerConfig(type, (ModConfigSpec) spec);
    }
}