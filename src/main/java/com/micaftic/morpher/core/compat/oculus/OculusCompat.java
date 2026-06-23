package com.micaftic.morpher.core.compat.oculus;

public final class OculusCompat {

    private OculusCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isPBRActive() {
        return false;
    }

    public static void updatePBRState() {
    }

    public static boolean isShaderPackInUse() {
        return false;
    }

    public static boolean isRenderingShadowPass() {
        return false;
    }
}
