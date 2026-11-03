package com.micaftic.morpher.core.vulkanexp;

import com.micaftic.morpher.mixin.client.CommandEncoderAccessor;
import com.micaftic.morpher.mixin.client.GpuDeviceAccessor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;

final class VulkanBackendAccess {
    private VulkanBackendAccess() {
    }

    static GpuDeviceBackend deviceBackend(GpuDevice device) {
        return ((GpuDeviceAccessor) device).sparkleMorpher$getBackend();
    }

    static CommandEncoderBackend commandBackend(CommandEncoder encoder) {
        return ((CommandEncoderAccessor) encoder).sparkleMorpher$backend();
    }
}
