package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
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
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.util.AnimatableCacheUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
            RenderContext.enter(RenderPass.GUI_PREVIEW);
        } else {
            // 兼容旧调用（无 previous 引用）：回退到世界阶段。渲染入口应优先用 enter/restore + finally。
            RenderContext.restore(RenderPass.WORLD);
        }
    }

    /** 是否处于额外玩家渲染阶段（GUI_PREVIEW pass）。 */
    public static boolean isExtraPlayer() {
        return RenderContext.isGuiPreview();
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

    // 纸娃娃（离屏缓存渲染，2026-08-13）：overlay 每帧完整渲染玩家模型开销大（提交/管线），
    // 改为离屏缓存——每 4 帧渲染到 FBO（动画更新），其余帧只贴图（开销≈1 次 draw）。
    private static RenderTarget extraPlayerFbo;
    private static int extraPlayerFboWidth = -1;
    private static int extraPlayerFboHeight = -1;
    private static int extraPlayerLastRenderTick = -1;

    public static void renderPlayerOverlay(GuiGraphics guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        if (localPlayer == null || scale <= 0.0f) {
            return;
        }
        int width = Math.max(1, Math.round(scale));
        int height = Math.max(1, Math.round(scale * 2.0f));
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        int tick = com.micaftic.morpher.client.event.ClientTickEvent.getTickCount();

        // FBO 缓存（屏幕尺寸；投影与 GUI 一致，坐标天然正确）
        if (extraPlayerFbo == null || extraPlayerFboWidth != screenW || extraPlayerFboHeight != screenH) {
            if (extraPlayerFbo != null) {
                extraPlayerFbo.destroyBuffers();
            }
            extraPlayerFbo = new TextureTarget(screenW, screenH, false, false);
            extraPlayerFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            extraPlayerFboWidth = screenW;
            extraPlayerFboHeight = screenH;
            extraPlayerLastRenderTick = -1;
        }

        // 每 4 帧重渲染到 FBO（动画更新；其余帧贴缓存图）
        if (tick - extraPlayerLastRenderTick >= 4 || extraPlayerLastRenderTick < 0) {
            extraPlayerFbo.bindWrite(true);
            RenderSystem.clear(256, false);
            renderOverlayModel(guiGraphics, localPlayer, x, y, scale, yawOffset, zDepth, partialTick);
            guiGraphics.flush();
            extraPlayerFbo.unbindWrite();
            extraPlayerLastRenderTick = tick;
        }

        // 每帧贴 FBO 的 overlay 区域（UV 裁剪）
        RenderSystem.setShaderTexture(0, extraPlayerFbo.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = (float) x;
        float y0 = (float) y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        float u0 = x0 / screenW;
        float v0 = y0 / screenH;
        float u1 = x1 / screenW;
        float v1 = y1 / screenH;
        float z = (float) zDepth;
        buffer.addVertex(x0, y1, z).setUv(u0, v0);
        buffer.addVertex(x1, y1, z).setUv(u1, v0);
        buffer.addVertex(x1, y0, z).setUv(u1, v1);
        buffer.addVertex(x0, y0, z).setUv(u0, v1);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** 渲染玩家模型到当前渲染目标（GUI 投影一致；由调用方绑定 FBO）。 */
    private static void renderOverlayModel(GuiGraphics guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        RenderPass previousPass = RenderContext.enter(RenderPass.GUI_PREVIEW);
        float previewYaw = FRONT_FACING_YAW;
        try {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x + (scale * 0.5d), y + (scale * 2.0d), zDepth);
            guiGraphics.pose().scale(scale, scale, -scale);

            Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.1f);
            Quaternionf rotationY = Axis.YP.rotationDegrees(yawOffset - FRONT_FACING_YAW + 180.0f);
            rotationZ.mul(rotationY);
            guiGraphics.pose().mulPose(rotationZ);

            Lighting.setupForEntityInInventory();
            EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            rotationY.conjugate();
            entityRenderDispatcher.overrideCameraOrientation(rotationY);
            entityRenderDispatcher.setRenderShadow(false);

            boolean renderedCustomModel = PlayerCapability.get(localPlayer)
                    .filter(PlayerCapability::isModelActive)
                    .map(cap -> {
                        RenderSystem.runAsFancy(() -> RendererManager.getPlayerRenderer()
                                .render(localPlayer, previewYaw, partialTick, guiGraphics.pose(), guiGraphics.bufferSource(), 15728880));
                        return true;
                    })
                    .orElse(false);
            if (!renderedCustomModel) {
                RenderSystem.runAsFancy(() -> {
                    entityRenderDispatcher.render(localPlayer, 0.0d, 0.0d, 0.0d, previewYaw, partialTick, guiGraphics.pose(), guiGraphics.bufferSource(), 15728880);
                });
            }

            entityRenderDispatcher.setRenderShadow(true);
        } finally {
            guiGraphics.pose().popPose();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Lighting.setupFor3DItems();
            RenderContext.restore(previousPass);
        }
    }
}
