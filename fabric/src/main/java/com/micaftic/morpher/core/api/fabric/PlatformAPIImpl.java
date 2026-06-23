package com.micaftic.morpher.core.api.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class PlatformAPIImpl {
    private PlatformAPIImpl() {
    }

    public static boolean isServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    public static String getPlatformName() {
        return "Fabric";
    }
}
