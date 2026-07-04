package com.micaftic.morpher.core.vulkanexp;

import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import org.lwjgl.vulkan.VkDevice;

public final class VulkanExperimentalCapability {
    private VulkanExperimentalCapability() {
    }

    public static Report probe() {
        try {
            GpuDevice gpuDevice = RenderSystem.getDevice();
            if (gpuDevice == null) {
                return Report.disabled("RenderSystem device is null");
            }

            GpuDeviceBackend deviceBackend = VulkanBackendAccess.deviceBackend(gpuDevice);
            if (!(deviceBackend instanceof VulkanDevice vulkanDevice)) {
                String backendClass = deviceBackend == null ? "null" : deviceBackend.getClass().getName();
                return Report.disabled("Minecraft backend is not Vulkan: " + backendClass + "; detector="
                        + SmGraphicsBackendDetector.currentBackend() + " (" + SmGraphicsBackendDetector.reason() + ")");
            }

            CommandEncoder encoder = gpuDevice.createCommandEncoder();
            CommandEncoderBackend commandBackend = VulkanBackendAccess.commandBackend(encoder);
            boolean commandBackendAvailable = commandBackend instanceof VulkanCommandEncoder;
            VkDevice vkDevice = vulkanDevice.vkDevice();
            VulkanQueue computeQueue = vulkanDevice.computeQueue();
            long vma = vulkanDevice.vma();
            boolean deviceAvailable = vkDevice != null;
            boolean computeQueueAvailable = computeQueue != null && computeQueue.vkQueue() != null;
            boolean vmaAvailable = vma != 0L;

            boolean enabled = commandBackendAvailable && deviceAvailable && computeQueueAvailable && vmaAvailable;
            String reason = enabled ? "Vulkan backend handles are visible"
                    : "Vulkan backend missing required handle(s)";
            return new Report(
                    enabled,
                    reason,
                    gpuDevice.getClass().getName(),
                    deviceBackend.getClass().getName(),
                    commandBackend == null ? "null" : commandBackend.getClass().getName(),
                    deviceAvailable,
                    computeQueueAvailable,
                    computeQueue == null ? -1 : computeQueue.queueFamilyIndex(),
                    vmaAvailable
            );
        } catch (Throwable t) {
            return Report.disabled("Vulkan capability probe failed: " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()));
        }
    }

    public record Report(
            boolean enabled,
            String reason,
            String gpuDeviceClass,
            String deviceBackendClass,
            String commandBackendClass,
            boolean vkDeviceAvailable,
            boolean computeQueueAvailable,
            int computeQueueFamilyIndex,
            boolean vmaAvailable
    ) {
        public static Report disabled(String reason) {
            return new Report(false, reason, "unknown", "unknown", "unknown", false, false, -1, false);
        }

        public String summary() {
            return "enabled=" + enabled
                    + ", reason=" + reason
                    + ", gpuDeviceClass=" + gpuDeviceClass
                    + ", deviceBackendClass=" + deviceBackendClass
                    + ", commandBackendClass=" + commandBackendClass
                    + ", vkDeviceAvailable=" + vkDeviceAvailable
                    + ", computeQueueAvailable=" + computeQueueAvailable
                    + ", computeQueueFamilyIndex=" + computeQueueFamilyIndex
                    + ", vmaAvailable=" + vmaAvailable;
        }
    }
}
