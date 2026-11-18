package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class MovementAnimationPredicate implements IAnimationPredicate<SpmVehicleEntity> {

    public static final String[] ANIMATION_NAMES = {"forward", "idle"};

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmVehicleEntity> event) {
        Entity entity = event.getAnimatable().getEntity();
        if (entity == null) {
            return HostPlayState.Decision.STOP;
        }
        Vec3 deltaMovement = entity.getDeltaMovement();
        if (Math.sqrt((deltaMovement.x * deltaMovement.x) + (deltaMovement.z * deltaMovement.z)) > 0.05d) {
            return IAnimationPredicate.predicate(event, "forward");
        }
        return IAnimationPredicate.predicate(event, "idle");
    }
}
