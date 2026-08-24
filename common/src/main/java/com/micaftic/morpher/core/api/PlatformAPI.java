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
        return LoaderKind.FABRIC;
    }
}
