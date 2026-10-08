package com.micaftic.morpher.event;

import com.micaftic.morpher.core.api.entity.EntityDataBridge;
import net.minecraft.world.entity.LivingEntity;

public final class ServerLivingEventHooks {
    public static final String SHIELD_BLOCK_COOLDOWN_TAG = "ysm$shield_block_cooldown";

    private ServerLivingEventHooks() {}

    public static void onShieldBlock(LivingEntity entity) {
        EntityDataBridge.getPersistentData(entity).putInt(SHIELD_BLOCK_COOLDOWN_TAG, 5);
    }

    public static void onLivingTick(LivingEntity entity) {
        if (EntityDataBridge.getPersistentData(entity).contains(SHIELD_BLOCK_COOLDOWN_TAG)) {
        int ticks = EntityDataBridge.getPersistentData(entity).getInt(SHIELD_BLOCK_COOLDOWN_TAG);
            if (ticks > 0) {
                EntityDataBridge.getPersistentData(entity).putInt(SHIELD_BLOCK_COOLDOWN_TAG, ticks - 1);
            } else {
                EntityDataBridge.getPersistentData(entity).remove(SHIELD_BLOCK_COOLDOWN_TAG);
            }
        }
    }

    public static boolean isShieldBlockOnCooldown(LivingEntity livingEntity) {
        return EntityDataBridge.getPersistentData(livingEntity).contains(SHIELD_BLOCK_COOLDOWN_TAG);
    }
}
