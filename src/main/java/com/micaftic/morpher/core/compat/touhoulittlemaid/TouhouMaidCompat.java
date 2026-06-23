package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.network.message.FeedbackData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class TouhouMaidCompat {

    private TouhouMaidCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void init() {
    }

    public static boolean isMaidEntity(Entity entity) {
        return false;
    }

    public static void handleProjectileOwner(Projectile projectile, Entity entity) {
    }

    public static void registerAnimationRoulette(Entity entity, String str, int i) {
    }

    public static void applyFeedback(Entity entity, FeedbackData message) {
    }
    public static void playMaidAnimation(Entity entity, String str) {
    }
}
