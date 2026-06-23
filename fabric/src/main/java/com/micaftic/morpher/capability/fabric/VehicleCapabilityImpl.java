package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.VehicleCapability;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import com.micaftic.morpher.capability.fabric.client.VehicleCapabilityClientStore;

public final class VehicleCapabilityImpl {

    private VehicleCapabilityImpl() {
    }

    public static Optional<VehicleCapability> get(Entity entity) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || !entity.level().isClientSide()) {
            return Optional.empty();
        }
        return VehicleCapabilityClientStore.get(entity);
    }
}
