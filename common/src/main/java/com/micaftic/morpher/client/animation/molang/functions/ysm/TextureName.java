package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import net.minecraft.world.entity.player.Player;

public class TextureName implements IValueEvaluator<String, IContext<Player>> {
    @Override
    public String eval(IContext<Player> context) {
        AnimatableEntity<?> animatableEntity = context.geoInstance();
        if (animatableEntity instanceof CustomPlayerEntity) {
            return ((CustomPlayerEntity) animatableEntity).getCurrentTextureName();
        }
        return null;
    }
}