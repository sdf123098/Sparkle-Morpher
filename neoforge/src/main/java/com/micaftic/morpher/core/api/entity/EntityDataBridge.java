package com.micaftic.morpher.core.api.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.WeakHashMap;

public final class EntityDataBridge {
    private static final Map<Entity, CompoundTag> PERSISTENT_DATA = new WeakHashMap<>();
    private EntityDataBridge() {}
    public static CompoundTag getPersistentData(Entity entity) { return PERSISTENT_DATA.computeIfAbsent(entity, k -> new CompoundTag()); }
    public static boolean shouldRiderSit(Entity vehicle) { return true; }
}