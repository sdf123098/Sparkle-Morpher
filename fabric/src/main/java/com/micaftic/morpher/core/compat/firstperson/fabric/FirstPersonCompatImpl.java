package com.micaftic.morpher.core.compat.firstperson.fabric;

public final class FirstPersonCompatImpl {

    private FirstPersonCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isFirstPersonActive() {
        return false;
    }

    public static boolean shouldHideHead() {
        return false;
    }

    public static void setCameraDistance(float distance) {
    }
}
