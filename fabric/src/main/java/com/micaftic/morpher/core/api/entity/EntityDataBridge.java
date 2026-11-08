package com.micaftic.morpher.core.api.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class EntityDataBridge {

    private EntityDataBridge() {
    }

    public static CompoundTag getPersistentData(Entity entity) {
        return com.micaftic.morpher.core.api.entity.fabric.EntityDataBridgeImpl.getPersistentData(entity);
    }

    public static boolean shouldRiderSit(Entity vehicle) {
        return com.micaftic.morpher.core.api.entity.fabric.EntityDataBridgeImpl.shouldRiderSit(vehicle);
    }
}
