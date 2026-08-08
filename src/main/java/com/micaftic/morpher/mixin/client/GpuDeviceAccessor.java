package com.micaftic.morpher.mixin.client;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GpuDevice.class)
public interface GpuDeviceAccessor {
    @Accessor("backend")
    GpuDeviceBackend sparkleMorpher$getBackend();
}
