package com.micaftic.morpher.core.compat.immersiveaircraft.fabric;

import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import org.joml.Vector3f;

import java.util.Optional;

public final class ImmersiveAirCraftCompatImpl {

    private ImmersiveAirCraftCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<Vector3f> getAircraftRotation(AnimationEvent<GeckoVehicleEntity> event) {
        return Optional.empty();
    }
}
