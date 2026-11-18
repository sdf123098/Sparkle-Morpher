package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.SpmProjectileEntity;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import net.minecraft.world.entity.projectile.Projectile;

public class ProjectileAnimationPredicate implements IAnimationPredicate<SpmProjectileEntity> {

    public static final String[] ENVIRONMENT_STATES = {"water", "ground", "fly", "fire"};

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmProjectileEntity> event) {
        Projectile projectile = event.getAnimatable().getEntity();
        if (projectile == null) {
            return HostPlayState.Decision.STOP;
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
