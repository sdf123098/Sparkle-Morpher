package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.ModelInfoCapability;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public class MobEffectEvent {

    private MobEffectEvent() {
    }

    public static void onEffectAdded(LivingEntity entity, MobEffect effect, int amplifier) {
        if (!YesSteveModel.isAvailable() || entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer serverPlayer && effect != null) {
            ModelInfoCapability.get(serverPlayer).ifPresent(cap ->
                    cap.getAnimSync().syncEffectAdded(serverPlayer, new Holder.Direct<>(effect), amplifier + 1));
        }
    }

    public static void onEffectRemoved(LivingEntity entity, MobEffect effect) {
        if (!YesSteveModel.isAvailable() || entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer serverPlayer && effect != null) {
            ModelInfoCapability.get(serverPlayer).ifPresent(cap ->
                    cap.getAnimSync().syncEffectRemoved(serverPlayer, new Holder.Direct<>(effect)));
        }
    }
}
