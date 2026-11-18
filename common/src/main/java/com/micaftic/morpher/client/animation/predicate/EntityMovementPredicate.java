package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import net.minecraft.world.entity.Entity;

public class EntityMovementPredicate implements IAnimationPredicate<SpmVehicleEntity> {

    public static final String[] MOVEMENT_STATES = {"water", "ground", "fly"};

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmVehicleEntity> event) {
        Entity entity = event.getAnimatable().getEntity();
        if (entity == null) {
            return HostPlayState.Decision.STOP;
        }
        if (entity.isInWater()) {
            return IAnimationPredicate.predicate(event, "water");
        }
        if (entity.onGround()) {
            return IAnimationPredicate.predicate(event, "ground");
        }
        return IAnimationPredicate.predicate(event, "fly");
    }
}
