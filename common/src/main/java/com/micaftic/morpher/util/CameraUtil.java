package com.micaftic.morpher.util;

import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class CameraUtil {

    private CameraUtil() {
    }

    public static int getCameraType(HostAnimatable<?> animatable) {
        return getCameraType(animatable != null ? animatable.getEntity() : null);
    }

    public static int getCameraType(Entity entity) {
        if (entity != null
                && entity == Minecraft.getInstance().player
                && entity == Minecraft.getInstance().getCameraEntity()
                && hasCameraContext()) {
            return Minecraft.getInstance().options.getCameraType().ordinal();
        }
        return CameraType.THIRD_PERSON_FRONT.ordinal();
    }

    public static boolean isFirstPerson(HostAnimatable<? extends Entity> animatable) {
        return animatable.getEntity() == Minecraft.getInstance().player
                && animatable.getEntity() == Minecraft.getInstance().getCameraEntity()
                && hasCameraContext()
                && !OculusCompat.isPBRActive()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static boolean hasCameraContext() {
        if (RenderContext.isAnyPreview() || RenderContext.isPaperDoll()) {
            return false;
        }
        return ModelPreviewRenderer.isWorldRender() || ModelPreviewRenderer.isFirstPerson();
    }

    public static boolean isThirdPersonModel(HostAnimatable<?> model) {
        return (model instanceof IPreviewAnimatable) || ModelPreviewRenderer.isPreview();
    }
}
