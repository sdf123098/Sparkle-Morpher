package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VulkanRenderPass.class, remap = false)
public abstract class VulkanStorageDescriptorMixin {
    @Shadow protected VulkanRenderPipeline pipeline;

    @Redirect(method = "pushDescriptors", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"))
    private void sparkleMorpher$storageDescriptor(VkCommandBuffer commandBuffer, int bindPoint,
            long layout, int set, VkWriteDescriptorSet.Buffer writes) {
        if ("sparkle_morpher".equals(pipeline.info().getLocation().getNamespace())) {
            VulkanBoneStorage.patchWrites(writes,
                    pipeline.layout().entries().stream().map(VulkanBindGroupLayout.Entry::name).toList());
        }
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, bindPoint, layout, set, writes);
    }
}
