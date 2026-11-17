package com.micaftic.morpher;

import com.micaftic.morpher.core.acceleration.NativeRuntimeSupport;
import com.micaftic.morpher.core.architectury.platform.Platform;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class RuntimeAccelerationLoader {
    private RuntimeAccelerationLoader() {
    }

    public static void init() throws IOException {
        NativeRuntimeSupport.init(Platform::getConfigFolder);
    }

    public static boolean isAvailable() {
        return NativeRuntimeSupport.isAvailable();
    }

    public static boolean isLoaded() {
        return NativeRuntimeSupport.isLoaded();
    }

    public static boolean isOnAndroid() {
        return NativeRuntimeSupport.isOnAndroid();
    }

    public static Component getErrorComponent() {
        return NativeRuntimeSupport.getErrorComponent();
    }

    public static String getErrorMessage() {
        return NativeRuntimeSupport.getErrorMessage();
    }
}
