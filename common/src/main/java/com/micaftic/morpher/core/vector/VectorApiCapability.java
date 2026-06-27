package com.micaftic.morpher.core.vector;

public final class VectorApiCapability {
    private static final boolean AVAILABLE = detectAvailable();
    private static final String REASON = AVAILABLE ? "available" : "jdk.incubator.vector module is not available; launch with --add-modules jdk.incubator.vector";

    private VectorApiCapability() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String getReason() {
        return REASON;
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
