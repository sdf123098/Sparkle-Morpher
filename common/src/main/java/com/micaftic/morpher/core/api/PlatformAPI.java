package com.micaftic.morpher.core.api;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    public static boolean isServer() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.isServer();
    }

    public static String getPlatformName() {
        return com.micaftic.morpher.core.api.fabric.PlatformAPIImpl.getPlatformName();
    }
}
