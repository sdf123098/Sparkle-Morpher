package com.micaftic.morpher.core.api.camera;

/** Stable camera snapshot consumed by render/runtime code. */
public final class CameraApi {
    private CameraApi() { }
    public static CameraState snapshot(float yaw, float pitch, boolean firstPerson) { return new CameraState(yaw, pitch, firstPerson); }
    public record CameraState(float yaw, float pitch, boolean firstPerson) { }
}
