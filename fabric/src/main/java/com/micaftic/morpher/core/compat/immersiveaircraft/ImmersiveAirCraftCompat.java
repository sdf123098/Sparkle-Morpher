package com.micaftic.morpher.core.compat.immersiveaircraft;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import org.joml.Vector3f;

import java.util.Optional;

public final class ImmersiveAirCraftCompat {

    private ImmersiveAirCraftCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.immersiveaircraft.fabric.ImmersiveAirCraftCompatImpl.isLoaded();
    }

    public static Optional<Vector3f> getAircraftRotation(HostActionEvent<SpmVehicleEntity> event) {
        return com.micaftic.morpher.core.compat.immersiveaircraft.fabric.ImmersiveAirCraftCompatImpl.getAircraftRotation(event);
    }
}
