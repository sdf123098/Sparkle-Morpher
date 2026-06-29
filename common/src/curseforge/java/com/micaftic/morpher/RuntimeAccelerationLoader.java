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
        return null;
    }

    public static String getErrorMessage() {
        return null;
    }
}