package com.micaftic.morpher.geckolib3.core.molang.funciton.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.client.player.LocalPlayer;

public abstract class LocalPlayerEntityFunction extends ContextFunction<LocalPlayer> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LocalPlayer;
    }
}