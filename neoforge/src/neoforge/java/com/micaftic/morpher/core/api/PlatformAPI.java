package com.micaftic.morpher.core.api;

import com.micaftic.morpher.core.api.loader.LoaderKind;

import net.neoforged.fml.loading.FMLEnvironment;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    public static boolean isServer() {
        return FMLUtils.isDedicatedServer();
    }

    public static boolean isClient() {
        return !FMLUtils.isDedicatedServer();
    }

    public static String getPlatformName() {
        return "neoforge";
    }

    public static LoaderKind loaderKind() {
        return LoaderKind.NEOFORGE;
    }

    private static final class FMLUtils {
        static boolean isDedicatedServer() {
            return FMLEnvironment.getDist().isDedicatedServer();
        }
    }
}
