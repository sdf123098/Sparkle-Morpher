package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.client.animation.molang.PhysicsManager;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;

public class SecondOrderFunction extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int name = arguments.getStringId(context, 0);
        if (name == StringPool.EMPTY_ID) {
            return 0;
        }
        float input = FirstOrderFunction.physicsArgument(context, arguments, 1);
        int size = arguments.size();
        float frequency = 1.0f;
        float coefficient = 1.0f;
        float response = 1.0f;
        if (size >= 3) {
            frequency = FirstOrderFunction.physicsArgument(context, arguments, 2);
        }
        if (size >= 4) {
            coefficient = FirstOrderFunction.physicsArgument(context, arguments, 3);
        }
        if (size >= 5) {
            response = FirstOrderFunction.physicsArgument(context, arguments, 4);
        }
        PhysicsManager physicsManager = (context.entity() instanceof com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext<?> program && program.programPhysics != null
                ? program.programPhysics : context.entity().geoInstance().getPhysicsManager());
        return physicsManager.sampleSecond(name, input, frequency, coefficient, response);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
