package com.micaftic.morpher.core.api.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import com.micaftic.morpher.core.api.loader.LoaderKind;

public final class PlatformAPIImpl {
    private PlatformAPIImpl() {
    }

    public static boolean isServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public static String getPlatformName() {
        return "Fabric";
    }

    public static LoaderKind loaderKind() {
        return LoaderKind.FABRIC;
    }

    public static String getModVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("sparkle_morpher")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
