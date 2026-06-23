package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.fabric.YsmComponents;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class VehicleModelCapabilityImpl {

    private VehicleModelCapabilityImpl() {
    }

    public static Optional<VehicleModelCapability> get(Entity entity) {
        VehicleModelComponent component = YsmComponents.VEHICLE_MODEL.getNullable(entity);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }
}
