package com.micaftic.morpher.core.compat.oculus;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class OculusCompat {

    private OculusCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isPBRActive() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void updatePBRState() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isShaderPackInUse() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isRenderingShadowPass() {
        throw new AssertionError();
    }
}
