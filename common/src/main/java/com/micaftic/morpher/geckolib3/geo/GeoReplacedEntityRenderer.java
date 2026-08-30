package com.micaftic.morpher.geckolib3.geo;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.gltf.GltfMaterialResolver;
import com.micaftic.morpher.client.renderer.gltf.GltfRenderTypes;
import com.micaftic.morpher.client.renderer.gltf.GltfVertexConsumerRenderer;
import com.micaftic.morpher.client.renderer.layer.HeldItemLayer;
import com.micaftic.morpher.resource.gltf.GltfHandNodeResolver;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.util.Color;
import com.micaftic.morpher.geckolib3.extended.LivingEntityRendererAccessor;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.model.provider.data.EntityModelData;
import com.micaftic.morpher.geckolib3.util.EModelRenderCycle;
import com.micaftic.morpher.geckolib3.util.IRenderCycle;
import com.micaftic.morpher.resource.gltf.GltfAnimationClock;
import com.micaftic.morpher.resource.gltf.GltfAnimationController;
import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.resource.gltf.GltfSceneEvaluator;
import com.micaftic.morpher.client.input.InputStateKey;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
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

    private boolean fallFlyingPitchHandledByAnimation;

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
        if (t.getModelAssembly() != null && t.getModelAssembly().isGltf()) {
            renderGltfEntity(t, textureLocation, entityYaw, partialTick, poseStack, multiBufferSource, packedLight);
            TEntity entity = t.getEntity();
            ((LivingEntityRendererAccessor) this).tlm$renderNameTag(entity, entityYaw, partialTick, poseStack, multiBufferSource, packedLight);
            RenderLivingBridge.firePost(entity, this, partialTick, poseStack, multiBufferSource, packedLight);
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
            try {
                if (entity.getPose() == Pose.SLEEPING && (bedOrientation = entity.getBedOrientation()) != null) {
                    float eyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f;
                    poseStack.translate((-bedOrientation.getStepX()) * eyeHeight, 0.0f, (-bedOrientation.getStepZ()) * eyeHeight);
                }
            boolean previousFallFlyingPitchState = this.fallFlyingPitchHandledByAnimation;
            this.fallFlyingPitchHandledByAnimation = t.getModelAssembly() != null
                    && t.getModelAssembly().getAnimationBundle() != null
                    && t.getModelAssembly().getAnimationBundle().isFallFlyingPitchHandledByAnimation();
            try {
                setupRotations(entity, poseStack, modelData.lerpedAge, modelData.lerpBodyRot, partialTick);
            } finally {
                this.fallFlyingPitchHandledByAnimation = previousFallFlyingPitchState;
            }
            if (t.getEntity().getVehicle() != null && !com.micaftic.morpher.client.render.RenderContext.isGuiPreview()) {
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
            } finally {
                poseStack.popPose();
            }
        }
        ((LivingEntityRendererAccessor) this).tlm$renderNameTag(entity, entityYaw, partialTick, poseStack, multiBufferSource, packedLight);
        RenderLivingBridge.firePost(entity, this, partialTick, poseStack, multiBufferSource, packedLight);
    }

    private void renderGltfEntity(T t, @Nullable Identifier overrideTexture, float entityYaw, float partialTick,
                                  PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        TEntity entity = t.getEntity();
        ModelAssembly assembly = t.getModelAssembly();
        if (assembly == null || assembly.getGltfModel() == null) return;
        poseStack.pushPose();
        try {
            setupRotations(entity, poseStack, entity.tickCount + partialTick,
                    Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot), partialTick);
            preRenderCallback(entity, poseStack, partialTick);
            poseStack.translate(0.0f, 0.01f, 0.0f);
            GltfModel model = assembly.getGltfModel();
            float renderScale = model.recommendedMinecraftScale();
            poseStack.scale(renderScale, renderScale, renderScale);
            if (model.scenes().isEmpty() || model.defaultScene() < 0) return;
            GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
            float clock = GltfAnimationClock.fromMinecraftTicks(entity.tickCount, partialTick);
            GltfAnimationController controller = t.getGltfAnimationController();
            if (controller == null) controller = new GltfAnimationController(model);
            boolean attacking = InputStateKey.isAnyHandSwinging(entity);
            boolean usingItem = InputStateKey.isUsingItem(entity, InputStateKey.getUsedItemHand(entity));
            controller.selectForMotion((float) entity.getDeltaMovement().horizontalDistance(), entity.onGround(),
                    entity.isCrouching(), entity.deathTime > 0, attacking, usingItem, clock);
            GltfSceneEvaluator.Pose pose = controller.evaluate(evaluator, model.defaultScene(), clock);
            java.util.function.Function<GltfModel.Material, VertexConsumer> consumerFactory = material -> {
                GltfMaterialResolver.ResolvedMaterial<Identifier> resolved = GltfMaterialResolver.resolve(
                        material, ClientModelManager.getDefaultTexture(), overrideTexture, textureIndex -> {
                            if (material == null) return null;
                            var texture = assembly.getGltfTexture(material.baseColorTextureIndex());
                            if (texture == null) return null;
                            IResourceLocatable locatable = UploadManager.getOrCreateLocatable(texture, true);
                            return locatable.getResourceLocationOrNull();
                        });
                return bufferSource.getBuffer(GltfRenderTypes.get(resolved.texture(), resolved.alphaMode(), resolved.doubleSided()));
            };
            GltfVertexConsumerRenderer.render(model, evaluator, pose, poseStack, consumerFactory,
                    packedLight, packOverlayCoords(entity, getHurtOverlayProgress(entity, partialTick)),
                    1.0f, 1.0f, 1.0f, 1.0f);
            renderGltfHeldItems(t, model, pose, poseStack, bufferSource, packedLight, partialTick, model.recommendedMinecraftScale());
        } finally {
            poseStack.popPose();
        }
    }

    private void renderGltfHeldItems(T animatable, com.micaftic.morpher.resource.gltf.GltfModel model,
                                     com.micaftic.morpher.resource.gltf.GltfSceneEvaluator.Pose pose,
                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                     float partialTick, float renderScale) {
        HeldItemLayer itemLayer = null;
        for (GeoLayerRenderer<T> layerRenderer : this.layerRenderers) {
            if (layerRenderer instanceof HeldItemLayer) {
                itemLayer = (HeldItemLayer) layerRenderer;
                break;
            }
        }
        if (itemLayer == null) return;
        TEntity entity = animatable.getEntity();
        renderGltfHeldItem(itemLayer, entity, entity.getMainHandItem(), entity.getMainArm(), model, pose,
                poseStack, bufferSource, packedLight, partialTick, renderScale);
        renderGltfHeldItem(itemLayer, entity, entity.getOffhandItem(), entity.getMainArm().getOpposite(), model, pose,
                poseStack, bufferSource, packedLight, partialTick, renderScale);
    }

    private void renderGltfHeldItem(HeldItemLayer itemLayer, LivingEntity entity, ItemStack itemStack,
                                    HumanoidArm humanoidArm, com.micaftic.morpher.resource.gltf.GltfModel model,
                                    com.micaftic.morpher.resource.gltf.GltfSceneEvaluator.Pose pose,
                                    PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                    float partialTick, float renderScale) {
        if (itemStack == null || itemStack.isEmpty()) return;
        GltfHandNodeResolver.Hand hand = humanoidArm == HumanoidArm.LEFT
                ? GltfHandNodeResolver.Hand.LEFT : GltfHandNodeResolver.Hand.RIGHT;
        int handNode = GltfHandNodeResolver.find(model, hand);
        if (handNode < 0) return;
        poseStack.pushPose();
        try {
            poseStack.mulPose(pose.worldMatrix(handNode));
            if (renderScale != 0.0f && renderScale != 1.0f) {
                poseStack.scale(1.0f / renderScale, 1.0f / renderScale, 1.0f / renderScale);
            }
            itemLayer.renderGltfThirdPersonItem(entity, itemStack, humanoidArm, poseStack, bufferSource,
                    packedLight, partialTick);
        } finally {
            poseStack.popPose();
        }
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
            tentity.setLivingEntityFlag(4, false);
        }
        if (tentity.onClimbable() && !com.micaftic.morpher.client.render.RenderContext.isGuiPreview()) {
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
            if (tentity.isFallFlying() && !com.micaftic.morpher.client.render.RenderContext.isGuiPreview()) {
                applyFallFlyingRotation(tentity, poseStack, partialTicks, zIsAutoSpinAttack, this.fallFlyingPitchHandledByAnimation);
            }
        }
        if (t > 0) {
            tentity.deathTime = t;
        }
        if (zIsAutoSpinAttack) {
            tentity.setLivingEntityFlag(4, true);
        }
    }

    private static void applyFallFlyingRotation(LivingEntity entity, PoseStack poseStack, float partialTicks, boolean autoSpinAttack, boolean animationHandlesPitch) {
        float ticks = (float) entity.getFallFlyingTicks() + partialTicks;
        float progress = Mth.clamp(ticks * ticks / 100.0f, 0.0f, 1.0f);
        if (!autoSpinAttack && !animationHandlesPitch) {
            poseStack.mulPose(Axis.XP.rotationDegrees(progress * (-90.0f - entity.getXRot())));
        }
        Vec3 view = entity.getViewVector(partialTicks);
        Vec3 movement = entity.getDeltaMovement();
        double movementHorizontal = movement.horizontalDistanceSqr();
        double viewHorizontal = view.horizontalDistanceSqr();
        if (movementHorizontal > 0.0d && viewHorizontal > 0.0d) {
            double dot = (movement.x * view.x + movement.z * view.z) / Math.sqrt(movementHorizontal * viewHorizontal);
            double cross = movement.x * view.z - movement.z * view.x;
            poseStack.mulPose(Axis.YP.rotation((float) (Math.signum(cross) * Math.acos(Mth.clamp(dot, -1.0d, 1.0d)))));
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
        // MC 26.2: Minecraft.renderNames() 已移除（名字渲染改由 EntityRenderState.nameTag 状态驱动）
        return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) < d * d && entity == Minecraft.getInstance().getEntityRenderDispatcher().crosshairPickEntity && entity.hasCustomName();
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
