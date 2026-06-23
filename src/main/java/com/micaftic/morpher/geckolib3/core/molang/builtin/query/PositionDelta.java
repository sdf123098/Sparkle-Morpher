package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.geckolib3.util.MovementQuery;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class PositionDelta extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int value = arguments.getAsInt(context, 0);
        IContext<Entity> entityContext = context.entity();
        Vec3 vec3 = MovementQuery.getPositionDelta(entityContext.entity(), entityContext.geoInstance().getPositionTracker());
        switch (value) {
            case 0:
                return vec3.x;
            case 1:
                return vec3.y;
            case 2:
                return vec3.z;
            default:
                return null;
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
