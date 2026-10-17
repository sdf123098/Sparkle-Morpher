package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.client.animation.molang.PhysicsManager;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;

public class FirstOrderFunction extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int name = arguments.getStringId(context, 0);
        if (name == StringPool.EMPTY_ID) {
            return 0;
        }
        float input = physicsArgument(context, arguments, 1);
        float response = 1.0f;
        if (arguments.size() >= 3) {
            response = physicsArgument(context, arguments, 2);
        }
        PhysicsManager physicsManager = (context.entity() instanceof com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext<?> program && program.programPhysics != null
                ? program.programPhysics : context.entity().geoInstance().getPhysicsManager());
        return physicsManager.sampleFirst(name, input, response);
    }

    static float physicsArgument(ExecutionContext<?> context, ArgumentCollection arguments, int index) {
        long before = com.micaftic.morpher.client.animation.molang.EvaluationHealth.sequence();
        Object value = arguments.getValue(context,index);
        if(value instanceof Number number && !Float.isFinite(number.floatValue())) return Float.NaN;
        float result = com.micaftic.morpher.molang.runtime.binding.ValueConversions.asFloat(value);
        return before == com.micaftic.morpher.client.animation.molang.EvaluationHealth.sequence() ? result : Float.NaN;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
