package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.acceleratedrendering.AcceleratedRenderingCompat;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.client.animation.AnimationTracker;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RenderPass;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.util.AnimatableCacheUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;
import java.util.concurrent.ExecutionException;
import com.mojang.math.Axis;

public final class ModelPreviewRenderer {

    public static final float FRONT_FACING_YAW = 180.0f;

    private static final float MODEL_PREVIEW_MOUSE_YAW_DEGREES = 25.0f;

    private static final float MODEL_PREVIEW_MOUSE_PITCH_DEGREES = 15.0f;

    private static final float MODEL_PREVIEW_MOUSE_DEADZONE = 0.08f;

    private static final float EXTRA_PLAYER_HEAD_YAW_LIMIT = 85.0f;

    private static final PreviewMouseRotation NO_MOUSE_ROTATION = new PreviewMouseRotation(0.0f, 0.0f);

    private static final double ANIMATION_PREVIEW_Z = 250.0d;

    private static final double MODEL_PREVIEW_Z = 50.0d;

    private static final ThreadLocal<Boolean> PREVIEW_MODE = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> FIRST_PERSON_MODE = ThreadLocal.withInitial(() -> false);

    // Animation evaluation runs on worker threads during a world render. Unlike the preview
    // modes, this frame-scoped flag must therefore be visible across threads.
    private static volatile boolean worldRenderMode;

    private static boolean inventoryPreviewFrontFacing = false;

    private static final class PreviewMouseRotation {
        private final float yaw;
        private final float pitch;

        private PreviewMouseRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static PreviewMouseRotation getPreviewMouseRotation(int left, int top, int right, int bottom, int mouseX, int mouseY, boolean disablePreviewRotation) {
        if (disablePreviewRotation || right <= left || bottom <= top || mouseX == Integer.MIN_VALUE || mouseY == Integer.MIN_VALUE) {
            return NO_MOUSE_ROTATION;
        }
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        float halfWidth = Math.max(1.0f, (right - left) * 0.5f);
        float halfHeight = Math.max(1.0f, (bottom - top) * 0.5f);
        float normalizedYaw = applyPreviewMouseDeadzone(Mth.clamp((centerX - mouseX) / halfWidth, -1.0f, 1.0f));
        float normalizedPitch = applyPreviewMouseDeadzone(Mth.clamp((centerY - mouseY) / halfHeight, -1.0f, 1.0f));
        float yaw = normalizedYaw * MODEL_PREVIEW_MOUSE_YAW_DEGREES;
        float pitch = normalizedPitch * MODEL_PREVIEW_MOUSE_PITCH_DEGREES;
        return new PreviewMouseRotation(yaw, pitch);
    }

    private static float applyPreviewMouseDeadzone(float value) {
        return Math.abs(value) < MODEL_PREVIEW_MOUSE_DEADZONE ? 0.0f : value;
    }

    private static float getExtraPlayerHeadYawOffset(LivingEntity entity) {
        return Mth.clamp(Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot), -EXTRA_PLAYER_HEAD_YAW_LIMIT, EXTRA_PLAYER_HEAD_YAW_LIMIT);
    }

    private static float getExtraPlayerHeadYawOffsetO(LivingEntity entity) {
        return Mth.clamp(Mth.wrapDegrees(entity.yHeadRotO - entity.yBodyRotO), -EXTRA_PLAYER_HEAD_YAW_LIMIT, EXTRA_PLAYER_HEAD_YAW_LIMIT);
    }

    public static void setPreviewMode(boolean previewMode) {
        PREVIEW_MODE.set(previewMode);
    }

    public static boolean isPreview() {
        return PREVIEW_MODE.get();
    }

