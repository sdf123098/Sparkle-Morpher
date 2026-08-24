package com.micaftic.morpher.core.compat.ironsspellbooks;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import net.minecraft.world.entity.LivingEntity;

public final class SpellbooksCompat {

    private SpellbooksCompat() {
    }

    public static boolean isLoaded() { return false;
    }

    public static void registerBindings(CtrlBinding binding) {
    }

    public static PlayState resolvePlayState(AnimationEvent<LivingAnimatable<?>> event, LivingEntity entity) {
        return null;
    }
}
