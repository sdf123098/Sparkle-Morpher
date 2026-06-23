package com.micaftic.morpher.client.event;

import net.minecraft.world.entity.LivingEntity;
import com.micaftic.morpher.core.api.entity.EntityDataBridge;

public class ShieldBlockCooldownEvent {

    public static final String TAG_KEY = "ysm$shield_block_cooldown";

    private ShieldBlockCooldownEvent() {
    }

    public static void onShieldBlock(LivingEntity entity) {
        EntityDataBridge.getPersistentData(entity).putInt(TAG_KEY, 5);
    }

    public static void onLivingTick(LivingEntity entity) {
        if (EntityDataBridge.getPersistentData(entity).contains(TAG_KEY)) {
            int i = EntityDataBridge.getPersistentData(entity).getInt(TAG_KEY).orElse(0);
            if (i > 0) {
                EntityDataBridge.getPersistentData(entity).putInt(TAG_KEY, i - 1);
            } else {
                EntityDataBridge.getPersistentData(entity).remove(TAG_KEY);
            }
        }
    }

    public static boolean isOnCooldown(LivingEntity livingEntity) {
        return EntityDataBridge.getPersistentData(livingEntity).contains(TAG_KEY);
    }
}
