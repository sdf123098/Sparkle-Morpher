package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.client.animation.AnimationTracker;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.mixin.client.EntityRidingAccessor;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.util.AnimatableCacheUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import com.mojang.math.Axis;

public final class ModelPreviewRenderer {

    // The old YSM preview renderer draws immediately. MC 26.x extracts GUI draw
    // commands first, so direct entity preview rendering corrupts later GUI batches.
    private static final boolean DIRECT_GUI_PREVIEWS_SUPPORTED = false;

    private static final float MODEL_PREVIEW_MOUSE_YAW_DEGREES = 25.0f;

    private static final float MODEL_PREVIEW_MOUSE_PITCH_DEGREES = 15.0f;

    private static final float MODEL_PREVIEW_MOUSE_DEADZONE = 0.02f;

    private static final float EXTRA_PLAYER_HEAD_YAW_LIMIT = 85.0f;

    private static final PreviewMouseRotation NO_MOUSE_ROTATION = new PreviewMouseRotation(0.0f, 0.0f);

    private static final float GRASS_R = 0.26f;
    private static final float GRASS_G = 0.55f;
    private static final float GRASS_B = 0.22f;
    private static final float DIRT_R = 0.32f;
    private static final float DIRT_G = 0.22f;
    private static final float DIRT_B = 0.13f;
    private static final float BED_RED_R = 0.72f;
    private static final float BED_RED_G = 0.08f;
    private static final float BED_RED_B = 0.07f;
    private static final float BED_WHITE_R = 0.92f;
    private static final float BED_WHITE_G = 0.88f;
    private static final float BED_WHITE_B = 0.78f;

