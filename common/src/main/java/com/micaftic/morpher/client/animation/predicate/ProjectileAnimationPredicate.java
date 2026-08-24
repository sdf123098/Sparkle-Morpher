package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.GeckoProjectileEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import net.minecraft.world.entity.projectile.Projectile;

public class ProjectileAnimationPredicate implements IAnimationPredicate<GeckoProjectileEntity> {

    public static final String[] ENVIRONMENT_STATES = {"water", "ground", "fly", "fire"};

    @Override
    public PlayState predicate(AnimationEvent<GeckoProjectileEntity> event, ExpressionEvaluator<?> evaluator) {
        Projectile projectile = event.getAnimatable().getEntity();
        if (projectile == null) {
            return PlayState.STOP;
        }
        if (projectile.isInWater()) {
            return IAnimationPredicate.predicate(event, "water");
        }
        if (projectile.isOnFire()) {
            return IAnimationPredicate.predicate(event, "fire");
        }
        if ((projectile instanceof ProjectileStateAccessor) && ((ProjectileStateAccessor) projectile).ysm$isInGround()) {
            return IAnimationPredicate.predicate(event, "ground");
        }
        return IAnimationPredicate.predicate(event, "air");
    }
}
