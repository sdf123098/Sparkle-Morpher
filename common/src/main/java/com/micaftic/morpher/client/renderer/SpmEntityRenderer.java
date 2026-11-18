package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.animation.particle.AnimationParticleRuntime;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.entity.SpmEntity;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public abstract class SpmEntityRenderer<TEntity extends Entity, T extends HostAnimatable<TEntity>>
        extends EntityRenderer<TEntity, EntityRenderState> implements ISpmRenderer<T> {

    public Matrix4f worldMatrix;
    public Matrix4f modelMatrix;
    private RenderCycle renderState;
    public MultiBufferSource bufferSource;

    public SpmEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.worldMatrix = new Matrix4f();
        this.modelMatrix = new Matrix4f();
        this.renderState = RenderCycle.INITIAL;
        this.bufferSource = null;
    }

    public void renderEntity(T animatable, float entityYaw, float partialTick, PoseStack poseStack,
                             MultiBufferSource multiBufferSource, int packedLight) {
        HostActionEvent<?> event = animatable.processAnimation(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null) {
            Entity entity = animatable.getEntity();
            boolean visible = !entity.isInvisibleTo(minecraft.player);
            boolean glowing = minecraft.shouldEntityAppearGlowing(entity);
            RenderType renderType = getRenderType(
                    animatable.getTextureLocation(),
                    visible,
                    glowing,
                    animatable.getCurrentModel().getGeoModel().isTranslucentTexture(0));
            if (renderType != null && (visible || glowing)) {
                int argb = getRenderColor(animatable, partialTick, poseStack, multiBufferSource, null, packedLight);
                ModelScenePose model = animatable.getCurrentModel();
                if (animatable instanceof SpmEntity<?> geoEntity) {
                    ClientModelManager.markModelUsed(geoEntity.getModelId());
                }
                this.worldMatrix = new Matrix4f(poseStack.last().pose());
                setCurrentModelRenderCycle(RenderCycle.INITIAL);
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
                renderWithBoneAndRenderType(
                        model, animatable, partialTick, renderType, poseStack, multiBufferSource, 0, null,
                        packedLight, packOverlayCoords(entity, 0.0f),
                        ((argb >>> 16) & 0xFF) / 255.0f,
                        ((argb >>> 8) & 0xFF) / 255.0f,
                        (argb & 0xFF) / 255.0f,
                        ((argb >>> 24) & 0xFF) / 255.0f);
                AnimationParticleRuntime.flush(animatable, event, this.worldMatrix, poseStack.last().pose());
                poseStack.popPose();
            }
        }
    }

    @Override
    public void renderEarly(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource,
                            VertexConsumer buffer, int packedLight, int packedOverlayIn,
                            float red, float green, float blue, float alpha) {
        this.modelMatrix = new Matrix4f(poseStack.last().pose());
        ISpmRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public static int packOverlayCoords(Entity entity, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(false));
    }

    @Override
    @NotNull
    public RenderCycle getCurrentModelRenderCycle() {
        return this.renderState;
    }

    @Override
    public void setCurrentModelRenderCycle(RenderCycle cycle) {
        this.renderState = cycle;
    }

    @Override
    public void setCurrentRTB(MultiBufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    @Override
    public MultiBufferSource getCurrentRTB() {
        return this.bufferSource;
    }
}
