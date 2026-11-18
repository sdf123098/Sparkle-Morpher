package com.micaftic.morpher.core.api;

import com.micaftic.morpher.core.api.loader.LoaderKind;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    public static boolean isServer() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.isServer();
    }

    public static boolean isClient() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.isClient();
    }

    public static String getPlatformName() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.getPlatformName();
    }

    public static LoaderKind loaderKind() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.loaderKind();
    }

    public static String getModVersion() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.getModVersion();
    }

    public static boolean isModLoaded(String modId) {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.isModLoaded(modId);
    }

    public static String getModVersion(String modId) {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.getModVersion(modId);
    }
}
