package com.micaftic.morpher.core.api;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    @ExpectPlatform
    public static boolean isServer() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getPlatformName() {
        throw new AssertionError();
    }
}
