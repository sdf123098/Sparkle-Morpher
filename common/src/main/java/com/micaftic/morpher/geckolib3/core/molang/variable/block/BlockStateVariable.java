package com.micaftic.morpher.geckolib3.core.molang.variable.block;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.level.block.state.BlockState;

public class BlockStateVariable extends LambdaVariable<BlockState> {
    public BlockStateVariable(IValueEvaluator<?, IContext<BlockState>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof BlockState;
    }
}