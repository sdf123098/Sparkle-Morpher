package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.util.CameraUtil;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.Player;

public class FirstPersonModHide implements IValueEvaluator<Boolean, IContext<Player>> {
    @Override
    public Boolean eval(IContext<Player> ctx) {
        // Full-body first-person mods may invoke the player renderer from SparkleMorpher's
        // first-person render pass. The camera state, not the pass flag, decides whether the
        // local model needs its first-person hiding animation.
        return CameraUtil.getCameraType(ctx) == CameraType.FIRST_PERSON.ordinal();
    }
}
