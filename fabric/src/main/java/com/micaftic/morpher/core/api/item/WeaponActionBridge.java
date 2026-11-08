package com.micaftic.morpher.core.api.item;

import net.minecraft.world.entity.LivingEntity;

public final class WeaponActionBridge {

    private WeaponActionBridge() {
    }

    public static WeaponActionState get(LivingEntity entity, float partialTick) {
        return com.micaftic.morpher.core.api.item.fabric.WeaponActionBridgeImpl.get(entity, partialTick);
    }
}
