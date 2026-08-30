package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface HeldItemLayer {
    void renderGltfThirdPersonItem(LivingEntity livingEntity, ItemStack itemStack,
                                   HumanoidArm humanoidArm, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight, float partialTick);
}
