package com.micaftic.morpher.core.compat.firstperson;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class FirstPersonCompat {

    private FirstPersonCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isFirstPersonActive() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean shouldHideHead() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void setCameraDistance(float distance) {
        throw new AssertionError();
    }
}
