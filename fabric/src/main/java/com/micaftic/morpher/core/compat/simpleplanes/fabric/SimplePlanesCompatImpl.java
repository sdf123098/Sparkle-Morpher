package com.micaftic.morpher.core.compat.simpleplanes.fabric;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import org.joml.Vector3f;

import java.util.Optional;

public final class SimplePlanesCompatImpl {

    private SimplePlanesCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<Vector3f> getSimplePlanesRotation(HostActionEvent<SpmVehicleEntity> event) {
        return Optional.empty();
    }
}
