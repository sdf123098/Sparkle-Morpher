package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.core.HostAnimatable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

public abstract class SpmLayerRenderer<T extends HostAnimatable<?>> {
    public abstract void render(
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            int packedLightIn,
            T entityLivingBaseIn,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch);
}
