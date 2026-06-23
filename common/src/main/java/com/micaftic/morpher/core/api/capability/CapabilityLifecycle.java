package com.micaftic.morpher.core.api.capability;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;

public final class CapabilityLifecycle {

    private CapabilityLifecycle() {
    }

    @ExpectPlatform
    public static void revive(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void invalidate(Entity entity) {
        throw new AssertionError();
    }
}
