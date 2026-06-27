package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.geckolib3.extended.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingRendererMixin extends EntityRenderer<LivingEntity, EntityRenderState> implements LivingEntityRendererAccessor {
    public LivingRendererMixin(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    @Unique
    public void tlm$renderNameTag(LivingEntity pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        // Suppress name tags during GUI preview rendering (model selection tab, inventory preview, etc.)
        // DummyPlayer entities have GameProfile names like "ysm_dab_404c_3..." which would appear
        // as text on model heads if not suppressed. NeoForge's event patches handle this,
        // but Fabric's vanilla shouldShowName may still return true for these entities.
        if (ModelPreviewRenderer.isPreview()) {
            return;
        }
        double distSq = this.entityRenderDispatcher.distanceToSqr(pEntity);
        if (this.shouldShowName(pEntity, distSq)) {
            SubmitNodeCollector collector = SubmitRenderContext.get();
            if (collector != null) {
                CameraRenderState cameraState = new CameraRenderState();
                Minecraft mc = Minecraft.getInstance();
                cameraState.pos = mc.gameRenderer.mainCamera().position();
                cameraState.orientation.set(mc.gameRenderer.mainCamera().rotation());
                EntityRenderState renderState = this.createRenderState();
                this.extractRenderState(pEntity, renderState, pPartialTick);
                pPoseStack.pushPose();
                this.submitNameDisplay(renderState, pPoseStack, collector, cameraState);
                pPoseStack.popPose();
            }
        }
    }
}
