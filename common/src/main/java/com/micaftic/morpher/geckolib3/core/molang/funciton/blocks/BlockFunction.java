package com.micaftic.morpher.geckolib3.core.molang.funciton.blocks;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.world.level.block.Block;

public abstract class BlockFunction extends ContextFunction<Block> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Block;
    }
}