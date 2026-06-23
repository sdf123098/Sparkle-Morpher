package com.micaftic.morpher.core.compat.simpleplanes.fabric;

import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import org.joml.Vector3f;

import java.util.Optional;

public final class SimplePlanesCompatImpl {

    private SimplePlanesCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<Vector3f> getSimplePlanesRotation(AnimationEvent<GeckoVehicleEntity> event) {
        return Optional.empty();
    }
}
