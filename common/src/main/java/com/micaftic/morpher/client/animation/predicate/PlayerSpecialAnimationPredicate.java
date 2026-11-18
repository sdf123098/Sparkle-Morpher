package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import net.minecraft.world.entity.player.Player;

public class PlayerSpecialAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<CustomPlayerEntity> event) {
        Player player = event.getAnimatable().getEntity();
        if (player == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        String str = ParcoolCompat.getActionName(player);
        if (str != null && event.getAnimatable().getAnimation(str) != null) {
            return IAnimationPredicate.predicate(event, str);
        }
        return HostPlayState.Decision.STOP;
    }
}
