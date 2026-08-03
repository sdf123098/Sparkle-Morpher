package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.network.message.FeedbackData;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class TouhouMaidCompat {

    private TouhouMaidCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidEntity(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void handleProjectileOwner(Projectile projectile, Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerAnimationRoulette(Entity entity, String str, int i) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void applyFeedback(Entity entity, FeedbackData message) {
        throw new AssertionError();
    }

}
