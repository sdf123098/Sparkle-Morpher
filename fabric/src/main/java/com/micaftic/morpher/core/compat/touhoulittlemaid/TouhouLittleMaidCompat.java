package com.micaftic.morpher.core.compat.touhoulittlemaid;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class TouhouLittleMaidCompat {

    private TouhouLittleMaidCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidEntity(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isYsmModel(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getYsmModelId(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getYsmModelTexture(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidRideable(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isSimplePlanesEntity(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isImmersiveAircraftEntity(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidItem(Item item) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getMaidEntityId(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidSitting(LivingEntity livingEntity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidOwnedBy(Entity entity, Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getMaidGameAnimation(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getMaidRenderAnimation(Entity entity) {
        throw new AssertionError();
    }
}
