package com.micaftic.morpher.core.api.item;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;

public final class WeaponActionBridge {

    private WeaponActionBridge() {
    }

    @ExpectPlatform
    public static WeaponActionState get(LivingEntity entity, float partialTick) {
        throw new AssertionError();
    }
}
