package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanAtlasUniforms;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 atlas animation declares Globals but omits its binding on the draw pass. */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimationState", remap = false)
public abstract class VulkanSpriteAnimationUniformMixin {
    @Inject(method = "drawToAtlas", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"))
    private void sparkleMorpher$bindAtlasGlobals(RenderPass pass, GpuBufferSlice spriteInfo, CallbackInfo ci) {
        VulkanAtlasUniforms.bind(pass);
    }
}
