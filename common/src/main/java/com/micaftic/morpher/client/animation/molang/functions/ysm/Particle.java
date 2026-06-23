package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.util.ParticleEffectUtil;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ExecutionException;

public class Particle extends EntityFunction {

    private final boolean abs;

    public Particle(boolean abs) {
        this.abs = abs;
    }

    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        if (!context.entity().isClientSide() || context.entity().geoInstance().hasCustomTexture()) {
            return null;
        }
        try {
            return ParticleEffectUtil.handleParticle(context, arguments, this.abs);
        } catch (ExecutionException | CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }
}