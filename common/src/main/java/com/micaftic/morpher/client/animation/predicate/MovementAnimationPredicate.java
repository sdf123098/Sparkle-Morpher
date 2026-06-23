package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class MovementAnimationPredicate implements IAnimationPredicate<GeckoVehicleEntity> {

    public static final String[] ANIMATION_NAMES = {"forward", "idle"};

    @Override
    public PlayState predicate(AnimationEvent<GeckoVehicleEntity> event, ExpressionEvaluator<?> evaluator) {
        Entity entity = event.getAnimatable().getEntity();
        if (entity == null) {
            return PlayState.STOP;
        }
        Vec3 deltaMovement = entity.getDeltaMovement();
        if (Math.sqrt((deltaMovement.x * deltaMovement.x) + (deltaMovement.z * deltaMovement.z)) > 0.05d) {
            return IAnimationPredicate.predicate(event, "forward");
        }
        return IAnimationPredicate.predicate(event, "idle");
    }
}