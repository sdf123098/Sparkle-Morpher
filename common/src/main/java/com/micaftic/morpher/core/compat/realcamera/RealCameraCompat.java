package com.micaftic.morpher.core.compat.realcamera;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class RealCameraCompat {

    private RealCameraCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isActive() {
        throw new AssertionError();
    }
}
