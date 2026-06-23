package com.micaftic.morpher.core.compat.simpleplanes;

import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import org.joml.Vector3f;

import java.util.Optional;

public final class SimplePlanesCompat {

    private SimplePlanesCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.simpleplanes.fabric.SimplePlanesCompatImpl.isLoaded();
    }

    public static Optional<Vector3f> getSimplePlanesRotation(AnimationEvent<GeckoVehicleEntity> event) {
        return com.micaftic.morpher.core.compat.simpleplanes.fabric.SimplePlanesCompatImpl.getSimplePlanesRotation(event);
    }
}
