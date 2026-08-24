package com.micaftic.morpher.core.api.capability;

import net.minecraft.world.entity.Entity;

public final class CapabilityLifecycle {
    private CapabilityLifecycle() {}
    public static void revive(Entity entity) {}
    public static void invalidate(Entity entity) {}
}