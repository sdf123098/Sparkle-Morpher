package com.micaftic.morpher.core.compat.touhoulittlemaid;

import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class MaidCapabilityBridge {

    private MaidCapabilityBridge() {
    }

    public static Optional<Object> get(Entity entity) {
        return MaidCapability.get(entity).map(capability -> capability);
    }
}
