package com.micaftic.morpher.geckolib3.core.molang.variable.block;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockBehaviorVariable extends LambdaVariable<BlockBehaviour> {
    public BlockBehaviorVariable(IValueEvaluator<?, IContext<BlockBehaviour>> valueEvaluator) {
        super(valueEvaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof BlockBehaviour;
    }
}