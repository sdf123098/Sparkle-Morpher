package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.player.Player;

public class PlayerSpecialAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public PlayState predicate(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
        Player player = event.getAnimatable().getEntity();
        if (player == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return null;
        }
        String str = ParcoolCompat.getActionName(player);
        if (str != null && event.getAnimatable().getAnimation(str) != null) {
            return IAnimationPredicate.predicate(event, str);
        }
        return PlayState.STOP;
    }
}