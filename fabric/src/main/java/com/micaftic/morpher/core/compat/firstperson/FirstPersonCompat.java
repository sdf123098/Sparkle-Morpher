package com.micaftic.morpher.core.compat.firstperson;


public final class FirstPersonCompat {

    private FirstPersonCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.firstperson.fabric.FirstPersonCompatImpl.isLoaded();
    }

    public static boolean isFirstPersonActive() {
        return com.micaftic.morpher.core.compat.firstperson.fabric.FirstPersonCompatImpl.isFirstPersonActive();
    }

    public static boolean shouldHideHead() {
        return com.micaftic.morpher.core.compat.firstperson.fabric.FirstPersonCompatImpl.shouldHideHead();
    }

    public static void setCameraDistance(float distance) {
        com.micaftic.morpher.core.compat.firstperson.fabric.FirstPersonCompatImpl.setCameraDistance(distance);
    }
}
