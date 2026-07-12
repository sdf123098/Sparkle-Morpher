package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.util.CameraUtil;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.Player;

public class FirstPersonModHide implements IValueEvaluator<Boolean, IContext<Player>> {
    @Override
    public Boolean eval(IContext<Player> ctx) {
        if (!ctx.animationEvent().isFirstPerson() && CameraUtil.getCameraType(ctx) == CameraType.FIRST_PERSON.ordinal()) {
            if (FirstPersonCompat.isLoaded()) {
                return FirstPersonCompat.shouldHideHead();
            }
            // No external first-person model mod is managing head visibility, but SparkleMorpher
            // still renders the local player's body in first person itself - hide the head so the
            // camera does not end up inside it (fixes "see inside the head" on instances without a
            // first-person mod).
            return true;
        }
        return false;
    }
}