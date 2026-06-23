package com.micaftic.morpher.geckolib3.geo;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.util.Color;
import com.micaftic.morpher.geckolib3.extended.LivingEntityRendererAccessor;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.model.provider.data.EntityModelData;
import com.micaftic.morpher.geckolib3.util.EModelRenderCycle;
import com.micaftic.morpher.geckolib3.util.IRenderCycle;
import com.micaftic.morpher.mixin.client.LivingEntityAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.micaftic.morpher.core.api.client.RenderLivingBridge;

import java.util.List;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class GeoReplacedEntityRenderer<TEntity extends LivingEntity, T extends LivingAnimatable<TEntity>> extends LivingEntityRenderer implements IGeoRenderer<T> {

    public final List<GeoLayerRenderer<T>> layerRenderers = new ObjectArrayList<>();

    public Matrix4f dispatchedMat = new Matrix4f();

    public Matrix4f renderEarlyMat = new Matrix4f();

    public MultiBufferSource rtb;

    private IRenderCycle currentModelRenderCycle = EModelRenderCycle.INITIAL;

    public GeoReplacedEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5f);
        this.rtb = null;
    }

    public static int packOverlayCoords(LivingEntity entity, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
    }

    @Override
    @NotNull
    public IRenderCycle getCurrentModelRenderCycle() {
        return this.currentModelRenderCycle;
    }

    @Override
    public void setCurrentModelRenderCycle(IRenderCycle cycle) {
        this.currentModelRenderCycle = cycle;
    }

    @Override
    public void renderEarly(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource, VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        // 浣跨敤 .set 鏉ラ伩鍏嶆瘡娆℃覆鏌撳垱寤烘柊鐨?Matrix4f, 鍑忓皯 allocation rate
        this.renderEarlyMat.set(poseStack.last().pose());
        IGeoRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public void renderEntity(T t, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        renderEntityWithTexture(t, null, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    public void renderEntityWithTexture(T t, @Nullable Identifier textureLocation, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
        Direction bedOrientation;
        AnimationFrameProfiler.beginRenderFrame(partialTick);
        if (RenderLivingBridge.firePre(t.getEntity(), this, partialTick, poseStack, multiBufferSource, packedLight)) {
            return;
        }
        AnimationEvent<?> event = t.processAnimation(partialTick);
        TEntity entity = t.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null) {
            EntityModelData modelData = event.getModelData();
            // 浣跨敤 .set 鏉ラ伩鍏嶆瘡娆℃覆鏌撳垱寤烘柊鐨?Matrix4f, 鍑忓皯 allocation rate
            this.dispatchedMat.set(poseStack.last().pose());
            setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
            poseStack.pushPose();
            if (entity.getPose() == Pose.SLEEPING && (bedOrientation = entity.getBedOrientation()) != null) {
                float eyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f;
                poseStack.translate((-bedOrientation.getStepX()) * eyeHeight, 0.0f, (-bedOrientation.getStepZ()) * eyeHeight);
            }
            setupRotations(entity, poseStack, modelData.lerpedAge, modelData.lerpBodyRot, partialTick);
            if (t.getEntity().getVehicle() != null) {
                Entity vehicle = t.getEntity().getVehicle();
                VehicleCapability.get(vehicle).ifPresent(cap -> {
                    if (cap.isModelReady()) {
                        Vector3f vector3f = cap.getExpressionOffset();
                        if (vector3f != null) {
                            poseStack.mulPose(new Quaternionf().rotateZYX(vector3f.z, 0.0f, vector3f.x).invert());
                        }
                    }
                });
            }
            preRenderCallback(entity, poseStack, partialTick);
            poseStack.translate(0.0f, 0.01f, 0.0f);
            AnimatedGeoModel animatedGeoModel = t.getCurrentModel();
            Identifier renderTexture = textureLocation == null ? t.getTextureLocation() : textureLocation;
            int textureIndex = textureLocation == null ? t.getTextureIndex() : 0;
            // MC 26.x: isBodyVisible/shouldEntityAppearGlowing API changed
            RenderType renderType = getRenderType(renderTexture, true /* isBodyVisible(entity) */ && !entity.isInvisibleTo(minecraft.player), false /* minecraft.shouldEntityAppearGlowing(entity) */, t.getCurrentModel().getGeoModel().isTranslucentTexture(textureIndex));
            boolean useExtraPlayer = t.isRenderLayersFirst();
            Color color = getRenderColor(t, partialTick, poseStack, multiBufferSource, null, packedLight);
            renderWithBone(animatedGeoModel, t, partialTick, poseStack, multiBufferSource, null, packedLight, packOverlayCoords(entity, getHurtOverlayProgress(entity, partialTick)), color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
            if (useExtraPlayer && !entity.isSpectator()) {
                render(t, partialTick, poseStack, multiBufferSource, packedLight, event, modelData);
            }
            if (renderType != null) {
                renderWithBoneAndRenderType(animatedGeoModel, t, partialTick, renderType, poseStack, multiBufferSource, textureIndex, null, packedLight, packOverlayCoords(entity, getHurtOverlayProgress(entity, partialTick)), color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f, renderTexture);
            }
            if (!useExtraPlayer && !entity.isSpectator()) {
                render(t, partialTick, poseStack, multiBufferSource, packedLight, event, modelData);
            }
            poseStack.popPose();
        }
        ((LivingEntityRendererAccessor) this).tlm$renderNameTag(entity, entityYaw, partialTick, poseStack, multiBufferSource, packedLight);
        RenderLivingBridge.firePost(entity, this, partialTick, poseStack, multiBufferSource, packedLight);
    }

    public void render(T entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, AnimationEvent<?> event, EntityModelData data) {
        for (GeoLayerRenderer<T> layerRenderer : this.layerRenderers) {
            layerRenderer.render(poseStack, bufferSource, packedLightIn, entity, event.getLimbSwing(), event.getLimbSwingAmount(), partialTick, data.lerpedAge, data.rawNetHeadYaw, data.rawHeadPitch);
        }
    }

    public float getHurtOverlayProgress(TEntity entity, float partialTick) {
        return 0.0f;
    }

    public void preRenderCallback(TEntity entity, PoseStack poseStack, float partialTick) {
    }

    public void setupRotations(TEntity tentity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        int t = tentity.deathTime;
        boolean zIsAutoSpinAttack = tentity.isAutoSpinAttack();
        if (t > 0) {
            tentity.deathTime = 0;
        }
        if (zIsAutoSpinAttack) {
            ((LivingEntityAccessor) tentity).invokeSetLivingEntityFlag(4, false);
        }
        if (tentity.onClimbable()) {
            Optional<BlockPos> lastClimbablePos = tentity.getLastClimbablePos();
            if (lastClimbablePos.isPresent()) {
                Optional<Direction> optionalValue = tentity.level().getBlockState(lastClimbablePos.get()).getOptionalValue(HorizontalDirectionalBlock.FACING);
                if (optionalValue.isPresent()) {
                    rotationYaw = optionalValue.get().getOpposite().get2DDataValue() * 90;
                }
            }
        }
        if (tentity.getPose() == Pose.SLEEPING) {
            Direction bedOrientation = tentity.getBedOrientation();
            float sleepRotation = bedOrientation == null ? rotationYaw : sleepDirectionToRotation(bedOrientation);
            poseStack.mulPose(Axis.YP.rotationDegrees(sleepRotation));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - rotationYaw));
        }
        if (t > 0) {
            tentity.deathTime = t;
        }
        if (zIsAutoSpinAttack) {
            ((LivingEntityAccessor) tentity).invokeSetLivingEntityFlag(4, true);
        }
    }

    private static float sleepDirectionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0f;
            case WEST -> 0.0f;
            case NORTH -> 270.0f;
            case EAST -> 180.0f;
            default -> 0.0f;
        };
    }

    public boolean shouldShowName(TEntity entity) {
        double d = entity.isDiscrete() ? 32.0d : 64.0d;
        return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) < d * d && entity == Minecraft.getInstance().getEntityRenderDispatcher().crosshairPickEntity && entity.hasCustomName() && Minecraft.renderNames();
    }

    public final boolean addLayerRenderer(GeoLayerRenderer<T> layerRenderer) {
        return this.layerRenderers.add(layerRenderer);
    }

    @Override
    public MultiBufferSource getCurrentRTB() {
        return this.rtb;
    }

    @Override
    public void setCurrentRTB(MultiBufferSource bufferSource) {
        this.rtb = bufferSource;
    }
}
