package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public class RelativeBlockName extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        Identifier key;
        BlockState blockState = MolangUtils.getRelativeBlockState(context, arguments);
        if (blockState == null || (key = BuiltInRegistries.BLOCK.getKey(blockState.getBlock())) == null) {
            return null;
        }
        return key.toString();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}
