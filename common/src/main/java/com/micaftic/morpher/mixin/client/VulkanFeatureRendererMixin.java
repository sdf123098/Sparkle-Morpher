package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanModelFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class VulkanFeatureRendererMixin {
    @Shadow @Final private FeatureRendererMap featureRenderers;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void sparkleMorpher$registerSkinning(CallbackInfo ci) {
        featureRenderers.put(VulkanModelFeatureRenderer.TYPE, new VulkanModelFeatureRenderer());
    }
}