    /** 兼容便捷方法：进入/退出额外玩家渲染阶段（内部走 RenderContext，替代 EXTRA_PLAYER_MODE ThreadLocal）。 */
    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        if (extraPlayerMode) {
            RenderContext.enter(RenderPass.OLD_HUD);
        } else {
            // 兼容旧调用（无 previous 引用）：回退到世界阶段。渲染入口应优先用 enter/restore + finally。
            RenderContext.restore(RenderPass.WORLD);
        }
    }

    /** 是否处于额外玩家渲染阶段（GUI_PREVIEW pass）。 */
    public static boolean isExtraPlayer() {
        return RenderContext.isOldHud();
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        FIRST_PERSON_MODE.set(firstPersonMode);
    }

    public static void setWorldRenderMode(boolean worldRenderMode) {
        ModelPreviewRenderer.worldRenderMode = worldRenderMode;
    }

    public static boolean isWorldRender() {
        return worldRenderMode;
    }

    public static boolean isFirstPerson() {
        return FIRST_PERSON_MODE.get() || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        return FIRST_PERSON_MODE.get() && !FirstPersonCompat.isFirstPersonActive();
    }

    public static void setInventoryPreviewFrontFacing(boolean frontFacing) {
        inventoryPreviewFrontFacing = frontFacing;
    }

    public static boolean isInventoryPreviewFrontFacing() {
        return inventoryPreviewFrontFacing;
    }

    public static void renderVehicleModel(Entity entity, PoseStack poseStack, float partialTick) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            VehicleCapability.get(vehicle).ifPresent(cap -> {
                int index;
                AnimatedGeoModel model;
                List<IBone> list;
                if (!cap.isModelInitialized() || !cap.isModelReady() || (index = vehicle.getPassengers().indexOf(entity)) < 0 || (model = cap.getCurrentModel()) == null || model.passengerGroupChains().isEmpty() || index >= model.passengerGroupChains().size() || (list = model.passengerGroupChains().get(index)) == null) {
                    return;
                }
                float bodyRotation = CustomVehicleRenderer.getBodyRotation(vehicle, Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot()), partialTick);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRotation));
                RenderUtils.prepMatrixForLocator(poseStack, list);
                poseStack.mulPose(Axis.YN.rotationDegrees(180.0f - bodyRotation));
                Vec3 passengerAttachment = vehicle.getPassengerRidingPosition(entity).subtract(vehicle.position());
                double myRidingOffset = -passengerAttachment.y();
                if (((entity instanceof Player) && PlayerCapability.get(entity).isPresent()) || TouhouLittleMaidCompat.isMaidRideable(entity)) {
                    myRidingOffset -= 0.5d;
                }
                poseStack.translate(0.0d, myRidingOffset, 0.0d);
            });
        }
    }

    // 动画测试界面的模型
    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, ANIMATION_PREVIEW_Z);
        poseStack.scale(scale, scale, -scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees((-10.0f) + pitch);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        Pose oldPose = livingEntity.getPose();
        float previewYaw = -yaw;
        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = previewYaw;
        livingEntity.yHeadRotO = previewYaw;

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        entityRenderDispatcher.overrideCameraOrientation(rotationX);
        entityRenderDispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        RenderSystem.runAsFancy(() -> {
            AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();
            if (animationTracker.isCurrentAnimation("sleep")) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
                poseStack.translate(0.5d, 0.5625d, 0.0d);
                livingEntity.setPose(Pose.SLEEPING);
            }
            if (animationTracker.isCurrentAnimation("swim") || animationTracker.isCurrentAnimation("swim_stand")) {
                livingEntity.setPose(Pose.SWIMMING);
            }
            if (animationTracker.isCurrentAnimation("sneak") || animationTracker.isCurrentAnimation("sneaking")) {
                livingEntity.setPose(Pose.CROUCHING);
            }
            if (animationTracker.isCurrentAnimation("sit")) {
                poseStack.translate(0.0d, -0.5d, 0.0d);
            }
            if (animationTracker.isCurrentAnimation("ride")) {
                poseStack.translate(0.0d, 0.85d, 0.0d);
            }
            if (animationTracker.isCurrentAnimation("ride_pig")) {
                poseStack.translate(0.0d, 0.3125d, 0.0d);
            }
            if (animationTracker.isCurrentAnimation("boat")) {
                poseStack.translate(0.0d, -0.45d, 0.0d);
            }
            try {
                renderVehicleForAnimation(yaw, animatableEntity, partialTick, poseStack, entityRenderDispatcher, bufferSource);
                if (animationTracker.isCurrentAnimation("sleep")) {
                    renderBedPreview(x, y, scale, pitch, yaw, bufferSource);
                }
                if (renderGround) {
                    renderGroundPreview(x, y, scale, pitch, yaw, bufferSource);
                }
                bufferSource.endBatch();
                renderer.renderEntity((LivingAnimatable) animatableEntity, 0.0f, partialTick, poseStack, bufferSource, 15728880);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        bufferSource.endBatch();
        entityRenderDispatcher.setRenderShadow(true);
        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        livingEntity.setPose(oldPose);

        Lighting.setupFor3DItems();
        setPreviewMode(false);
    }

    private static void renderBedPreview(float x, float y, float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, ANIMATION_PREVIEW_Z);
        poseStack.scale(scale, scale, -scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.RED_BED.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderGroundPreview(float x, float y, float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, ANIMATION_PREVIEW_Z);
        poseStack.scale(scale, scale, -scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0f, 0.0f, 1.0f);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.GRASS_BLOCK.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
            }
            poseStack.translate(1.0f, 0.0f, -3.0f);
        }

        poseStack.translate(-1.0f, 1.0f, 1.0f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.SHORT_GRASS.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
        poseStack.translate(0.0f, 0.0f, 1.0f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.RED_TULIP.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderVehicleForAnimation(float yaw, AnimatableEntity animatableEntity, float partialTick, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource) throws ExecutionException {
        Entity entity = animatableEntity.getEntity();
        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();

        if (animationTracker.isCurrentAnimation("ride")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.HORSE), () -> EntityType.HORSE.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.PIG), () -> EntityType.PIG.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.BOAT), () -> EntityType.BOAT.create(entity.level())), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource, Entity vehicleEntity, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        Vec3 passengerAttachment = vehicleEntity.getPassengerRidingPosition(riderEntity).subtract(vehicleEntity.position());
        entityRenderDispatcher.render(vehicleEntity, 0.0d, passengerAttachment.y(), 0.0d, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        poseStack.popPose();
    }

    // 模型预览页面
    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        renderLivingEntityPreview(x, y, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, FRONT_FACING_YAW);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        renderLivingEntityPreview(x, y, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, FRONT_FACING_YAW, getPreviewMouseRotation(left, top, right, bottom, mouseX, mouseY, disablePreviewRotation));
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw) {
        renderLivingEntityPreview(x, y, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, previewYaw, NO_MOUSE_ROTATION);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        renderLivingEntityPreview(x, y, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, previewYaw, getPreviewMouseRotation(left, top, right, bottom, mouseX, mouseY, disablePreviewRotation));
    }

    private static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, PreviewMouseRotation mouseRotation) {
        ItemStack[] savedEquipment;
        setPreviewMode(true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        LivingEntity livingEntity = animatable.getEntity();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, MODEL_PREVIEW_Z);
        poseStack.translate(0.0d, disablePreviewRotation ? 5.5d : 0.0d, 0.0d);
        poseStack.scale(scale, scale, -scale);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(disablePreviewRotation ? 0.0f : -10.0f + mouseRotation.pitch);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        if (hideEquipment && (livingEntity instanceof Player player)) {
            savedEquipment = new ItemStack[EquipmentSlot.values().length];
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                savedEquipment[slotIndex] = player.getItemBySlot(equipmentSlot).copy();
                if (equipmentSlot == EquipmentSlot.MAINHAND) {
                    player.getInventory().items.set(player.getInventory().selected, ItemStack.EMPTY);
                } else if (equipmentSlot == EquipmentSlot.OFFHAND) {
                    player.getInventory().offhand.set(0, ItemStack.EMPTY);
                } else {
                    NonNullList<ItemStack> armorList = player.getInventory().armor;
                    if (armorList.size() > equipmentSlot.getIndex()) {
                        armorList.set(equipmentSlot.getIndex(), ItemStack.EMPTY);
                    }
                }
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        float displayYaw = previewYaw + mouseRotation.yaw;
        livingEntity.yBodyRot = displayYaw;
        livingEntity.yBodyRotO = displayYaw;
        livingEntity.setYRot(displayYaw);
        livingEntity.yRotO = displayYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        Entity vehicle = livingEntity.getVehicle();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - displayYaw));
            livingEntity.yHeadRot = vehicleYaw;
            livingEntity.yHeadRotO = vehicleYaw;
        }

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        entityRenderDispatcher.overrideCameraOrientation(rotationX);
        entityRenderDispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        try {
            RenderSystem.runAsFancy(() -> {
                renderer.renderEntity(animatable, 0.0f, partialTick, poseStack, bufferSource, 15728880);
            });
        } finally {
            try {
                bufferSource.endBatch();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            entityRenderDispatcher.setRenderShadow(true);
            livingEntity.yBodyRot = oldBodyRot;
            livingEntity.yBodyRotO = oldBodyRotO;
            livingEntity.setYRot(oldYRot);
            livingEntity.yRotO = oldYRotO;
            livingEntity.setXRot(oldXRot);
            livingEntity.xRotO = oldXRotO;
            livingEntity.yHeadRotO = oldHeadRotO;
            livingEntity.yHeadRot = oldHeadRot;
            if (savedEquipment != null) {
                Player player = (Player) livingEntity;
                int slotIndex = 0;
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    ItemStack itemStack = savedEquipment[slotIndex];
                    if (equipmentSlot == EquipmentSlot.MAINHAND) {
                        player.getInventory().items.set(player.getInventory().selected, itemStack);
                    } else if (equipmentSlot == EquipmentSlot.OFFHAND) {
                        player.getInventory().offhand.set(0, itemStack);
                    } else {
                        NonNullList<ItemStack> armorList = player.getInventory().armor;
                        if (armorList.size() > equipmentSlot.getIndex()) {
                            armorList.set(equipmentSlot.getIndex(), itemStack);
                        }
                    }
                    slotIndex++;
                }
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Lighting.setupFor3DItems();
            setPreviewMode(false);
        }
    }

    // 额外玩家视图（离屏缓存渲染，2026-08-13）：overlay 每帧完整渲染玩家模型开销大（提交/管线），
    // 改为离屏缓存——定期渲染到 FBO（动画更新），其余帧只贴图（开销≈1 次 draw）。
    // 2 tick 约为 10 FPS：比原 4 tick / 5 FPS 顺滑，同时避免恢复每帧完整渲染的高开销。
    private static final int EXTRA_PLAYER_FBO_REFRESH_TICKS = 2;
    // GPU 路径不必追随数百/数千 FPS 的主画面重复评估同一段动画。60 Hz 已能保持连续观感，
    // 同时让高刷或未限帧场景的额外玩家视图成本保持恒定。
    private static final long EXTRA_PLAYER_GPU_REFRESH_NANOS = 1_000_000_000L / 60L;
    private static final int EXTRA_PLAYER_FBO_PADDING = 2;
    private static RenderTarget extraPlayerFbo;
    private static int extraPlayerFboWidth = -1;
    private static int extraPlayerFboHeight = -1;
    private static int extraPlayerLastRenderTick = -1;
    private static long extraPlayerLastRenderNanos = -1L;
    private static boolean extraPlayerGpuFastPath;

    public static void renderPlayerOverlay(GuiGraphics guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        boolean profile = ExtraPlayerRenderProfiler.enabled();
        long totalStart = profile ? System.nanoTime() : 0L;
        if (localPlayer == null || scale <= 0.0f) {
            return;
        }
        int width = Math.max(1, Math.round(scale));
        int height = Math.max(1, Math.round(scale * 2.0f));
        int logicalFboWidth = width + EXTRA_PLAYER_FBO_PADDING * 2;
        int logicalFboHeight = height + EXTRA_PLAYER_FBO_PADDING * 2;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainRenderTarget = minecraft.getMainRenderTarget();
        int screenW = minecraft.getWindow().getGuiScaledWidth();
        int screenH = minecraft.getWindow().getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0 || mainRenderTarget.viewWidth <= 0 || mainRenderTarget.viewHeight <= 0) {
            return;
        }
        float pixelScaleX = mainRenderTarget.viewWidth / (float) screenW;
        float pixelScaleY = mainRenderTarget.viewHeight / (float) screenH;
        int fboWidth = Math.max(1, (int) Math.ceil(logicalFboWidth * pixelScaleX));
        int fboHeight = Math.max(1, (int) Math.ceil(logicalFboHeight * pixelScaleY));
        int tick = com.micaftic.morpher.client.event.ClientTickEvent.getTickCount();

        // 只为旧 HUD 模型矩形分配 FBO，但按主目标的物理像素密度栅格化；避免每帧清理整张屏幕大小的颜色/深度附件。
        if (extraPlayerFbo == null || extraPlayerFboWidth != fboWidth || extraPlayerFboHeight != fboHeight) {
            if (extraPlayerFbo != null) {
                extraPlayerFbo.destroyBuffers();
            }
            // 玩家模型是 3D 几何，FBO 需要深度附件保证正确遮挡。
            extraPlayerFbo = new TextureTarget(fboWidth, fboHeight, true, false);
            extraPlayerFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            extraPlayerFboWidth = fboWidth;
            extraPlayerFboHeight = fboHeight;
            extraPlayerLastRenderTick = -1;
            extraPlayerLastRenderNanos = -1L;
            extraPlayerGpuFastPath = false;
        }

        // 定期重渲染到 FBO（动画更新；其余帧贴缓存图）。
        // 用独立 bufferSource（MultiBufferSource.immediate）确保提交到 FBO——guiGraphics 的
        // bufferSource 绑定主渲染目标，FBO 绑定后 flush 不会提交到 FBO（表现为透明）。
        // GPU 持久网格最多以 60 Hz 更新；SIMD/Java 兜底仍限制为 2 tick（约 10 Hz）。
        // 这样兼容模式不会再因为“刷新得更少”而反常地比 GPU 模式高出大量帧数。
        boolean redrawn = false;
        boolean fullyGpuThisRedraw = false;
        int frameGpuPasses = 0;
        int frameFallbackPasses = 0;
        long renderNanos = System.nanoTime();
        boolean initialRender = extraPlayerLastRenderTick < 0 || extraPlayerLastRenderNanos < 0L;
        boolean refreshDue = initialRender
                || (extraPlayerGpuFastPath
                ? renderNanos - extraPlayerLastRenderNanos >= EXTRA_PLAYER_GPU_REFRESH_NANOS
                : tick - extraPlayerLastRenderTick >= EXTRA_PLAYER_FBO_REFRESH_TICKS);
        if (refreshDue) {
            // Finish the outer HUD batch before changing framebuffer. ImmediatelyFast normally
            // flushes at this boundary, but doing it explicitly also covers compatibility bridges.
            guiGraphics.flush();
            boolean isolatedAcceleratedRendering = AcceleratedRenderingCompat.enterVanillaPipeline();
            boolean projectionBackedUp = false;
            ModelRendererBridge.beginPreviewFrame();
            try {
                // RenderTarget.clear() 会自行 bindWrite，清屏后又 unbindWrite，因此必须在 clear 之后重新绑定。
                long clearStart = profile ? System.nanoTime() : 0L;
                // glClear obeys the current color/depth write masks. Compatibility RenderTypes may leave
                // alpha writes disabled, which turns the nominal transparent clear into an opaque black quad.
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                extraPlayerFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                extraPlayerFbo.clear(false);
                extraPlayerFbo.bindWrite(true);
                RenderSystem.backupProjectionMatrix();
                projectionBackedUp = true;
                RenderSystem.setProjectionMatrix(
                        new Matrix4f().setOrtho(0.0f, logicalFboWidth, logicalFboHeight, 0.0f, 1000.0f, 21000.0f),
                        VertexSorting.ORTHOGRAPHIC_Z);
                if (profile) {
                    ExtraPlayerRenderProfiler.recordClear(System.nanoTime() - clearStart);
                }
                MultiBufferSource.BufferSource fboBuffer = MultiBufferSource.immediate(new ByteBufferBuilder(256));
                long modelStart = profile ? System.nanoTime() : 0L;
                renderOverlayModel(localPlayer, EXTRA_PLAYER_FBO_PADDING, EXTRA_PLAYER_FBO_PADDING,
                        scale, yawOffset, zDepth, partialTick, fboBuffer);
                if (profile) {
                    ExtraPlayerRenderProfiler.recordModel(System.nanoTime() - modelStart);
                }
                long batchStart = profile ? System.nanoTime() : 0L;
                fboBuffer.endBatch();
                // Accelerated Rendering + ImmediatelyFast may redirect even a private BufferSource
                // to a shared provider. Flush all GUI work while the old HUD FBO is still bound.
                guiGraphics.flush();
                if (profile) {
                    ExtraPlayerRenderProfiler.recordBatch(System.nanoTime() - batchStart);
                }
                extraPlayerLastRenderTick = tick;
                extraPlayerLastRenderNanos = renderNanos;
                redrawn = true;
            } finally {
                // 只有所有模型网格都由 GPU 后端完成，下一帧才继续无节流刷新。
                fullyGpuThisRedraw = ModelRendererBridge.wasPreviewGpuRendered();
                frameGpuPasses = ModelRendererBridge.getPreviewGpuPassCount();
                frameFallbackPasses = ModelRendererBridge.getPreviewFallbackPassCount();
                extraPlayerGpuFastPath = fullyGpuThisRedraw;
                AcceleratedRenderingCompat.exitVanillaPipeline(isolatedAcceleratedRendering);
                if (projectionBackedUp) {
                    RenderSystem.restoreProjectionMatrix();
                }
                // unbindWrite() 只会绑定 framebuffer 0，不会恢复 Minecraft 的主目标和视口。
                mainRenderTarget.bindWrite(true);
            }
        }

        // 每帧把局部 FBO 整张贴回对应 GUI 矩形。
        long compositeStart = profile ? System.nanoTime() : 0L;
        // Direct GPU/compatibility renderers can leave Minecraft's cached blend state out of sync
        // with the actual GL state. Force a disable -> enable transition, then set the blend
        // equation directly so transparent FBO pixels cannot overwrite the main target as black.
        RenderSystem.disableBlend();
        RenderSystem.enableBlend();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, extraPlayerFbo.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = (float) x - EXTRA_PLAYER_FBO_PADDING;
        float y0 = (float) y - EXTRA_PLAYER_FBO_PADDING;
        float x1 = x0 + logicalFboWidth;
        float y1 = y0 + logicalFboHeight;
        float z = (float) zDepth;
        buffer.addVertex(x0, y1, z).setUv(0.0f, 0.0f);
        buffer.addVertex(x1, y1, z).setUv(1.0f, 0.0f);
        buffer.addVertex(x1, y0, z).setUv(1.0f, 1.0f);
        buffer.addVertex(x0, y0, z).setUv(0.0f, 1.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        if (profile) {
            ExtraPlayerRenderProfiler.recordComposite(System.nanoTime() - compositeStart);
            ExtraPlayerRenderProfiler.finishFrame(System.nanoTime() - totalStart, redrawn,
                    fullyGpuThisRedraw, frameGpuPasses, frameFallbackPasses);
        }
    }

    /** 渲染玩家模型到当前渲染目标（FBO；独立 poseStack + bufferSource，由调用方绑定 FBO 并 endBatch）。 */
    private static void renderOverlayModel(LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick, MultiBufferSource bufferSource) {
        RenderPass previousPass = RenderContext.enter(RenderPass.OLD_HUD);
        float previewYaw = FRONT_FACING_YAW;
        PoseStack poseStack = new PoseStack();
        try {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            poseStack.translate(x + (scale * 0.5d), y + (scale * 2.0d), 0.0d);
            poseStack.scale(scale, scale, -scale);

            Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.1f);
            Quaternionf rotationY = Axis.YP.rotationDegrees(yawOffset - FRONT_FACING_YAW + 180.0f);
            rotationZ.mul(rotationY);
            poseStack.mulPose(rotationZ);

            Lighting.setupForEntityInInventory();
            EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            rotationY.conjugate();
            entityRenderDispatcher.overrideCameraOrientation(rotationY);
            entityRenderDispatcher.setRenderShadow(false);

            boolean renderedCustomModel = PlayerCapability.get(localPlayer)
                    .filter(PlayerCapability::isModelActive)
                    .map(cap -> {
                        RenderSystem.runAsFancy(() -> RendererManager.getPlayerRenderer()
                                .render(localPlayer, previewYaw, partialTick, poseStack, bufferSource, 15728880));
                        return true;
                    })
                    .orElse(false);
            if (!renderedCustomModel) {
                RenderSystem.runAsFancy(() -> {
                    entityRenderDispatcher.render(localPlayer, 0.0d, 0.0d, 0.0d, previewYaw, partialTick, poseStack, bufferSource, 15728880);
                });
            }

            entityRenderDispatcher.setRenderShadow(true);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Lighting.setupFor3DItems();
            RenderContext.restore(previousPass);
        }
    }
}
