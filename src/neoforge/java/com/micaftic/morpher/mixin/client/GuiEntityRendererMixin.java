package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
    // The PIP bufferSource field is declared in PictureInPictureRenderer (parent class),
    // not in GuiEntityRenderer itself. @Shadow cannot find inherited fields,
    // so we use reflection to access it.
    private static final java.lang.reflect.Field PIP_BUFFER_SOURCE_FIELD;

    static {
        try {
            PIP_BUFFER_SOURCE_FIELD = PictureInPictureRenderer.class.getDeclaredField("bufferSource");
            PIP_BUFFER_SOURCE_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("YSM: Failed to access PictureInPictureRenderer.bufferSource via reflection", e);
        }
    }

    private MultiBufferSource.BufferSource ysm$getPipBufferSource() {
        try {
            return (MultiBufferSource.BufferSource) PIP_BUFFER_SOURCE_FIELD.get(this);
        } catch (ReflectiveOperationException e) {
            // Fallback to main render buffer if reflection fails
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void ysm$renderQueuedPreview(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
        poseStack.pushPose();
        Vector3f translation = state.translation();
        poseStack.translate(translation.x, translation.y, translation.z);
        poseStack.mulPose(state.rotation());
        FeatureRenderDispatcher featureDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        if (ModelPreviewRenderer.renderQueuedGuiPreview(state.renderState(), poseStack, featureDispatcher.getSubmitNodeStorage(), ysm$getPipBufferSource())) {
            featureDispatcher.renderAllFeatures();
            poseStack.popPose();
            ModelPreviewRenderer.setPreviewMode(false);
            ci.cancel();
            return;
        }
        poseStack.popPose();
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private void ysm$clearGuiPreviewMode(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }
}