    private static final Map<EntityRenderState, GuiPreviewRequest> GUI_PREVIEWS = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final ThreadLocal<Boolean> PREVIEW_MODE = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> EXTRA_PLAYER_MODE = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> FIRST_PERSON_MODE = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> WORLD_RENDER_MODE = ThreadLocal.withInitial(() -> false);

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

    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        EXTRA_PLAYER_MODE.set(extraPlayerMode);
    }

    public static boolean isExtraPlayer() {
        return EXTRA_PLAYER_MODE.get();
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        FIRST_PERSON_MODE.set(firstPersonMode);
    }

    public static void setWorldRenderMode(boolean worldRenderMode) {
        WORLD_RENDER_MODE.set(worldRenderMode);
    }

    public static boolean isWorldRender() {
        return WORLD_RENDER_MODE.get();
    }

    public static boolean isFirstPerson() {
        return FIRST_PERSON_MODE.get() || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        return FIRST_PERSON_MODE.get()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && !FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isDirectGuiPreviewSupported() {
        return DIRECT_GUI_PREVIEWS_SUPPORTED;
    }

    public static boolean renderQueuedGuiPreview(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
        GuiPreviewRequest request = GUI_PREVIEWS.remove(renderState);
        if (request == null) {
            return false;
        }
        request.render(poseStack, collector, bufferSource);
        return true;
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        renderLivingEntityPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment, int mouseX, int mouseY) {
        if (guiGraphics == null || animatable == null || renderer == null || right <= left || bottom <= top) {
            return;
        }
        PreviewMouseRotation mouseRotation = getPreviewMouseRotation(left, top, right, bottom, mouseX, mouseY, disablePreviewRotation);
        EntityRenderState state = new EntityRenderState();
        GUI_PREVIEWS.put(state, new LivingGuiPreviewRequest(
                toModelOffset(originX, left, right, scale),
                toModelOffset(originY, top, bottom, scale),
                partialTick,
                animatable,
                renderer,
                disablePreviewRotation,
                hideEquipment,
                (disablePreviewRotation ? 180.0f : 200.0f) + mouseRotation.yaw,
                mouseRotation.pitch,
                false
        ));
        guiGraphics.entity(state, scale, new Vector3f(), new Quaternionf(), null, left, top, right, bottom);
    }

    public static void renderEntityPreview(GuiGraphicsExtractor guiGraphics, int left, int top, int right, int bottom, float originX, float originY, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        if (guiGraphics == null || animatableEntity == null || renderer == null || right <= left || bottom <= top) {
            return;
        }
        EntityRenderState state = new EntityRenderState();
        GUI_PREVIEWS.put(state, new FreeGuiPreviewRequest(
                toModelOffset(originX, left, right, scale),
                toModelOffset(originY, top, bottom, scale),
                pitch,
                yaw,
                partialTick,
                animatableEntity,
                renderer,
                renderGround
        ));
        guiGraphics.entity(state, scale, new Vector3f(), new Quaternionf(), null, left, top, right, bottom);
    }

    private static float toModelOffset(float origin, int start, int end, float scale) {
        return (origin - ((start + end) * 0.5f)) / Math.max(1.0f, scale);
    }

    private interface GuiPreviewRequest {
        void render(PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource);
    }

    private static final class LivingGuiPreviewRequest implements GuiPreviewRequest {
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

        private LivingGuiPreviewRequest(float offsetX, float offsetY, float partialTick, LivingAnimatable<?> animatable, GeoReplacedEntityRenderer renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, float previewPitch, boolean extraPlayer) {
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
        }

        @Override
        public void render(PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
            renderLivingGuiPreview(poseStack, offsetX, offsetY, partialTick, animatable, renderer, disablePreviewRotation, hideEquipment, previewYaw, previewPitch, extraPlayer, collector, bufferSource);
        }
    }

    private static final class FreeGuiPreviewRequest implements GuiPreviewRequest {
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
    private static void renderLivingGuiPreview(PoseStack poseStack, float offsetX, float offsetY, float partialTick, LivingAnimatable animatable, GeoReplacedEntityRenderer renderer, boolean disablePreviewRotation, boolean hideEquipment, float previewYaw, float previewPitch, boolean extraPlayer, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
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
        float headYawOffset = extraPlayer ? getExtraPlayerHeadYawOffset(livingEntity) : 0.0f;
        float headYawOffsetO = extraPlayer ? getExtraPlayerHeadYawOffsetO(livingEntity) : 0.0f;
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
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = livingEntity.getYRot() + headYawOffset;
        livingEntity.yHeadRotO = livingEntity.getYRot() + headYawOffsetO;

        Entity vehicle = livingEntity.getVehicle();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - previewYaw));
            livingEntity.yHeadRot = vehicleYaw;
            livingEntity.yHeadRotO = vehicleYaw;
        }

        try {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            renderer.renderEntity(animatable, 0.0f, partialTick, poseStack, bufferSource, 15728880);
            bufferSource.endBatch();
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
                renderVehicleForAnimation(yaw, animatableEntity, animationTracker, partialTick, poseStack, Minecraft.getInstance().getEntityRenderDispatcher(), bufferSource);
                if (isPreviewAnimation(animationTracker, "sleep")) {
                    renderBedPreview(poseStack, yaw, bufferSource);
                }
                if (renderGround) {
                    renderGroundPreview(poseStack, yaw, bufferSource);
                }
                renderer.renderEntity((LivingAnimatable) animatableEntity, 0.0f, partialTick, poseStack, bufferSource, 15728880);
                bufferSource.endBatch();
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
                Vec3 passengerAttachment = ((EntityRidingAccessor) vehicle).invokeGetPassengerAttachmentPoint(entity, entity.getDimensions(entity.getPose()), 1.0F);
                double myRidingOffset = -passengerAttachment.y();
                poseStack.translate(0.0d, myRidingOffset, 0.0d);
            });
        }
    }

    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        if (!isDirectGuiPreviewSupported()) {
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
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

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
                renderVehicleForAnimation(yaw, animatableEntity, animationTracker, partialTick, poseStack, entityRenderDispatcher, bufferSource);
                if (isPreviewAnimation(animationTracker, "sleep")) {
                    renderBedPreview(scale, pitch, yaw, bufferSource);
                }
                if (renderGround) {
                    renderGroundPreview(scale, pitch, yaw, bufferSource);
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

    private static void renderBedPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        renderSimpleBed(poseStack, bufferSource);
    }

    private static void renderBedPreview(PoseStack poseStack, float yaw, MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        renderSimpleBed(poseStack, bufferSource);
        poseStack.popPose();
    }

    private static void renderGroundPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        renderSimpleGround(poseStack, bufferSource);
    }

    private static void renderGroundPreview(PoseStack poseStack, float yaw, MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        renderSimpleGround(poseStack, bufferSource);
        poseStack.popPose();
    }

    private static void renderSimpleGround(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                float shade = ((x + z) & 1) == 0 ? 1.0f : 0.88f;
                addTopQuad(buffer, pose, x, z, x + 1.0f, z + 1.0f, GRASS_R * shade, GRASS_G * shade, GRASS_B * shade);
                addSideQuad(buffer, pose, x, z, x + 1.0f, z + 1.0f, DIRT_R, DIRT_G, DIRT_B);
            }
        }
        addPlantCross(buffer, pose, 0.55f, 1.45f, 1.0f, 0.28f, 0.64f, 0.18f);
        addPlantCross(buffer, pose, 1.85f, 2.2f, 1.0f, 0.75f, 0.08f, 0.08f);
    }

    private static void renderSimpleBed(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        addCuboid(buffer, pose, 0.0f, 0.0f, 0.0f, 1.0f, 0.28f, 1.0f, BED_RED_R, BED_RED_G, BED_RED_B);
        addCuboid(buffer, pose, 0.0f, 0.29f, 0.0f, 1.0f, 0.34f, 0.35f, BED_WHITE_R, BED_WHITE_G, BED_WHITE_B);
    }

    private static void addTopQuad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, 1.0f, z1, x2, 1.0f, z1, x2, 1.0f, z2, x1, 1.0f, z2, r, g, b, 0.0f, 1.0f, 0.0f);
    }

    private static void addSideQuad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, 0.85f, z1, x2, 0.85f, z1, x2, 1.0f, z1, x1, 1.0f, z1, r, g, b, 0.0f, 0.0f, -1.0f);
        addQuad(buffer, pose, x2, 0.85f, z1, x2, 0.85f, z2, x2, 1.0f, z2, x2, 1.0f, z1, r, g, b, 1.0f, 0.0f, 0.0f);
    }

    private static void addPlantCross(VertexConsumer buffer, PoseStack.Pose pose, float x, float z, float y, float r, float g, float b) {
        addQuad(buffer, pose, x - 0.12f, y, z, x + 0.12f, y, z, x + 0.12f, y + 0.45f, z, x - 0.12f, y + 0.45f, z, r, g, b, 0.0f, 0.0f, 1.0f);
        addQuad(buffer, pose, x, y, z - 0.12f, x, y, z + 0.12f, x, y + 0.45f, z + 0.12f, x, y + 0.45f, z - 0.12f, r, g, b, 1.0f, 0.0f, 0.0f);
    }

    private static void addCuboid(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, 0.0f, 1.0f, 0.0f);
        addQuad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r * 0.55f, g * 0.55f, b * 0.55f, 0.0f, -1.0f, 0.0f);
        addQuad(buffer, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r * 0.72f, g * 0.72f, b * 0.72f, -1.0f, 0.0f, 0.0f);
        addQuad(buffer, pose, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, r * 0.72f, g * 0.72f, b * 0.72f, 1.0f, 0.0f, 0.0f);
        addQuad(buffer, pose, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r * 0.65f, g * 0.65f, b * 0.65f, 0.0f, 0.0f, -1.0f);
        addQuad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r * 0.65f, g * 0.65f, b * 0.65f, 0.0f, 0.0f, 1.0f);
    }

    private static void addQuad(VertexConsumer buffer, PoseStack.Pose pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b,
                                float nx, float ny, float nz) {
        buffer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x3, y3, z3).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x4, y4, z4).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
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

    private static void renderVehicleForAnimation(float yaw, AnimatableEntity animatableEntity, AnimationTracker animationTracker, float partialTick, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource) throws ExecutionException {
        if (animationTracker == null) {
            return;
        }
        Entity entity = animatableEntity.getEntity();

        if (animationTracker.isCurrentAnimation("ride")) {
            // MC 26.x: EntityType.create needs EntitySpawnReason, BOAT removed
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.HORSE), () -> EntityType.HORSE.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.PIG), () -> EntityType.PIG.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.BOAT), () -> EntityType.BOAT.create(entity.level())), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource, Entity vehicleEntity, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        Vec3 passengerAttachment = ((EntityRidingAccessor) vehicleEntity).invokeGetPassengerAttachmentPoint(riderEntity, riderEntity.getDimensions(riderEntity.getPose()), 1.0F);
        // MC 26.x: EntityRenderDispatcher.render() signature changed
        // entityRenderDispatcher.render(vehicleEntity, 0.0d, passengerAttachment.y(), 0.0d, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        poseStack.popPose();
    }

    // 模型预览页面
    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        if (!isDirectGuiPreviewSupported()) {
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
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

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

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        renderPlayerOverlay(guiGraphics, localPlayer, x, y, scale, yawOffset, zDepth, partialTick, true);
    }

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick, boolean clipToFrame) {
        if (guiGraphics == null || localPlayer == null || scale <= 0.0f) {
            return;
        }
        int left = Math.round((float) x);
        int top = Math.round((float) y);
        int right = Math.round((float) (x + scale));
        int bottom = Math.round((float) (y + (scale * 2.0f)));
        if (right <= left || bottom <= top) {
            return;
        }
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        int renderLeft = left;
        int renderTop = top;
        int renderRight = right;
        int renderBottom = bottom;
        if (!clipToFrame) {
            Minecraft minecraft = Minecraft.getInstance();
            renderLeft = Math.min(0, left);
            renderTop = Math.min(0, top);
            renderRight = Math.max(minecraft.getWindow().getGuiScaledWidth(), right);
            renderBottom = Math.max(minecraft.getWindow().getGuiScaledHeight(), bottom);
        }
        if (renderCustomLocalPlayerPreview(guiGraphics, localPlayer, renderLeft, renderTop, renderRight, renderBottom, centerX, bottom - 2.0f, scale, 180.0f + yawOffset, partialTick, true)) {
            return;
        }
        float mouseX = centerX - ((float) Math.tan(yawOffset / 20.0f) * 40.0f);
        setExtraPlayerMode(true);
        if (clipToFrame) {
            guiGraphics.enableScissor(left, top, right, bottom);
        }
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(guiGraphics, left, top, right, bottom, Math.max(1, Math.round(scale)), mouseX, centerY, 1.0f, localPlayer);
        } finally {
            if (clipToFrame) {
                guiGraphics.disableScissor();
            }
            setExtraPlayerMode(false);
        }

    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer) {
        return renderCustomLocalPlayerPreview(guiGraphics, localPlayer, left, top, right, bottom, originX, originY, scale, yaw, partialTick, extraPlayer, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer, int mouseX, int mouseY) {
        if (guiGraphics == null || localPlayer == null || right <= left || bottom <= top || scale <= 0.0f || GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_MODEL)) {
            return false;
        }
        PlayerCapability capability = PlayerCapability.get(localPlayer).orElse(null);
        if (capability == null || !capability.isModelActive()) {
            return false;
        }
        capability.tickModel();
        if (!capability.isModelReady()) {
            return false;
        }
        PreviewMouseRotation mouseRotation = getPreviewMouseRotation(left, top, right, bottom, mouseX, mouseY, extraPlayer);
        EntityRenderState state = new EntityRenderState();
        GUI_PREVIEWS.put(state, new LivingGuiPreviewRequest(
                toModelOffset(originX, left, right, scale),
                toModelOffset(originY, top, bottom, scale),
                partialTick,
                capability,
                RendererManager.getPlayerRenderer(),
                false,
                false,
                yaw + mouseRotation.yaw,
                mouseRotation.pitch,
                extraPlayer
        ));
        guiGraphics.entity(state, scale, new Vector3f(), new Quaternionf(), null, left, top, right, bottom);
        return true;
    }
}
