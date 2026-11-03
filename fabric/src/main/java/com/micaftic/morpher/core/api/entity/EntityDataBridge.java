package com.micaftic.morpher.core.api.entity;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class EntityDataBridge {

    private EntityDataBridge() {
    }

    @ExpectPlatform
    public static CompoundTag getPersistentData(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean shouldRiderSit(Entity vehicle) {
        throw new AssertionError();
    }
}
