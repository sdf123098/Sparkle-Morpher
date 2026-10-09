package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import com.micaftic.morpher.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"), cancellable = true)
    private void ysm$renderQueuedPreview(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
        poseStack.pushPose();
        Vector3fc translation = state.translation();
        poseStack.translate(translation.x(), translation.y(), translation.z());
        poseStack.rotate(state.rotation());
        if (!(collector instanceof SubmitNodeStorage submitNodeStorage)) {
            poseStack.popPose();
            return;
        }
        if (ModelPreviewRenderer.renderQueuedGuiPreview(state.renderState(), poseStack, submitNodeStorage, null)) {
            poseStack.popPose();
            ModelPreviewRenderer.setPreviewMode(false);
            ci.cancel();
            return;
        }
        poseStack.popPose();
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("RETURN"))
    private void ysm$clearGuiPreviewMode(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }
}
