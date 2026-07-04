package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public class RotationToCamera extends ContextFunction<Object> {
    @Override
    public Object eval(ExecutionContext<IContext<Object>> context, ArgumentCollection arguments) {
        int args = arguments.getAsInt(context, 0);
        if (args < 0 || args > 1) {
            return null;
        }
        Camera mainCamera = Minecraft.getInstance().gameRenderer.mainCamera();
        if (args == 0) {
            return mainCamera.xRot();
        }
        return mainCamera.yRot();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
