package com.micaftic.morpher.core.vulkanexp;

import com.micaftic.morpher.mixin.client.GpuDeviceAccessor;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;

final class VulkanBackendAccess {
    private VulkanBackendAccess() {
    }

    static GpuDeviceBackend deviceBackend(GpuDevice device) {
        return ((GpuDeviceAccessor) device).sparkleMorpher$getBackend();
    }

    static CommandEncoderBackend commandBackend(CommandEncoder encoder) {
        return ((FrontendCommandEncoder) encoder).backend();
    }
}
