package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class MaidCapabilityBridgeImpl {

    private MaidCapabilityBridgeImpl() {
    }

    public static Optional<Object> get(Entity entity) {
        return MaidCapability.get(entity).map(capability -> capability);
    }
}
