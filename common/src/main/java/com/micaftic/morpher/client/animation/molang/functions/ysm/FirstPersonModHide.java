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
        if (!ctx.animationEvent().isFirstPerson() && FirstPersonCompat.isLoaded() && CameraUtil.getCameraType(ctx) == CameraType.FIRST_PERSON.ordinal()) {
            return FirstPersonCompat.shouldHideHead();
        }
        return false;
    }
}