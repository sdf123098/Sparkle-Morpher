package com.micaftic.morpher.core.api;

import net.neoforged.fml.loading.FMLEnvironment;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    public static boolean isServer() {
        return FMLUtils.isDedicatedServer();
    }

    public static String getPlatformName() {
        return "neoforge";
    }

    private static final class FMLUtils {
        static boolean isDedicatedServer() {
            return FMLEnvironment.getDist().isDedicatedServer();
        }
    }
}
