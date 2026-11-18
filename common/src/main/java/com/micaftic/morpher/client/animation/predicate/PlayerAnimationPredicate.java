package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.core.compat.carryon.CarryOnDataHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public class PlayerAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<CustomPlayerEntity> event) {
        Player player = event.getAnimatable().getEntity();
        if (player == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        if (player.getPose() == Pose.SWIMMING) {
            return HostPlayState.Decision.STOP;
        }
        if (player.getPose() == Pose.FALL_FLYING && player.isFallFlying()) {
            return HostPlayState.Decision.STOP;
        }
        switch (CarryOnDataHelper.getCarryType(player)) {
            case ENTITY:
                return IAnimationPredicate.playLoopAnimation(event, "carryon:entity");
            case BLOCK:
                return IAnimationPredicate.playLoopAnimation(event, "carryon:block");
            case PLAYER:
                return IAnimationPredicate.playLoopAnimation(event, "carryon:player");
        }
        return HostPlayState.Decision.STOP;
    }
}
