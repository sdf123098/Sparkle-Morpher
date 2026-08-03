package com.micaftic.morpher.core.compat.touhoulittlemaid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class TouhouLittleMaidCompat {

    private TouhouLittleMaidCompat() {
    }

    public static boolean isLoaded() {
        return TouhouLittleMaidAccess.isLoaded();
    }

    public static boolean isMaidEntity(Entity entity) {
        return TouhouLittleMaidAccess.isMaid(entity);
    }

    public static boolean isMaidRideable(Entity entity) {
        return TouhouLittleMaidAccess.isMaid(entity);
    }

    public static boolean isSimplePlanesEntity(Entity entity) {
        return TouhouLittleMaidAccess.isChair(entity);
    }

    public static boolean isImmersiveAircraftEntity(Entity entity) {
        return TouhouLittleMaidAccess.isSit(entity);
    }

    public static boolean isMaidItem(Item item) {
        return TouhouLittleMaidAccess.isGohei(item);
    }

    public static String getMaidEntityId(Entity entity) {
        return TouhouLittleMaidAccess.getChairModelId(entity);
    }

    public static boolean isMaidSitting(LivingEntity livingEntity) {
        return TouhouLittleMaidAccess.hasFishingHook(livingEntity);
    }

    public static boolean isMaidOwnedBy(Entity entity, Player player) {
        return TouhouLittleMaidAccess.isOwnedBy(entity, player);
    }

    public static String getMaidGameAnimation(Entity entity) {
        return TouhouLittleMaidAccess.getGameAnimation(entity);
    }

    public static String getMaidRenderAnimation(Entity entity) {
        return TouhouLittleMaidAccess.getRenderAnimation(entity);
    }
}
