package com.micaftic.morpher.core.acceleration;

import com.micaftic.morpher.RuntimeAccelerationLoader;
import dev.spm.core.acceleration.CpuComputeProvider;

public final class AccelerationCapability {
    private AccelerationCapability() {
    }

    public static CpuComputeProvider computeProvider() {
        return NativeRuntimeSupport.computeProvider();
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
            return computeProvider().backend();
        }
        String message = RuntimeAccelerationLoader.getErrorMessage();
        return message == null || message.isBlank() ? computeProvider().backend() : message;
    }
}
