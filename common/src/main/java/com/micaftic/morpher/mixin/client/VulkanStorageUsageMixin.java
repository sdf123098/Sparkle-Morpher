package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VulkanConst.class, remap = false)
public abstract class VulkanStorageUsageMixin {
    @Inject(method = "bufferUsageToVk", at = @At("RETURN"), cancellable = true)
    private static void sparkleMorpher$storageUsage(int usage, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VulkanBoneStorage.nativeUsage(usage, cir.getReturnValue()));
    }
}
