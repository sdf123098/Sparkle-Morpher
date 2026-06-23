package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import com.micaftic.morpher.mixin.client.ArrowEntityAccessor;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;

public class EffectLevel extends ContextFunction<Entity> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }

    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int effects = 0;

        for (int i = 0; i < arguments.size(); i++) {
            Identifier effectId = arguments.getResourceLocation(context, i);
            if (effectId != null) {
                MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectId).map(ref -> ref.value()).orElse(null);
                if (mobEffect != null) {
                    Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
                    if (context.entity().geoInstance() instanceof PlayerCapability cap
                            && !cap.isLocalPlayerModel()) {
                        effects += cap.getPositionTracker().getEffectAmplifier(effectHolder);
                    } else if (((IContext<?>)context.entity()).entity() instanceof LivingEntity) {
                        MobEffectInstance mobEffectInstance = ((LivingEntity)((IContext<?>)context.entity()).entity())
                                .getEffect(effectHolder);
                        if (mobEffectInstance != null) {
                            effects += mobEffectInstance.getAmplifier() + 1;
                        }
                    } else {
                        if (!(((IContext<?>)context.entity()).entity() instanceof net.minecraft.world.entity.projectile.arrow.Arrow)) {
                            return null;
                        }

                        for (MobEffectInstance mobEffectInstance : ((ArrowEntityAccessor)((IContext<?>)context.entity()).entity())
                                .getEffects()) {
                            if (mobEffectInstance.getEffect().value() == mobEffect) {
                                effects += mobEffectInstance.getAmplifier() + 1;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return effects;
    }
}
