package com.micaftic.morpher.core.vector;

public final class VectorApiCapability {
    private static final boolean AVAILABLE = detectAvailable();
    private static final String REASON = AVAILABLE ? "available" : "optional Java Vector acceleration disabled; scalar Java model math will be used";

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
