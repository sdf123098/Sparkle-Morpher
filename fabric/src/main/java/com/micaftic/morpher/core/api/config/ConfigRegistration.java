package com.micaftic.morpher.core.api.config;

import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistration {

    private ConfigRegistration() {
    }


    @SuppressWarnings("removal") // The 1.21.1 port still supplies Forge-format config specs.
    public static void register(String modId, ModConfig.Type type, Object spec) {
        com.micaftic.morpher.core.api.config.fabric.ConfigRegistrationImpl.register(modId, type, spec);
    }
}
