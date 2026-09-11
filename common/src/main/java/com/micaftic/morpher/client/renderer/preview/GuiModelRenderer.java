package com.micaftic.morpher.client.renderer.preview;

import com.micaftic.morpher.client.animation.AnimationTracker;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RenderPass;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ExecutionException;

/**
 * GUI / Screen 内实体模型预览渲染（路线图 §22.1 拆分，1.2.6 Slice C）。
 *
 * <p>从 {@code ModelPreviewRenderer} 外提：背包、模型选择页、模型设置页等 GUI 内
 * 实体预览（含 vanilla 回退）。绘制走 {@link PreviewRenderBridge} 的 PIP 排队机制，
 * 请求在 {@code GuiEntityRendererMixin} 回调点执行。</p>
 *
 * <p>行为等价搬迁：方法体、调用顺序、状态保存/恢复与迁移前一致。渲染阶段与预览标志
 * 读写走 {@link RenderContext}（原 {@code ModelPreviewRenderer} 直接委托的就是它）。</p>
 */
public final class GuiModelRenderer {

    /** 额外玩家（经典 HUD 小人）头部相对身体的 yaw 上限（度）。保留给 head mode 功能项。 */
    private static final float EXTRA_PLAYER_HEAD_YAW_LIMIT = 85.0f;

    private GuiModelRenderer() {
    }

