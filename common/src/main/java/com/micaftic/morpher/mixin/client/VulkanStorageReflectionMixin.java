package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.util.List;

@Mixin(value = IntermediaryShaderModule.class, remap = false)
public abstract class VulkanStorageReflectionMixin {
    @Inject(method = "createFromSpirv", at = @At("RETURN"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sparkleMorpher$reflectStorage(String filename, ByteBuffer spirv,
            CallbackInfoReturnable<IntermediaryShaderModule> cir) {
        if (!filename.contains("sparkle_morpher") || !filename.contains("blaze3d_bone_skin")) return;
        var module = cir.getReturnValue();
        int offset = VulkanBoneStorage.storageBindingOffset(spirv);
        if (offset >= 0) {
            try {
                // Mojang's package-private record is the compiler's binding-offset carrier.
                // Resolve it only on the Vulkan shader-compile path, never during a draw.
                Constructor<?> constructor = Class.forName("com.mojang.blaze3d.vulkan.glsl.SpvUniformBuffer")
                        .getDeclaredConstructor(String.class, int.class);
                constructor.setAccessible(true);
                ((List) module.uniformBuffers()).add(constructor.newInstance(VulkanBoneStorage.NAME, offset));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot extend Vulkan storage-buffer reflection", e);
            }
        }
    }
}
