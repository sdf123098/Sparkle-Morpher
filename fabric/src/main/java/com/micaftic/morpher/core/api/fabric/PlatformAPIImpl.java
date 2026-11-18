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
        return getModVersion("sparkle_morpher");
    }

    public static boolean isModLoaded(String modId) {
        return modId != null && !modId.isEmpty() && FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String getModVersion(String modId) {
        if (modId == null || modId.isEmpty()) {
            return "unknown";
        }
        try {
            return FabricLoader.getInstance()
                    .getModContainer(modId)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
