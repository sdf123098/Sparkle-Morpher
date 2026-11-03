package com.micaftic.morpher.core.compat.simpleplanes;

import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import dev.architectury.injectables.annotations.ExpectPlatform;
import org.joml.Vector3f;

import java.util.Optional;

public final class SimplePlanesCompat {

    private SimplePlanesCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<Vector3f> getSimplePlanesRotation(AnimationEvent<GeckoVehicleEntity> event) {
        throw new AssertionError();
    }
}
