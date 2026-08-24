package com.micaftic.morpher.core.acceleration;

import com.micaftic.morpher.RuntimeAccelerationLoader;

public final class AccelerationCapability {
    private AccelerationCapability() {
    }

    public static boolean isLoaded() {
        return RuntimeAccelerationLoader.isLoaded();
    }

    public static boolean canBuildGpuMesh() {
        return true;
    }

    public static boolean canRenderSimd() {
        return isLoaded();
    }

    public static String getReason() {
        if (isLoaded()) {
            return "ok";
        }
        String message = RuntimeAccelerationLoader.getErrorMessage();
        return message == null || message.isBlank() ? "native ysm-core not loaded" : message;
    }
}
