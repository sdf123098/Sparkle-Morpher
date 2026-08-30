package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.geckolib3.geo.GeoLayerRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** NeoForge's independent held-item layer for replaced Touhou Little Maid models. */
public final class MaidItemInHandLayer extends GeoLayerRenderer<MaidCapability> implements HeldItemLayer {
    private final ItemInHandRenderer itemRenderer;

    public MaidItemInHandLayer(ItemInHandRenderer itemRenderer) {
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       MaidCapability capability, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = capability.getEntity();
        AnimatedGeoModel model = capability.getCurrentModel();
        if (model == null || capability.getModelAssembly() == null) return;
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();
        if (mainHand.isEmpty() && offHand.isEmpty()) return;
        HandLocatorProfile profile = capability.getModelAssembly().getAnimationBundle().getHandLocatorProfile();
        HumanoidArm mainArm = entity.getMainArm();
        renderHand(entity, model, profile, mainHand, mainArm, poseStack, packedLight);
        renderHand(entity, model, profile, offHand, mainArm.getOpposite(), poseStack, packedLight);
    }

    private void renderHand(LivingEntity entity, AnimatedGeoModel model, HandLocatorProfile profile,
                            ItemStack item, HumanoidArm arm, PoseStack poseStack, int packedLight) {
        if (item.isEmpty() || !hasHandAnchor(model, arm)) return;
        ItemDisplayContext displayContext = arm == HumanoidArm.LEFT
                ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (hasDirectHandAnchor(model, arm)) {
            poseStack.pushPose();
            if (!applyItemBoneTransform(arm, poseStack, model, item, profile)) applyFallbackHandTransform(poseStack);
            renderItem(entity, item, displayContext, poseStack, packedLight);
            poseStack.popPose();
            return;
        }
        List<List<com.micaftic.morpher.geckolib3.core.processor.IBone>> chains =
                arm == HumanoidArm.LEFT ? model.leftHandChains() : model.rightHandChains();
        for (List<com.micaftic.morpher.geckolib3.core.processor.IBone> chain : chains) {
            if (chain == null || chain.isEmpty()) continue;
            poseStack.pushPose();
            if (applyItemBoneTransform(poseStack, chain, profile)) {
                applyFallbackHandTransform(poseStack);
                renderItem(entity, item, displayContext, poseStack, packedLight);
            }
            poseStack.popPose();
        }
    }

    private void renderItem(LivingEntity entity, ItemStack item, ItemDisplayContext displayContext,
                            PoseStack poseStack, int packedLight) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        if (collector != null) itemRenderer.renderItem(entity, item, displayContext, poseStack, collector, packedLight);
    }

    private boolean applyItemBoneTransform(HumanoidArm arm, PoseStack poseStack, AnimatedGeoModel model,
                                           ItemStack item, HandLocatorProfile profile) {
        List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> locator =
                arm == HumanoidArm.LEFT ? model.leftHandBones() : model.rightHandBones();
        if (profile.usesSpecialHandLocatorSwordAnchor() && !item.isEmpty() && item.is(ItemTags.SWORDS)) {
            List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> swordLocator =
                    arm == HumanoidArm.LEFT ? model.leftSwordBones() : model.rightSwordBones();
            if (swordLocator != null && !swordLocator.isEmpty()) locator = swordLocator;
        }
        return applyItemBoneTransform(poseStack, locator, profile);
    }

    private boolean applyItemBoneTransform(PoseStack poseStack,
                                           List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> locator,
                                           HandLocatorProfile profile) {
        if (locator == null || locator.isEmpty()) return false;
        if (profile.usesEquipmentLocatorTransform()) RenderUtils.prepMatrixForEquipmentLocator(poseStack, locator);
        else RenderUtils.prepMatrixForLocator(poseStack, locator);
        return true;
    }

    private boolean hasHandAnchor(AnimatedGeoModel model, HumanoidArm arm) {
        return hasDirectHandAnchor(model, arm) || hasHandChainAnchor(model, arm);
    }

    private boolean hasDirectHandAnchor(AnimatedGeoModel model, HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? !model.leftHandBones().isEmpty() : !model.rightHandBones().isEmpty();
    }

    private boolean hasHandChainAnchor(AnimatedGeoModel model, HumanoidArm arm) {
        List<List<com.micaftic.morpher.geckolib3.core.processor.IBone>> chains =
                arm == HumanoidArm.LEFT ? model.leftHandChains() : model.rightHandChains();
        return chains.stream().anyMatch(chain -> chain != null && !chain.isEmpty());
    }

    private void applyFallbackHandTransform(PoseStack poseStack) {
        poseStack.translate(0.0d, -0.0625d, -0.1d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
    }

    @Override
    public void renderGltfThirdPersonItem(LivingEntity livingEntity, ItemStack itemStack, HumanoidArm humanoidArm,
                                          PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                          float partialTick) {
        if (itemStack == null || itemStack.isEmpty()) return;
        renderItem(livingEntity, itemStack,
                humanoidArm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                poseStack, packedLight);
    }
}
