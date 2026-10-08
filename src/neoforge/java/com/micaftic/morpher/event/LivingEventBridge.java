package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class LivingEventBridge {
    private LivingEventBridge() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onLivingShieldBlock);
        NeoForge.EVENT_BUS.addListener(LivingEventBridge::onEntityTickPost);
    }

    private static void onLivingShieldBlock(LivingShieldBlockEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getBlocked()) {
            ServerLivingEventHooks.onShieldBlock(event.getEntity());
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            ServerLivingEventHooks.onLivingTick(livingEntity);
        }
    }
}