    /** MC 26.2 起 {@code Minecraft.renderBuffers()} 已移除，旧直取 BufferSource 路径恒不可用。 */
    public static MultiBufferSource.BufferSource getLegacyBufferSourceOrNull() {
        // 26.1.2 分支仍提供 Minecraft.renderBuffers()，保持该分支既有行为。
        return Minecraft.getInstance().renderBuffers().bufferSource();
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        renderLivingEntityPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, int mouseX, int mouseY) {
        if (guiGraphics == null || animatable == null || right <= left || bottom <= top || scale <= 0.0f) {
            return;
        }
        if (renderer == null) {
            renderVanillaLivingPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, animatable.getEntity(), disablePreviewRotation, mouseX, mouseY, false);
            return;
        }
        PreviewMath.MouseRotation mouseRotation = PreviewMath.mouseRotation(left, top, right, bottom, mouseX, mouseY, disablePreviewRotation);
        enqueueLivingPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, (disablePreviewRotation ? 180.0f : 200.0f) + mouseRotation.yaw(), mouseRotation.pitch(), false, DollOptions.GUI_PREVIEW);
    }

    /**
     * HUD 小人额外选项（§22.2 head mode + 载具对齐）。GUI 预览使用 {@link #GUI_PREVIEW}
     * （偏移为 0、不对齐载具），因此 GUI 预览行为与历史完全一致。
     */
    public record DollOptions(float headYawOffset, float headYawOffsetO, boolean alignWithVehicle) {
        public static final DollOptions GUI_PREVIEW = new DollOptions(0.0f, 0.0f, false);
    }

    /**
     * 为一个已就绪实体排队一份 GUI 预览请求并提交（PIP 回调时执行）。
     *
     * <p>供 GUI 预览（本类）与 HUD 小人预览共用，使请求类型保持私有、调用方无需
     * 触碰排队细节。{@code options} 承载小人专属的 head mode / 载具对齐。</p>
     */
    public static void enqueueLivingPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, LivingAnimatable<?> animatable, GeoReplacedEntityRenderer renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, float previewPitch, boolean extraPlayer, DollOptions options) {
        EntityRenderState state = new EntityRenderState();
        PreviewRenderBridge.enqueue(state, new LivingGuiPreviewRequest(
                PreviewMath.toModelOffset(originX, left, right, scale),
                PreviewMath.toModelOffset(originY, top, bottom, scale),
                partialTick,
                animatable,
                renderer,
                disablePreviewRotation,
                hideEquipment,
                previewYaw,
                previewPitch,
                extraPlayer,
                options == null ? DollOptions.GUI_PREVIEW : options
        ));
        guiGraphics.entity(state, scale, new Vector3f(), new Quaternionf(), null, left, top, right, bottom);
    }

    /** 经典 HUD 小人头部相对身体 yaw（当前帧），clamp 到 ±85°（head mode=FOLLOW 用）。 */
    public static float extraPlayerHeadYawOffset(LivingEntity entity) {
        return getExtraPlayerHeadYawOffset(entity);
    }

    /** 经典 HUD 小人头部相对身体 yaw（上一帧），clamp 到 ±85°（head mode=FOLLOW 用）。 */
    public static float extraPlayerHeadYawOffsetO(LivingEntity entity) {
        return getExtraPlayerHeadYawOffsetO(entity);
    }

    public static void renderEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        if (guiGraphics == null || animatableEntity == null || right <= left || bottom <= top || scale <= 0.0f) {
            return;
        }
        if (renderer == null) {
            if (animatableEntity.getEntity() instanceof LivingEntity livingEntity) {
                renderVanillaLivingPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, livingEntity, false, PreviewMath.mouseXFromYaw(originX, yaw), Math.round(originY - pitch), false);
            }
            return;
        }
        EntityRenderState state = new EntityRenderState();
        PreviewRenderBridge.enqueue(state, new FreeGuiPreviewRequest(
                PreviewMath.toModelOffset(originX, left, right, scale),
                PreviewMath.toModelOffset(originY, top, bottom, scale),
                pitch,
                yaw,
                partialTick,
                animatableEntity,
                renderer,
                renderGround
        ));
        guiGraphics.entity(state, scale, new Vector3f(), new Quaternionf(), null, left, top, right, bottom);
    }

    private static void renderVanillaLivingPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, LivingEntity livingEntity, boolean disablePreviewRotation, int mouseX, int mouseY, boolean extraPlayer) {
        if (livingEntity == null || right <= left || bottom <= top) {
            return;
        }
        int resolvedMouseX = mouseX == Integer.MIN_VALUE ? Math.round(originX) : mouseX;
        int resolvedMouseY = mouseY == Integer.MIN_VALUE ? Math.round(originY) : mouseY;
        if (disablePreviewRotation) {
            resolvedMouseX = Math.round((left + right) * 0.5f);
            resolvedMouseY = Math.round((top + bottom) * 0.5f);
        }
        boolean previousExtraPlayerMode = isExtraPlayer();
        setExtraPlayerMode(extraPlayer || previousExtraPlayerMode);
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(guiGraphics, left, top, right, bottom, Math.max(1, Math.round(scale)), resolvedMouseX, resolvedMouseY, 1.0f, livingEntity);
        } finally {
            setExtraPlayerMode(previousExtraPlayerMode);
        }
    }

    private static final class LivingGuiPreviewRequest implements PreviewRenderBridge.QueuedPreview {
        private final float offsetX;
        private final float offsetY;
        private final float partialTick;
        private final LivingAnimatable<?> animatable;
        private final GeoReplacedEntityRenderer renderer;
        private final boolean disablePreviewRotation;
        private final boolean hideEquipment;
        private final float previewYaw;
        private final float previewPitch;
        private final boolean extraPlayer;
        private final DollOptions options;

        private LivingGuiPreviewRequest(float offsetX, float offsetY, float partialTick, LivingAnimatable<?> animatable, GeoReplacedEntityRenderer renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, float previewPitch, boolean extraPlayer, DollOptions options) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.partialTick = partialTick;
            this.animatable = animatable;
            this.renderer = renderer;
            this.disablePreviewRotation = disablePreviewRotation;
            this.hideEquipment = hideEquipment;
            this.previewYaw = previewYaw;
            this.previewPitch = previewPitch;
            this.extraPlayer = extraPlayer;
            this.options = options;
        }

        @Override
        public void render(PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
            renderLivingGuiPreview(poseStack, offsetX, offsetY, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, previewYaw, previewPitch, extraPlayer, options, collector, bufferSource);
        }
    }

    private static final class FreeGuiPreviewRequest implements PreviewRenderBridge.QueuedPreview {
        private final float offsetX;
        private final float offsetY;
        private final float pitch;
        private final float yaw;
        private final float partialTick;
        private final AnimatableEntity animatableEntity;
        private final GeoReplacedEntityRenderer renderer;
        private final boolean renderGround;

        private FreeGuiPreviewRequest(float offsetX, float offsetY, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.pitch = pitch;
            this.yaw = yaw;
            this.partialTick = partialTick;
            this.animatableEntity = animatableEntity;
            this.renderer = renderer;
            this.renderGround = renderGround;
        }

        @Override
        public void render(PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
            renderFreeGuiPreview(poseStack, offsetX, offsetY, pitch, yaw, partialTick, animatableEntity, renderer, renderGround, bufferSource);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderLivingGuiPreview(PoseStack poseStack, float offsetX, float offsetY, float partialTick, LivingAnimatable animatable, GeoReplacedEntityRenderer renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, float previewPitch, boolean extraPlayer, DollOptions options, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
        ItemStack[] savedEquipment;
        boolean previousPreviewMode = isPreview();
        boolean previousExtraPlayerMode = isExtraPlayer();
        SubmitNodeCollector previousCollector = SubmitRenderContext.get();
        setPreviewMode(true);
        setExtraPlayerMode(extraPlayer || previousExtraPlayerMode);
        SubmitRenderContext.set(collector != null ? collector : previousCollector);
        LivingEntity livingEntity = (LivingEntity) animatable.getEntity();
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(disablePreviewRotation ? 0.0f : -10.0f + previewPitch);
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
        // head mode（§22.2）：默认 STRAIGHT 偏移为 0（等价历史行为）；FOLLOW 时为玩家真实头部偏移。
        float headYawOffset = options.headYawOffset();
        float headYawOffsetO = options.headYawOffsetO();
        if (hideEquipment && (livingEntity instanceof Player player)) {
            savedEquipment = new ItemStack[EquipmentSlot.values().length];
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                try {
                    savedEquipment[slotIndex] = player.getItemBySlot(equipmentSlot);
                } catch (Exception e) {
                    savedEquipment[slotIndex] = ItemStack.EMPTY;
                }
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        // Keep yRot/yRotO unchanged: YSM's input_vertical reads getViewYRot(),
        // which must remain the player's real movement-facing yaw during the preview.
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = previewYaw + headYawOffset;
        livingEntity.yHeadRotO = previewYaw + headYawOffsetO;

        Entity vehicle = livingEntity.getVehicle();
        // 经典 HUD 小人默认刻意跳过载具对齐（历史行为）；alignWithVehicle 显式开启才对
        // 齐（§22.2 载具项）。GUI/Screen 预览沿用 !extraPlayer 的原有条件。
        if (vehicle instanceof LivingEntity && (!extraPlayer || options.alignWithVehicle())) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - previewYaw));
            // 载具与 head mode 同时生效时，头部在载具基准上再叠加 head mode 偏移（默认 0，等价历史）。
            livingEntity.yHeadRot = vehicleYaw + headYawOffset;
            livingEntity.yHeadRotO = vehicleYaw + headYawOffsetO;
        }

        try {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            renderer.renderEntity(animatable, 0.0f, partialTick, poseStack, bufferSource, 15728880);
            if (bufferSource != null) {
                bufferSource.endBatch();
            }
        } finally {
            livingEntity.yBodyRot = oldBodyRot;
            livingEntity.yBodyRotO = oldBodyRotO;
            livingEntity.setYRot(oldYRot);
            livingEntity.yRotO = oldYRotO;
            livingEntity.setXRot(oldXRot);
            livingEntity.xRotO = oldXRotO;
            livingEntity.yHeadRotO = oldHeadRotO;
            livingEntity.yHeadRot = oldHeadRot;
            if (savedEquipment != null) {
                // Equipment restore skipped: MC 26.x no longer exposes the old mutable inventory fields.
            }
            poseStack.popPose();
            SubmitRenderContext.set(previousCollector);
            setExtraPlayerMode(previousExtraPlayerMode);
            setPreviewMode(previousPreviewMode);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderFreeGuiPreview(PoseStack poseStack, float offsetX, float offsetY, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround, MultiBufferSource.BufferSource bufferSource) {
        boolean previousPreviewMode = isPreview();
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, 0.0d);
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
        livingEntity.yBodyRot = -yaw;
        livingEntity.yBodyRotO = -yaw;
        livingEntity.setYRot(180.0f);
        livingEntity.yRotO = 180.0f;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = -yaw;
        livingEntity.yHeadRotO = -yaw;

        try {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            AnimationTracker animationTracker = getPreviewAnimationTracker(animatableEntity);
            if (isPreviewAnimation(animationTracker, "sleep")) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
                poseStack.translate(0.5d, 0.5625d, 0.0d);
                livingEntity.setPose(Pose.SLEEPING);
            }
            if (isPreviewAnimation(animationTracker, "swim") || isPreviewAnimation(animationTracker, "swim_stand")) {
                livingEntity.setPose(Pose.SWIMMING);
            }
            if (isPreviewAnimation(animationTracker, "sneak") || isPreviewAnimation(animationTracker, "sneaking")) {
                livingEntity.setPose(Pose.CROUCHING);
            }
            if (isPreviewAnimation(animationTracker, "sit")) {
                poseStack.translate(0.0d, -0.5d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "ride")) {
                poseStack.translate(0.0d, 0.85d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "ride_pig")) {
                poseStack.translate(0.0d, 0.3125d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "boat")) {
                poseStack.translate(0.0d, -0.45d, 0.0d);
            }
            try {
                if (bufferSource != null) {
                    PreviewSceneRenderer.renderVehicleForAnimation(yaw, animatableEntity, animationTracker, partialTick, poseStack, Minecraft.getInstance().getEntityRenderDispatcher(), bufferSource);
                }
                if (bufferSource != null && isPreviewAnimation(animationTracker, "sleep")) {
                    PreviewSceneRenderer.renderBedPreview(poseStack, yaw, bufferSource);
                }
                if (bufferSource != null && renderGround) {
                    PreviewSceneRenderer.renderGroundPreview(poseStack, yaw, bufferSource);
                }
                renderer.renderEntity((LivingAnimatable) animatableEntity, 0.0f, partialTick, poseStack, bufferSource, 15728880);
                if (bufferSource != null) {
                    bufferSource.endBatch();
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } finally {
            livingEntity.yBodyRot = oldBodyRot;
            livingEntity.yBodyRotO = oldBodyRotO;
            livingEntity.setYRot(oldYRot);
            livingEntity.yRotO = oldYRotO;
            livingEntity.setXRot(oldXRot);
            livingEntity.xRotO = oldXRotO;
            livingEntity.yHeadRotO = oldHeadRotO;
            livingEntity.yHeadRot = oldHeadRot;
            livingEntity.setPose(oldPose);
            poseStack.popPose();
            setPreviewMode(previousPreviewMode);
        }
    }

    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        if (!PreviewRenderBridge.isDirectGuiPreviewSupported()) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = getLegacyBufferSourceOrNull();
        if (bufferSource == null) {
            return;
        }
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();
        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1250.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);
        // MC 26.x: applyModelViewMatrix removed

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
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
        livingEntity.yBodyRot = -yaw;
        livingEntity.yBodyRotO = -yaw;
        livingEntity.setYRot(180.0f);
        livingEntity.yRotO = 180.0f;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = -yaw;
        livingEntity.yHeadRotO = -yaw;

        // MC 26.x: Lighting.setupForEntityInInventory() removed;
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        // MC 26.x: overrideCameraOrientation removed
        // entityRenderDispatcher.overrideCameraOrientation(rotationX);
        // MC 26.x: setRenderShadow removed
        // entityRenderDispatcher.setRenderShadow(false);
        { // MC 26.x: was RenderSystem.runAsFancy(() -> {
            AnimationTracker animationTracker = getPreviewAnimationTracker(animatableEntity);
            if (isPreviewAnimation(animationTracker, "sleep")) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
                poseStack.translate(0.5d, 0.5625d, 0.0d);
                livingEntity.setPose(Pose.SLEEPING);
            }
            if (isPreviewAnimation(animationTracker, "swim") || isPreviewAnimation(animationTracker, "swim_stand")) {
                livingEntity.setPose(Pose.SWIMMING);
            }
            if (isPreviewAnimation(animationTracker, "sneak") || isPreviewAnimation(animationTracker, "sneaking")) {
                livingEntity.setPose(Pose.CROUCHING);
            }
            if (isPreviewAnimation(animationTracker, "sit")) {
                poseStack.translate(0.0d, -0.5d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "ride")) {
                poseStack.translate(0.0d, 0.85d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "ride_pig")) {
                poseStack.translate(0.0d, 0.3125d, 0.0d);
            }
            if (isPreviewAnimation(animationTracker, "boat")) {
                poseStack.translate(0.0d, -0.45d, 0.0d);
            }
            try {
                PreviewSceneRenderer.renderVehicleForAnimation(yaw, animatableEntity, animationTracker, partialTick, poseStack, entityRenderDispatcher, bufferSource);
                if (isPreviewAnimation(animationTracker, "sleep")) {
                    PreviewSceneRenderer.renderBedPreview(scale, pitch, yaw, bufferSource);
                }
                if (renderGround) {
                    PreviewSceneRenderer.renderGroundPreview(scale, pitch, yaw, bufferSource);
                }
                bufferSource.endBatch();
                renderer.renderEntity((LivingAnimatable) animatableEntity, 0.0f, partialTick, poseStack, bufferSource, 15728880);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } // end was runAsFancy

        bufferSource.endBatch();
        // MC 26.x: setRenderShadow removed
        // entityRenderDispatcher.setRenderShadow(true);
        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        livingEntity.setPose(oldPose);

        modelViewStack.popMatrix();
        // MC 26.x: applyModelViewMatrix removed
        // MC 26.x: Lighting.setupFor3DItems() removed;
        setPreviewMode(false);
    }

    private static AnimationTracker getPreviewAnimationTracker(AnimatableEntity animatableEntity) {
        if (animatableEntity instanceof IPreviewAnimatable previewAnimatable) {
            return previewAnimatable.getAnimationStateMachine();
        }
        return null;
    }

    private static boolean isPreviewAnimation(AnimationTracker animationTracker, String animationName) {
        return animationTracker != null && animationTracker.isCurrentAnimation(animationName);
    }

    // 模型预览页面（旧直取 BufferSource 路径：MC 26.x 恒不可用，保留兼容签名）
    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        if (!PreviewRenderBridge.isDirectGuiPreviewSupported()) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = getLegacyBufferSourceOrNull();
        if (bufferSource == null) {
            return;
        }
        ItemStack[] savedEquipment;
        setPreviewMode(true);
        LivingEntity livingEntity = animatable.getEntity();
        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1050.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);
        // MC 26.x: applyModelViewMatrix removed

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(disablePreviewRotation ? 0.0f : -10.0f);
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
        // MC 26.x: Inventory.items/selected/offhand/armor are private/removed, skip equipment hiding
        if (hideEquipment && (livingEntity instanceof Player player)) {
            savedEquipment = new ItemStack[EquipmentSlot.values().length];
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                try {
                    savedEquipment[slotIndex] = player.getItemBySlot(equipmentSlot);
                } catch (Exception e) {
                    savedEquipment[slotIndex] = ItemStack.EMPTY;
                }
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        float previewYaw = disablePreviewRotation ? 180.0f : 200.0f;
        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        Entity vehicle = livingEntity.getVehicle();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - previewYaw));
            livingEntity.yHeadRot = vehicleYaw;
            livingEntity.yHeadRotO = vehicleYaw;
        }

        // MC 26.x: Lighting.setupForEntityInInventory() removed;
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        // MC 26.x: overrideCameraOrientation removed
        // entityRenderDispatcher.overrideCameraOrientation(rotationX);
        // MC 26.x: setRenderShadow removed
        // entityRenderDispatcher.setRenderShadow(false);
        { // MC 26.x: was RenderSystem.runAsFancy(() -> {
            renderer.renderEntity(animatable, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        } // end was runAsFancy

        bufferSource.endBatch();
        // MC 26.x: setRenderShadow removed
        // entityRenderDispatcher.setRenderShadow(true);
        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        // MC 26.x: Inventory fields private/removed, skip equipment restore
        if (savedEquipment != null) {
            // Player player = (Player) livingEntity;
            // Equipment restore skipped - Inventory API changed in MC 26.x
        }

        modelViewStack.popMatrix();
        // MC 26.x: applyModelViewMatrix removed
        // MC 26.x: Lighting.setupFor3DItems() removed;
        setPreviewMode(false);
    }

    private static boolean isPreview() {
        return RenderContext.isModelPreview();
    }

    private static float getExtraPlayerHeadYawOffset(LivingEntity entity) {
        return Mth.clamp(Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot), -EXTRA_PLAYER_HEAD_YAW_LIMIT, EXTRA_PLAYER_HEAD_YAW_LIMIT);
    }

    private static float getExtraPlayerHeadYawOffsetO(LivingEntity entity) {
        return Mth.clamp(Mth.wrapDegrees(entity.yHeadRotO - entity.yBodyRotO), -EXTRA_PLAYER_HEAD_YAW_LIMIT, EXTRA_PLAYER_HEAD_YAW_LIMIT);
    }

    private static void setPreviewMode(boolean previewMode) {
        RenderContext.setModelPreview(previewMode);
    }

    private static boolean isExtraPlayer() {
        return RenderContext.isOldHud();
    }

    private static void setExtraPlayerMode(boolean extraPlayerMode) {
        if (extraPlayerMode) {
            RenderContext.enter(RenderPass.OLD_HUD);
        } else {
            RenderContext.restore(RenderPass.WORLD);
        }
    }
}
