package com.micaftic.morpher.core.compat.oculus;

public final class OculusCompat {

    private OculusCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.oculus.fabric.OculusCompatImpl.isLoaded();
    }

    public static boolean isPBRActive() {
        return com.micaftic.morpher.core.compat.oculus.fabric.OculusCompatImpl.isPBRActive();
    }

    public static void updatePBRState() {
        com.micaftic.morpher.core.compat.oculus.fabric.OculusCompatImpl.updatePBRState();
    }

    public static boolean isShaderPackInUse() {
        return com.micaftic.morpher.core.compat.oculus.fabric.OculusCompatImpl.isShaderPackInUse();
    }

    public static boolean isRenderingShadowPass() {
        return com.micaftic.morpher.core.compat.oculus.fabric.OculusCompatImpl.isRenderingShadowPass();
    }
}
