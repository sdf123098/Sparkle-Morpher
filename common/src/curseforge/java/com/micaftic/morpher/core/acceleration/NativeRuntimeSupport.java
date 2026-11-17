package com.micaftic.morpher.core.acceleration;

import dev.spm.core.acceleration.CpuComputeProvider;
import dev.spm.core.render.cpu.JavaComputeProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * CurseForge variant of runtime acceleration support: always uses pure-Java {@link JavaComputeProvider}.
 */
public final class NativeRuntimeSupport {
    private static final CpuComputeProvider JAVA_PROVIDER = new JavaComputeProvider();

    private NativeRuntimeSupport() {
    }

    public static void init(Supplier<Path> configDirSupplier) {
    }

    public static CpuComputeProvider computeProvider() {
        return JAVA_PROVIDER;
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

    public static @Nullable Component getErrorComponent() {
        return Component.literal("Native SIMD is unavailable in the CurseForge distribution.");
    }

    public static String getErrorMessage() {
        return "[SM] Native SIMD unavailable: CurseForge distribution excludes native libraries";
    }
}
