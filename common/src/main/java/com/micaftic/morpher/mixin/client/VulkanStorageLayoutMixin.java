package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.LongBuffer;
import java.util.List;

@Mixin(value = VulkanBindGroupLayout.class, remap = false)
public abstract class VulkanStorageLayoutMixin {
    @Redirect(method = "create", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK12;vkCreateDescriptorSetLayout(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private static int sparkleMorpher$storageLayout(VkDevice device, VkDescriptorSetLayoutCreateInfo info,
            VkAllocationCallbacks allocator, LongBuffer result, VulkanDevice engine,
            List<VulkanBindGroupLayout.Entry> entries, String name) {
        VulkanBoneStorage.patchLayout(info, entries.stream().map(VulkanBindGroupLayout.Entry::name).toList());
        return VK12.vkCreateDescriptorSetLayout(device, info, allocator, result);
    }
}
