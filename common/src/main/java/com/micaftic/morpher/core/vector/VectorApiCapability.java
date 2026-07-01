package com.micaftic.morpher.core.vector;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.log.ChatLogger;
import net.minecraft.network.chat.Component;

public final class VectorApiCapability {
    public static final String JVM_ARGUMENT = "--add-modules jdk.incubator.vector";
    private static final boolean AVAILABLE = detectAvailable();
    private static final String REASON = AVAILABLE ? "available" : "jdk.incubator.vector module is not available; add JVM argument: " + JVM_ARGUMENT;
    private static boolean warnedUnavailable;

    private VectorApiCapability() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String getReason() {
        return REASON;
    }

    public static void warnIfRequested(boolean requested) {
        if (!requested || AVAILABLE || warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        YesSteveModel.LOGGER.warn("Java Vector renderer is enabled but unavailable. Add JVM argument: {}", JVM_ARGUMENT);
        ChatLogger.INSTANCE.logComponent(Component.literal("Java Vector renderer needs JVM argument: " + JVM_ARGUMENT));
    }

    private static boolean detectAvailable() {
        try {
            Class.forName("jdk.incubator.vector.FloatVector", false, VectorApiCapability.class.getClassLoader());
            return JdkVectorModelMath.isAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
