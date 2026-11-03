package com.micaftic.morpher.core.compat.realcamera;


public final class RealCameraCompat {

    private RealCameraCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.realcamera.fabric.RealCameraCompatImpl.isLoaded();
    }

    public static boolean isActive() {
        return com.micaftic.morpher.core.compat.realcamera.fabric.RealCameraCompatImpl.isActive();
    }
}
