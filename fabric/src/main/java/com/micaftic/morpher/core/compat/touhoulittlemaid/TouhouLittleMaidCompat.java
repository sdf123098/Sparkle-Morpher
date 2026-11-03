package com.micaftic.morpher.core.compat.touhoulittlemaid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class TouhouLittleMaidCompat {

    private TouhouLittleMaidCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isLoaded();
    }

    public static boolean isMaidEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidEntity(entity);
    }

    public static boolean isMaidRideable(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidRideable(entity);
    }

    public static boolean isSimplePlanesEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isSimplePlanesEntity(entity);
    }

    public static boolean isImmersiveAircraftEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isImmersiveAircraftEntity(entity);
    }

    public static boolean isMaidItem(Item item) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidItem(item);
    }

    public static String getMaidEntityId(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.getMaidEntityId(entity);
    }

    public static boolean isMaidSitting(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidSitting(livingEntity);
    }

    public static boolean isMaidOwnedBy(Entity entity, Player player) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidOwnedBy(entity, player);
    }

    public static String getMaidGameAnimation(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.getMaidGameAnimation(entity);
    }

    public static String getMaidRenderAnimation(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.getMaidRenderAnimation(entity);
    }
}
