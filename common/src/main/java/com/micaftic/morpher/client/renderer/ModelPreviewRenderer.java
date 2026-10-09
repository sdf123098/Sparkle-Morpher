package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RenderPass;
import com.micaftic.morpher.client.renderer.preview.GuiModelRenderer;
import com.micaftic.morpher.client.renderer.preview.PaperDollRenderer;
import com.micaftic.morpher.client.renderer.preview.PreviewRenderBridge;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 模型预览渲染的兼容 facade（路线图 §22.1 拆分，1.2.6）。
 *
 * <p>公开 API 一字未变，内部实现已按职责拆到 {@code client.renderer.preview}：
 * {@link PreviewRenderBridge}（GUI 预览排队/消费与桥门控）、
 * {@link GuiModelRenderer}（GUI/Screen 内实体预览与 vanilla 回退）、
 * {@link PaperDollRenderer}（经典 HUD 小人入口）、
 * {@code PreviewMath}（预览投影数学）、
 * {@code PreviewSceneRenderer}（床/地面/载具预览家具）。</p>
 *
 * <p>本类保留：渲染阶段标志（{@link RenderContext} 委托）、第一人称/世界渲染判定、
 * 载具模型矩阵。所有预览/小人渲染入口均转发到上述实现，调用点无需改动。</p>
 */
public final class ModelPreviewRenderer {

    // Animation evaluation runs on worker threads during a world render. Unlike the preview
    // modes, this frame-scoped flag must therefore be visible across threads.
    private static volatile boolean worldRenderMode;

    public static void setPreviewMode(boolean previewMode) {
        RenderContext.setModelPreview(previewMode);
    }

    public static boolean isPreview() {
        return RenderContext.isModelPreview();
    }

    public static void setGuiPreviewEntity(Entity entity, float partialTick) {
        PreviewRenderBridge.setGuiPreviewEntity(entity, partialTick);
    }

    public static Entity getAndClearGuiPreviewEntity() {
        return PreviewRenderBridge.getAndClearGuiPreviewEntity();
    }

    public static float getGuiPreviewPartialTick() {
        return PreviewRenderBridge.getGuiPreviewPartialTick();
    }

    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        if (extraPlayerMode) { RenderContext.enter(RenderPass.OLD_HUD); } else { RenderContext.restore(RenderPass.WORLD); }
    }

    public static boolean isExtraPlayer() {
        return RenderContext.isOldHud();
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        if (firstPersonMode) {
            RenderContext.enter(RenderPass.FIRST_PERSON);
        } else {
            RenderContext.restore(RenderPass.WORLD);
        }
    }

    public static void setWorldRenderMode(boolean worldRenderMode) {
        ModelPreviewRenderer.worldRenderMode = worldRenderMode;
    }

    public static boolean isWorldRender() {
        return worldRenderMode;
    }

    public static boolean isFirstPerson() {
        return RenderContext.isFirstPerson() || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        return RenderContext.isFirstPerson()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && !FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isDirectGuiPreviewSupported() {
        return PreviewRenderBridge.isDirectGuiPreviewSupported();
    }

    public static MultiBufferSource.BufferSource getLegacyBufferSourceOrNull() {
        return GuiModelRenderer.getLegacyBufferSourceOrNull();
    }

    public static boolean renderQueuedGuiPreview(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
        return PreviewRenderBridge.renderQueued(renderState, poseStack, collector, bufferSource);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        GuiModelRenderer.renderLivingEntityPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, int mouseX, int mouseY) {
        GuiModelRenderer.renderLivingEntityPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, mouseX, mouseY);
    }

    public static void renderEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        GuiModelRenderer.renderEntityPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, pitch, yaw, partialTick, animatableEntity, renderer, renderGround);
    }

    public static void renderVehicleModel(Entity entity, PoseStack poseStack, float partialTick) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && !GeckoVehicleEntity.usesVanillaRenderer(vehicle)) {
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
                poseStack.translate(0.0d, myRidingOffset, 0.0d);
            });
        }
    }

    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        GuiModelRenderer.renderEntityPreview(x, y, scale, pitch, yaw, partialTick, animatableEntity, renderer, renderGround);
    }

    // 模型预览页面（旧直取 BufferSource 路径，MC 26.x 恒不可用；保留兼容签名）
    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        GuiModelRenderer.renderLivingEntityPreview(x, y, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment);
    }

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        PaperDollRenderer.renderPlayerOverlay(guiGraphics, localPlayer, x, y, scale, yawOffset, zDepth, partialTick);
    }

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick, boolean clipToFrame) {
        PaperDollRenderer.renderPlayerOverlay(guiGraphics, localPlayer, x, y, scale, yawOffset, zDepth, partialTick, clipToFrame);
    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer) {
        return PaperDollRenderer.renderCustomLocalPlayerPreview(guiGraphics, localPlayer, left, top, right, bottom, originX, originY, scale, yaw, partialTick, extraPlayer);
    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer, int mouseX, int mouseY) {
        return PaperDollRenderer.renderCustomLocalPlayerPreview(guiGraphics, localPlayer, left, top, right, bottom, originX, originY, scale, yaw, partialTick, extraPlayer, mouseX, mouseY);
    }
}
