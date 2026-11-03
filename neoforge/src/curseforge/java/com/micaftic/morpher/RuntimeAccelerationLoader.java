package com.micaftic.morpher;

import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class RuntimeAccelerationLoader {
    private RuntimeAccelerationLoader() {
    }

    public static void init() throws IOException {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isOnAndroid() {
        return false;
    }

    public static Component getErrorComponent() {
        return Component.literal("Native SIMD is unavailable in the CurseForge distribution.");
    }

    public static String getErrorMessage() {
        return "[SM] Native SIMD unavailable: CurseForge distribution excludes native libraries";
    }
}
