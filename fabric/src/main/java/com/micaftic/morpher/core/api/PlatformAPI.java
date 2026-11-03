package com.micaftic.morpher.core.api;

import dev.architectury.injectables.annotations.ExpectPlatform;
import com.micaftic.morpher.core.api.loader.LoaderKind;

public final class PlatformAPI {
    private PlatformAPI() {
    }

    @ExpectPlatform
    public static boolean isServer() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isClient() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getPlatformName() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static LoaderKind loaderKind() {
        throw new AssertionError();
    }
}
