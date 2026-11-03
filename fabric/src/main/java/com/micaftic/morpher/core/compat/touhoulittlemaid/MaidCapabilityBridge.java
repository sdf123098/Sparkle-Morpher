package com.micaftic.morpher.core.compat.touhoulittlemaid;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class MaidCapabilityBridge {

    private MaidCapabilityBridge() {
    }

    @ExpectPlatform
    public static Optional<Object> get(Entity entity) {
        throw new AssertionError();
    }
}
