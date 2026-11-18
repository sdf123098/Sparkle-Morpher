package com.micaftic.morpher.core.compat.simpleplanes;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import org.joml.Vector3f;

import java.util.Optional;

public final class SimplePlanesCompat {

    private SimplePlanesCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.simpleplanes.fabric.SimplePlanesCompatImpl.isLoaded();
    }

    public static Optional<Vector3f> getSimplePlanesRotation(HostActionEvent<SpmVehicleEntity> event) {
        return com.micaftic.morpher.core.compat.simpleplanes.fabric.SimplePlanesCompatImpl.getSimplePlanesRotation(event);
    }
}
