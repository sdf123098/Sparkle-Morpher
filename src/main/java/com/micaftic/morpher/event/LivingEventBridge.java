package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.event.MobEffectEvent;
import com.micaftic.morpher.client.event.ShieldBlockCooldownEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class LivingEventBridge {
    private LivingEventBridge() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onMobEffectAdded);
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onMobEffectRemoved);
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onLivingShieldBlock);
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onEntityTickPost);
    }

    private static void onMobEffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (!YesSteveModel.isAvailable() || event.getEffectInstance() == null) {
            return;
        }
        MobEffectEvent.onEffectAdded(event.getEntity(), event.getEffectInstance().getEffect().value(), event.getEffectInstance().getAmplifier());
    }

    private static void onMobEffectRemoved(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove event) {
        if (!YesSteveModel.isAvailable() || event.getEffect() == null) {
            return;
        }
        MobEffectEvent.onEffectRemoved(event.getEntity(), event.getEffect().value());
    }

    private static void onLivingShieldBlock(LivingShieldBlockEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getBlocked()) {
            ShieldBlockCooldownEvent.onShieldBlock(event.getEntity());
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            ShieldBlockCooldownEvent.onLivingTick(livingEntity);
        }
    }
}
