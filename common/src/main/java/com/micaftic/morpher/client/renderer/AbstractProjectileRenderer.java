package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.entity.SpmEntity;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import com.mojang.math.Axis;

public abstract class AbstractProjectileRenderer<TEntity extends Projectile, T extends HostAnimatable<TEntity>> extends EntityRenderer<TEntity, EntityRenderState> implements ISpmRenderer<T> {

    public Matrix4f modelViewMatrix;

    public Matrix4f projectionMatrix;

    private RenderCycle renderState;

    public MultiBufferSource bufferSource;

    public AbstractProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.modelViewMatrix = new Matrix4f();
        this.projectionMatrix = new Matrix4f();
        this.renderState = RenderCycle.INITIAL;
        this.bufferSource = null;
    }

    public void render(T animatable, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        HostActionEvent<?> event = animatable.processAnimation(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null) {
            Projectile projectile = animatable.getEntity();
            boolean isVisible = !projectile.isInvisibleTo(minecraft.player);
            boolean zShouldEntityAppearGlowing = minecraft.shouldEntityAppearGlowing(projectile);
            RenderType renderType = getRenderType(animatable.getTextureLocation(), isVisible, zShouldEntityAppearGlowing, animatable.getCurrentModel().getGeoModel().isTranslucentTexture(0));
            if (renderType != null && (isVisible || zShouldEntityAppearGlowing)) {
                int argb = getRenderColor(animatable, partialTick, poseStack, bufferSource, null, packedLight);
                ModelScenePose model = animatable.getCurrentModel();
                if (animatable instanceof SpmEntity<?> geoEntity) {
                    ClientModelManager.markModelUsed(geoEntity.getModelId());
                }
                this.modelViewMatrix = new Matrix4f(poseStack.last().pose());
                setCurrentModelRenderCycle(RenderCycle.INITIAL);
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(projectile.getYRot(partialTick) - 90.0f));
                float xRot = projectile.getXRot(partialTick);
                if (projectile instanceof ThrownTrident) {
                    xRot += 90.0f;
                }
                poseStack.mulPose(Axis.ZP.rotationDegrees(xRot));
                renderWithBoneAndRenderType(
                        model, animatable, partialTick, renderType, poseStack, bufferSource, 0, null,
                        packedLight, getPackedLight(projectile, 0.0f),
                        ((argb >>> 16) & 0xFF) / 255.0f,
                        ((argb >>> 8) & 0xFF) / 255.0f,
                        (argb & 0xFF) / 255.0f,
                        ((argb >>> 24) & 0xFF) / 255.0f);
                com.micaftic.morpher.client.animation.particle.AnimationParticleRuntime.flush(animatable, event, this.modelViewMatrix, poseStack.last().pose());
                poseStack.popPose();
            }
        }
        // super.render() param changed in MC 1.26.1.2 - skip super call
        // super.render(animatable.getEntity(), entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void renderEarly(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource, VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        this.projectionMatrix = new Matrix4f(poseStack.last().pose());
        ISpmRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public static int getPackedLight(Entity entity, float u) {
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
