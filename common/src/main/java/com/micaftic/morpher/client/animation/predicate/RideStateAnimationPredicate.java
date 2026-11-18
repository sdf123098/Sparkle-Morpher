package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import net.minecraft.world.entity.Entity;

public class RideStateAnimationPredicate implements IAnimationPredicate<SpmVehicleEntity> {

    public static final String[] ANIMATION_NAMES = {"has_ride", "not_ride"};

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmVehicleEntity> event) {
        Entity entity = event.getAnimatable().getEntity();
        if (entity == null) {
            return HostPlayState.Decision.STOP;
        }
        if (!entity.getPassengers().isEmpty()) {
            return IAnimationPredicate.predicate(event, "has_ride");
        }
        return IAnimationPredicate.predicate(event, "not_ride");
    }
}
