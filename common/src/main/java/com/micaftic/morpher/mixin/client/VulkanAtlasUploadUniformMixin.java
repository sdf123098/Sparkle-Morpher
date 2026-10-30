package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanAtlasUniforms;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextureAtlas.class)
public abstract class VulkanAtlasUploadUniformMixin {
    @Redirect(method = "uploadInitialContents", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"))
    private void sparkleMorpher$bindAtlasUploadGlobals(RenderPass pass, int count, int instances, int first, int baseInstance) {
        VulkanAtlasUniforms.bind(pass);
        pass.draw(count, instances, first, baseInstance);
    }
}
