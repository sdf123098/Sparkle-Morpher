package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.LongBuffer;

@Mixin(value = VulkanRenderPipeline.class, remap = false)
public abstract class VulkanStorageLayoutMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK12;vkCreateDescriptorSetLayout(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private static int sparkleMorpher$storageLayout(VkDevice device, VkDescriptorSetLayoutCreateInfo info,
            VkAllocationCallbacks allocator, LongBuffer result, VulkanDevice engine,
            BackendRenderPipeline.CreateInfo pipelineInfo) {
        VulkanBoneStorage.patchLayout(info, pipelineInfo.uniforms().stream()
                .map(BindGroupLayout.UniformDescription::name).toList());
        return VK12.vkCreateDescriptorSetLayout(device, info, allocator, result);
    }
}
