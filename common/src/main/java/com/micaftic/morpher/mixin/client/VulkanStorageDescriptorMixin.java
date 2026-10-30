package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
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
        for (var uniform : pipeline.uniforms()) {
            if (VulkanBoneStorage.NAME.equals(uniform.name())) {
                VulkanBoneStorage.patchWrites(writes, pipeline.uniforms().stream()
                        .map(BindGroupLayout.UniformDescription::name).toList());
                break;
            }
        }
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, bindPoint, layout, set, writes);
    }
}
