package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.core.compat.slashblade.SlashBladeRenderer;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.core.api.item.WeaponKind;
import com.micaftic.morpher.geckolib3.geo.GeoLayerRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.micaftic.morpher.util.accessors.BufferSourceAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.KineticWeapon;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import com.micaftic.morpher.util.ItemTagsConstants;

public class CustomPlayerItemInHandLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    // 原版装备（bbmodel）矛使用姿态参数：改这里只影响 bbmodel
    private static final float EQUIPMENT_SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES = 90.0f;
    private static final float EQUIPMENT_SPEAR_ENGAGED_FORWARD_DEGREES = 90.0f;
    private static final float EQUIPMENT_SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES = 60.0f;
    private static final float EQUIPMENT_SPEAR_DISENGAGED_DOWN_DEGREES = 6.0f;
    private static final float EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y = 0.125f;
    private static final float EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z = 0.125f;
    // 原版装备（bbmodel）矛空闲（非使用）挂点竖直度旋钮：默认 0 = 与长枪基座完全一致、零视觉变化。
    // 实机若发现矛没完全竖直，只调这三个值即可（仅影响 bbmodel 的矛，不碰 YSM）。
    private static final float EQUIPMENT_SPEAR_HOLD_PITCH_DEGREES = 0.0f;
    private static final float EQUIPMENT_SPEAR_HOLD_YAW_DEGREES = 0.0f;
    private static final float EQUIPMENT_SPEAR_HOLD_ROLL_DEGREES = 0.0f;

    // YSM 自定义骨骼矛使用姿态参数：与上面完全独立，改这里只影响 YSM
    private static final float AUTHORED_SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES = 90.0f;
    private static final float AUTHORED_SPEAR_ENGAGED_FORWARD_DEGREES = 90.0f;
    private static final float AUTHORED_SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES = 60.0f;
    private static final float AUTHORED_SPEAR_DISENGAGED_DOWN_DEGREES = 6.0f;
    private static final float AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y = 0.125f;
    private static final float AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z = 0.125f;

    private final ItemInHandRenderer itemRenderer;

    public CustomPlayerItemInHandLayer(ItemInHandRenderer itemInHandRenderer) {
        this.itemRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = entityLivingBaseIn.getEntity();
        AnimatedGeoModel animatedGeoModel = entityLivingBaseIn.getCurrentModel();
        if (animatedGeoModel == null) {
            return;
        }
        ItemStack offhandItem = entity.getOffhandItem();
        ItemStack mainHandItem = entity.getMainHandItem();
        if (!offhandItem.isEmpty() || !mainHandItem.isEmpty()) {
            poseStack.pushPose();
            boolean useExtraPlayer = entityLivingBaseIn.isRenderLayersFirst();
            HandLocatorProfile handLocatorProfile = entityLivingBaseIn.getModelAssembly().getAnimationBundle().getHandLocatorProfile();
            HumanoidArm mainArm = entity.getMainArm();
            HumanoidArm offArm = mainArm.getOpposite();
            if (hasHandAnchor(animatedGeoModel, mainArm)) {
                if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
                    SlashBladeRenderer.renderOnEntity(entity, animatedGeoModel, poseStack, bufferSource, packedLightIn, mainHandItem, partialTick);
                } else {
                    TacCompat.handleGunSound(entity, mainHandItem);
                    renderItem(animatedGeoModel, entity, mainHandItem, getDisplayContext(mainArm), mainArm, poseStack, bufferSource, packedLightIn, partialTick, handLocatorProfile);
                    if (useExtraPlayer && !mainHandItem.isEmpty() && (bufferSource instanceof BufferSourceAccessor)) {
                        ((BufferSourceAccessor) bufferSource).initialize();
                    }
                    TacCompat.handleItemSound(mainHandItem);
                }
            }
            if (hasHandAnchor(animatedGeoModel, offArm)) {
                if (SlashBladeCompat.isSlashBladeItem(offhandItem)) {
                    SlashBladeRenderer.renderRightWaist(animatedGeoModel, poseStack, bufferSource, packedLightIn, offhandItem);
                } else {
                    if (!SWarfareCompat.isGunItem(offhandItem)) {
                        renderItem(animatedGeoModel, entity, offhandItem, getDisplayContext(offArm), offArm, poseStack, bufferSource, packedLightIn, partialTick, handLocatorProfile);
                    }
                    if (useExtraPlayer && !offhandItem.isEmpty() && (bufferSource instanceof BufferSourceAccessor)) {
                        ((BufferSourceAccessor) bufferSource).initialize();
                    }
                }
            }
            poseStack.popPose();
            TacCompat.applyItemTransform(offhandItem, animatedGeoModel, entity, poseStack, packedLightIn, partialTick);
            SWarfareCompat.applyGunTransform(offhandItem, animatedGeoModel, entity, poseStack, packedLightIn, partialTick);
        }
    }

    public void renderItem(AnimatedGeoModel model, LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float partialTick, HandLocatorProfile handLocatorProfile) {
        if (!itemStack.isEmpty()) {
            boolean isLeftHand = humanoidArm == HumanoidArm.LEFT;
            boolean renderedDirectly = false;
            boolean hasDirectAnchor = hasDirectHandAnchor(model, humanoidArm);
            boolean hasChainAnchor = hasHandChainAnchor(model, humanoidArm);
            boolean vanillaEquipment = handLocatorProfile.usesVanillaUseOrientation();
            if (hasDirectAnchor || !hasChainAnchor) {
                poseStack.pushPose();
                if (hasDirectAnchor && applyItemBoneTransform(humanoidArm, poseStack, model, itemStack, handLocatorProfile)) {
                    if (vanillaEquipment) {
                        applyFallbackHandTransform(itemStack, poseStack, true, true);
                        if (SWarfareCompat.isGunItem(itemStack)) {
                            poseStack.translate(0.1d, 0.0d, 0.0d);
                            poseStack.scale(1.25f, 1.25f, 1.25f);
                        }
                        renderVanillaItemWithUseOrientation(livingEntity, itemStack, itemDisplayContext, humanoidArm, poseStack, i, partialTick, true);
                    } else {
                        applySpearUseItemTransformIfActive(livingEntity, itemStack, humanoidArm, poseStack, partialTick, false);
                        renderVanillaItem(livingEntity, itemStack, itemDisplayContext, humanoidArm, poseStack, i);
                    }
                } else {
                    applyFallbackHandTransform(itemStack, poseStack, true, vanillaEquipment);
                    if (SWarfareCompat.isGunItem(itemStack)) {
                        poseStack.translate(0.1d, 0.0d, 0.0d);
                        poseStack.scale(1.25f, 1.25f, 1.25f);
                    }
                    renderVanillaItemWithUseOrientation(livingEntity, itemStack, itemDisplayContext, humanoidArm, poseStack, i, partialTick, vanillaEquipment);
                }
                poseStack.popPose();
                renderedDirectly = true;
            }
            if (!renderedDirectly) {
                (isLeftHand ? model.leftHandChains() : model.rightHandChains()).forEach(list -> {
                    poseStack.pushPose();
                    if (list != null && !list.isEmpty() && applyItemLocatorTransform(itemStack, poseStack, list, handLocatorProfile)) {
                        applyFallbackHandTransform(itemStack, poseStack, false, vanillaEquipment);
                        if (SWarfareCompat.isGunItem(itemStack)) {
                            poseStack.scale(1.25f, 1.25f, 1.25f);
                        }
                        renderVanillaItemWithUseOrientation(livingEntity, itemStack, itemDisplayContext, humanoidArm, poseStack, i, partialTick, vanillaEquipment);
                    }
                    poseStack.popPose();
                });
            }
        }
    }

    private void applyFallbackHandTransform(ItemStack itemStack, PoseStack poseStack, boolean directHandBone, boolean vanillaEquipment) {
        if (vanillaEquipment) {
            applyEquipmentHandTransform(itemStack, poseStack, directHandBone);
        } else {
            applyAuthoredHandTransform(itemStack, poseStack, directHandBone);
        }
    }

    // ==================== 原版装备（bbmodel）挂点变换：独立一套 ====================
    private void applyEquipmentHandTransform(ItemStack itemStack, PoseStack poseStack, boolean directHandBone) {
        switch (InnerClassify.getWeaponKind(itemStack)) {
            case TRIDENT -> applyEquipmentTridentHandTransform(poseStack, directHandBone);
            case LANCE -> applyEquipmentLanceHandTransform(poseStack, directHandBone);
            case SPEAR -> applyEquipmentSpearHandTransform(poseStack, directHandBone);
            case MACE -> applyEquipmentMaceHandTransform(poseStack, directHandBone);
            case NONE -> applyEquipmentDefaultHandTransform(poseStack);
        }
    }

    private void applyEquipmentTridentHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyEquipmentDefaultHandTransform(poseStack);
        if (!directHandBone) {
            poseStack.translate(0.0d, 0.0d, -0.0125d);
        }
    }

    private void applyEquipmentLanceHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyEquipmentDefaultHandTransform(poseStack);
        poseStack.translate(0.0d, directHandBone ? -0.01875d : -0.0125d, -0.025d);
    }

    // 矛专属空闲挂点变换（bbmodel）：基座与长枪一致，另加竖直度旋钮（默认 0）。
    // 注意：本变换对空闲与使用都生效；使用时矛尖的平→竖→垂由 applyEquipmentSpearUseItemTransform 叠加。
    private void applyEquipmentSpearHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyEquipmentDefaultHandTransform(poseStack);
        poseStack.translate(0.0d, directHandBone ? -0.01875d : -0.0125d, -0.025d);
        if (EQUIPMENT_SPEAR_HOLD_PITCH_DEGREES != 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(EQUIPMENT_SPEAR_HOLD_PITCH_DEGREES));
        }
        if (EQUIPMENT_SPEAR_HOLD_YAW_DEGREES != 0.0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(EQUIPMENT_SPEAR_HOLD_YAW_DEGREES));
        }
        if (EQUIPMENT_SPEAR_HOLD_ROLL_DEGREES != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(EQUIPMENT_SPEAR_HOLD_ROLL_DEGREES));
        }
    }

    private void applyEquipmentMaceHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyEquipmentDefaultHandTransform(poseStack);
        poseStack.translate(0.0d, directHandBone ? -0.0125d : 0.0d, 0.01875d);
    }

    private void applyEquipmentDefaultHandTransform(PoseStack poseStack) {
        poseStack.translate(0.0d, -0.0625d, -0.1d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
    }

    // ==================== YSM 自定义骨骼挂点变换：独立一套 ====================
    private void applyAuthoredHandTransform(ItemStack itemStack, PoseStack poseStack, boolean directHandBone) {
        switch (InnerClassify.getWeaponKind(itemStack)) {
            case TRIDENT -> applyAuthoredTridentHandTransform(poseStack, directHandBone);
            case LANCE, SPEAR -> applyAuthoredLanceHandTransform(poseStack, directHandBone);
            case MACE -> applyAuthoredMaceHandTransform(poseStack, directHandBone);
            case NONE -> applyAuthoredDefaultHandTransform(poseStack);
        }
    }

    private void applyAuthoredTridentHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyAuthoredDefaultHandTransform(poseStack);
        if (!directHandBone) {
            poseStack.translate(0.0d, 0.0d, -0.0125d);
        }
    }

    private void applyAuthoredLanceHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyAuthoredDefaultHandTransform(poseStack);
        poseStack.translate(0.0d, directHandBone ? -0.01875d : -0.0125d, -0.025d);
    }

    private void applyAuthoredMaceHandTransform(PoseStack poseStack, boolean directHandBone) {
        applyAuthoredDefaultHandTransform(poseStack);
        poseStack.translate(0.0d, directHandBone ? -0.0125d : 0.0d, 0.01875d);
    }

    private void applyAuthoredDefaultHandTransform(PoseStack poseStack) {
        poseStack.translate(0.0d, -0.0625d, -0.1d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
    }

    private void renderVanillaItemWithUseOrientation(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm, PoseStack poseStack, int packedLight, float partialTick, boolean vanillaEquipment) {
        if (shouldNormalizeBowItemScale(itemStack)) {
            normalizeBowItemScale(poseStack);
        }
        if (shouldApplySpearUseItemTransform(livingEntity, itemStack, humanoidArm)) {
            applySpearUseItemTransform(livingEntity, itemStack, humanoidArm, poseStack, partialTick, vanillaEquipment);
        }
        renderVanillaItem(livingEntity, itemStack, itemDisplayContext, humanoidArm, poseStack, packedLight);
    }

    private boolean shouldNormalizeBowItemScale(ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.getUseAnimation() == ItemUseAnimation.BOW;
    }

    private void normalizeBowItemScale(PoseStack poseStack) {
        float scale = getLargestAxisScale(poseStack.last().pose());
        if (scale <= 1.0f || scale < 1.0E-4f) {
            return;
        }
        float inverseScale = 1.0f / scale;
        poseStack.scale(inverseScale, inverseScale, inverseScale);
    }

    private float getLargestAxisScale(Matrix4f matrix) {
        float xScale = length(matrix.m00(), matrix.m01(), matrix.m02());
        float yScale = length(matrix.m10(), matrix.m11(), matrix.m12());
        float zScale = length(matrix.m20(), matrix.m21(), matrix.m22());
        return Math.max(xScale, Math.max(yScale, zScale));
    }

    private float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private void applySpearUseItemTransform(LivingEntity livingEntity, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, float partialTick, boolean vanillaEquipment) {
        KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon == null) {
            return;
        }
        float ticksUsingItem = InputStateKey.getTicksUsingItem(livingEntity, partialTick);
        SpearUseParams useParams = getSpearUseParams(kineticWeapon, ticksUsingItem);
        float handSign = humanoidArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        if (vanillaEquipment) {
            applyEquipmentSpearUseItemTransform(poseStack, handSign, useParams);
        } else {
            applyAuthoredSpearUseItemTransform(poseStack, handSign, useParams);
        }
    }

    // 原版装备（bbmodel）矛使用姿态：独立一套
    private void applyEquipmentSpearUseItemTransform(PoseStack poseStack, float handSign, SpearUseParams useParams) {
        float engagedProgress = smoothStep(useParams.engagedProgress());
        float tiredProgress = smoothStep(useParams.tiredProgress());
        float disengagedProgress = smoothStep(useParams.disengagedProgress());
        float engagedTipPlaneCompensation = EQUIPMENT_SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES * (1.0f - engagedProgress);

        poseStack.rotateAround(
                Axis.XP.rotationDegrees(engagedTipPlaneCompensation),
                0.0f,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
        poseStack.rotateAround(
                Axis.YP.rotationDegrees(handSign * (EQUIPMENT_SPEAR_ENGAGED_FORWARD_DEGREES + EQUIPMENT_SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES * tiredProgress)),
                0.0f,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
        poseStack.rotateAround(
                Axis.XP.rotationDegrees(EQUIPMENT_SPEAR_DISENGAGED_DOWN_DEGREES * disengagedProgress),
                0.0f,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                EQUIPMENT_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
    }

    // YSM 自定义骨骼矛使用姿态：独立一套
    private void applyAuthoredSpearUseItemTransform(PoseStack poseStack, float handSign, SpearUseParams useParams) {
        float engagedProgress = smoothStep(useParams.engagedProgress());
        float tiredProgress = smoothStep(useParams.tiredProgress());
        float disengagedProgress = smoothStep(useParams.disengagedProgress());
        float engagedTipPlaneCompensation = AUTHORED_SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES * (1.0f - engagedProgress);

        poseStack.rotateAround(
                Axis.XP.rotationDegrees(engagedTipPlaneCompensation),
                0.0f,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
        poseStack.rotateAround(
                Axis.YP.rotationDegrees(handSign * (AUTHORED_SPEAR_ENGAGED_FORWARD_DEGREES + AUTHORED_SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES * tiredProgress)),
                0.0f,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
        poseStack.rotateAround(
                Axis.XP.rotationDegrees(AUTHORED_SPEAR_DISENGAGED_DOWN_DEGREES * disengagedProgress),
                0.0f,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Y,
                AUTHORED_SPEAR_THIRD_PERSON_DISPLAY_PIVOT_Z);
    }

    private SpearUseParams getSpearUseParams(KineticWeapon kineticWeapon, float ticksUsingItem) {
        int finishRaisingTick = kineticWeapon.delayTicks();
        int finishTiredTick = kineticWeapon.dismountConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + finishRaisingTick;
        int finishDisengagedTick = Math.max(
                finishTiredTick,
                kineticWeapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + finishRaisingTick);
        return new SpearUseParams(
                progress(ticksUsingItem, 0.0f, finishRaisingTick),
                progress(ticksUsingItem, finishRaisingTick, finishTiredTick),
                progress(ticksUsingItem, finishTiredTick, finishDisengagedTick));
    }

    private float progress(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0f : 0.0f;
        }
        return clamp01((value - start) / (end - start));
    }

    private float smoothStep(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private boolean shouldApplySpearUseItemTransform(LivingEntity livingEntity, ItemStack itemStack, HumanoidArm humanoidArm) {
        if (itemStack.isEmpty()
                || itemStack.getUseAnimation() != ItemUseAnimation.SPEAR
                || InnerClassify.getWeaponKind(itemStack) != WeaponKind.SPEAR
                || itemStack.get(DataComponents.KINETIC_WEAPON) == null) {
            return false;
        }
        InteractionHand renderedHand = getRenderedHand(livingEntity, humanoidArm);
        if (!InputStateKey.isUsingItem(livingEntity, renderedHand)) {
            return false;
        }
        return InputStateKey.getUsedItemHand(livingEntity) == renderedHand;
    }

    private InteractionHand getRenderedHand(LivingEntity livingEntity, HumanoidArm humanoidArm) {
        return humanoidArm == livingEntity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private ItemDisplayContext getDisplayContext(HumanoidArm humanoidArm) {
        return humanoidArm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private boolean hasHandAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return hasDirectHandAnchor(model, humanoidArm) || hasHandChainAnchor(model, humanoidArm);
    }

    private boolean hasDirectHandAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return humanoidArm == HumanoidArm.LEFT ? !model.leftHandBones().isEmpty() : !model.rightHandBones().isEmpty();
    }

    private boolean hasHandChainAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return (humanoidArm == HumanoidArm.LEFT ? model.leftHandChains() : model.rightHandChains())
                .stream()
                .anyMatch(list -> list != null && !list.isEmpty());
    }

    private void renderVanillaItem(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm, PoseStack poseStack, int packedLight) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        if (collector != null) {
            this.itemRenderer.renderItem(livingEntity, itemStack, itemDisplayContext, poseStack, collector, packedLight);
        }
    }

    public boolean applyItemBoneTransform(HumanoidArm humanoidArm, PoseStack poseStack, AnimatedGeoModel model) {
        return applyItemBoneTransform(humanoidArm, poseStack, model, ItemStack.EMPTY, HandLocatorProfile.YSM_AUTHORED);
    }

    private boolean applyItemBoneTransform(HumanoidArm humanoidArm, PoseStack poseStack, AnimatedGeoModel model, ItemStack itemStack, HandLocatorProfile handLocatorProfile) {
        if (!hasDirectHandAnchor(model, humanoidArm)) {
            return false;
        }
        if (shouldUseSpecialSwordAnchor(itemStack, handLocatorProfile)) {
            java.util.List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> swordLocator = humanoidArm == HumanoidArm.LEFT ? model.leftSwordBones() : model.rightSwordBones();
            if (swordLocator != null && !swordLocator.isEmpty()) {
                return applyItemLocatorTransform(itemStack, poseStack, swordLocator, handLocatorProfile, true);
            }
        }
        if (humanoidArm == HumanoidArm.LEFT) {
            return applyItemLocatorTransform(itemStack, poseStack, model.leftHandBones(), handLocatorProfile);
        }
        return applyItemLocatorTransform(itemStack, poseStack, model.rightHandBones(), handLocatorProfile);
    }

    private boolean applyItemLocatorTransform(ItemStack itemStack, PoseStack poseStack, java.util.List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> locatorHierarchy, HandLocatorProfile handLocatorProfile) {
        return applyItemLocatorTransform(itemStack, poseStack, locatorHierarchy, handLocatorProfile, false);
    }

    private boolean applyItemLocatorTransform(ItemStack itemStack, PoseStack poseStack, java.util.List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> locatorHierarchy, HandLocatorProfile handLocatorProfile, boolean ignoreHiddenLastScale) {
        if (locatorHierarchy == null || locatorHierarchy.isEmpty()) {
            return false;
        }
        if (handLocatorProfile.usesEquipmentLocatorTransform()) {
            RenderUtils.prepMatrixForEquipmentLocator(poseStack, locatorHierarchy);
            return true;
        }
        if (ignoreHiddenLastScale) {
            return RenderUtils.prepMatrixForLocatorIgnoringHiddenLastScale(poseStack, locatorHierarchy);
        }
        return RenderUtils.prepMatrixForLocator(poseStack, locatorHierarchy);
    }

    private boolean shouldUseSpecialSwordAnchor(ItemStack itemStack, HandLocatorProfile handLocatorProfile) {
        return handLocatorProfile.usesSpecialHandLocatorSwordAnchor() && isSwordItem(itemStack);
    }

    private boolean isSwordItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty()
                && (itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTagsConstants.SWORDS));
    }

    private void applySpearUseItemTransformIfActive(LivingEntity livingEntity, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, float partialTick, boolean vanillaEquipment) {
        if (shouldApplySpearUseItemTransform(livingEntity, itemStack, humanoidArm)) {
            applySpearUseItemTransform(livingEntity, itemStack, humanoidArm, poseStack, partialTick, vanillaEquipment);
        }
    }

    private record SpearUseParams(float engagedProgress, float tiredProgress, float disengagedProgress) {
    }
}
