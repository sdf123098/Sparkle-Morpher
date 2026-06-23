package com.micaftic.morpher.util;

import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class CameraUtil {
    public static int getCameraType(IContext<? extends Entity> IContext) {
        if (IContext.entity() == Minecraft.getInstance().player && hasCameraContext()) {
            return IContext.mc().options.getCameraType().ordinal();
        }
        return CameraType.THIRD_PERSON_FRONT.ordinal();
    }

    public static boolean isFirstPerson(AnimatableEntity<? extends Entity> animatableEntity) {
        return animatableEntity.getEntity() == Minecraft.getInstance().player
                && hasCameraContext() && !OculusCompat.isPBRActive() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static boolean hasCameraContext() {
        return ModelPreviewRenderer.isWorldRender() || ModelPreviewRenderer.isFirstPerson();
    }

    public static boolean isThirdPerson(IContext<? extends Entity> IContext) {
        return isThirdPersonModel(IContext.geoInstance());
    }

    public static boolean isThirdPersonModel(AnimatableEntity<?> model) {
        return (model instanceof IPreviewAnimatable) || ModelPreviewRenderer.isPreview();
    }
}
