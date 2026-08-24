package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.core.api.entity.EntityDataBridge;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public final class ServerLivingEventHooks {
    public static final String SHIELD_BLOCK_COOLDOWN_TAG = "ysm$shield_block_cooldown";

    private ServerLivingEventHooks() {}

    public static void onEffectAdded(LivingEntity entity, MobEffect effect, int amplifier) {
        if (!YesSteveModel.isAvailable() || entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer serverPlayer && effect != null) {
            ModelInfoCapability.get(serverPlayer).ifPresent(cap ->
                    cap.getAnimSync().syncEffectAdded(serverPlayer, Holder.direct(effect), amplifier + 1));
        }
    }

    public static void onEffectRemoved(LivingEntity entity, MobEffect effect) {
        if (!YesSteveModel.isAvailable() || entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer serverPlayer && effect != null) {
            ModelInfoCapability.get(serverPlayer).ifPresent(cap ->
                    cap.getAnimSync().syncEffectRemoved(serverPlayer, Holder.direct(effect)));
        }
    }

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
