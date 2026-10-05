package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.cloud.client.CloudPlayerMotionSync;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.AbstractClientPlayerFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public class Sync extends AbstractClientPlayerFunction {
    private static final int MAX_ARGS = 16;

    @Override
    public Object eval(ExecutionContext<IContext<AbstractClientPlayer>> context, ArgumentCollection arguments) {
        if (!context.entity().isClientSide()) {
            return null;
        }
        AnimatableEntity<?> entity = context.entity().geoInstance();
        if (entity instanceof CustomPlayerEntity custom) {
            // Observers consume the owner's event, rather than generating their own random result.
            if (custom instanceof PlayerCapability && !custom.isLocalPlayerModel()
                    && CloudPlayerMotionSync.motion(custom) != null) return null;
            FloatArrayList values = collectArgs(context, arguments);
            custom.executeAnimationExpression(values);
            CloudPlayerMotionSync.expression(custom, "", values);
        }
        return null;
    }

    private static FloatArrayList collectArgs(ExecutionContext<IContext<AbstractClientPlayer>> context, ArgumentCollection arguments) {
        FloatArrayList floatArrayList = new FloatArrayList(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            floatArrayList.add(arguments.getAsFloat(context, i));
        }
        return floatArrayList;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size <= MAX_ARGS;
    }
}
