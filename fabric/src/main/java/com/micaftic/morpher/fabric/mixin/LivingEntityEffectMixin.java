package com.micaftic.morpher.fabric.mixin;

import com.micaftic.morpher.client.event.MobEffectEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void ysm$onEffectAdded(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectEvent.onEffectAdded(self, instance.getEffect().value(), instance.getAmplifier());
    }

    @Inject(method = "onEffectRemoved", at = @At("HEAD"))
    private void ysm$onEffectRemoved(MobEffectInstance instance, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectEvent.onEffectRemoved(self, instance.getEffect().value());
    }
}
