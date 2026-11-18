package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.client.renderer.MultiBufferSource;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.client.renderer.SpmLayerRenderer;
import com.micaftic.morpher.client.renderer.SpmRenderMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** NeoForge's independent held-item layer for replaced Touhou Little Maid models. */
public final class MaidItemInHandLayer extends SpmLayerRenderer<MaidCapability> implements HeldItemLayer {
    private final ItemInHandRenderer itemRenderer;

    public MaidItemInHandLayer(ItemInHandRenderer itemRenderer) { this.itemRenderer = itemRenderer; }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       MaidCapability capability, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = capability.getEntity();
        ModelScenePose model = capability.getCurrentModel();
        if (model == null || capability.getModelAssembly() == null) return;
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();
        if (mainHand.isEmpty() && offHand.isEmpty()) return;
        HandLocatorProfile profile = capability.getModelAssembly().getAnimationBundle().getHandLocatorProfile();
        HumanoidArm mainArm = entity.getMainArm();
        renderHand(entity, model, profile, mainHand, mainArm, poseStack, packedLight);
        renderHand(entity, model, profile, offHand, mainArm.getOpposite(), poseStack, packedLight);
    }

    private void renderHand(LivingEntity entity, ModelScenePose model, HandLocatorProfile profile,
                            ItemStack item, HumanoidArm arm, PoseStack poseStack, int packedLight) {
        if (item.isEmpty() || !hasHandAnchor(model, arm)) return;
        ItemDisplayContext displayContext = arm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (hasDirectHandAnchor(model, arm)) {
            poseStack.pushPose();
            boolean transformed = applyItemBoneTransform(arm, poseStack, model, item, profile);
            if (!transformed || profile.usesVanillaUseOrientation()) applyFallbackHandTransform(poseStack);
            renderItem(entity, item, displayContext, poseStack, packedLight);
            poseStack.popPose();
            return;
        }
        List<List<ModelBoneView>> chains = arm == HumanoidArm.LEFT ? model.leftHandGroupChains() : model.rightHandGroupChains();
        for (List<ModelBoneView> chain : chains) {
            if (chain == null || chain.isEmpty()) continue;
            poseStack.pushPose();
            if (applyItemBoneTransform(poseStack, chain, profile)) {
                applyFallbackHandTransform(poseStack);
                renderItem(entity, item, displayContext, poseStack, packedLight);
            }
            poseStack.popPose();
        }
    }

    private void renderItem(LivingEntity entity, ItemStack item, ItemDisplayContext displayContext, PoseStack poseStack, int packedLight) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        if (collector != null) itemRenderer.renderItem(entity, item, displayContext, poseStack, collector, packedLight);
    }

    private boolean applyItemBoneTransform(HumanoidArm arm, PoseStack poseStack, ModelScenePose model, ItemStack item, HandLocatorProfile profile) {
        List<? extends ModelBoneView> locator = arm == HumanoidArm.LEFT ? model.leftHandBones() : model.rightHandBones();
        if (profile.usesSpecialHandLocatorSwordAnchor() && !item.isEmpty() && item.is(ItemTags.SWORDS)) {
            List<? extends ModelBoneView> swordLocator = arm == HumanoidArm.LEFT ? model.leftSwordBones() : model.rightSwordBones();
            if (swordLocator != null && !swordLocator.isEmpty()) locator = swordLocator;
        }
        return applyItemBoneTransform(poseStack, locator, profile);
    }

    private boolean applyItemBoneTransform(PoseStack poseStack, List<? extends ModelBoneView> locator, HandLocatorProfile profile) {
        if (locator == null || locator.isEmpty()) return false;
        if (profile.usesEquipmentLocatorTransform()) SpmRenderMath.prepMatrixForEquipmentLocator(poseStack, locator); else SpmRenderMath.prepMatrixForLocator(poseStack, locator);
        return true;
    }

    private boolean hasHandAnchor(ModelScenePose model, HumanoidArm arm) { return hasDirectHandAnchor(model, arm) || hasHandChainAnchor(model, arm); }
    private boolean hasDirectHandAnchor(ModelScenePose model, HumanoidArm arm) { return arm == HumanoidArm.LEFT ? !model.leftHandBones().isEmpty() : !model.rightHandBones().isEmpty(); }
    private boolean hasHandChainAnchor(ModelScenePose model, HumanoidArm arm) {
        List<List<ModelBoneView>> chains = arm == HumanoidArm.LEFT ? model.leftHandGroupChains() : model.rightHandGroupChains();
        return chains.stream().anyMatch(chain -> chain != null && !chain.isEmpty());
    }
    private void applyFallbackHandTransform(PoseStack poseStack) { poseStack.translate(0.0d, -0.0625d, -0.1d); poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f)); }

    @Override
    public void renderGltfThirdPersonItem(LivingEntity livingEntity, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (itemStack == null || itemStack.isEmpty()) return;
        renderItem(livingEntity, itemStack, humanoidArm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poseStack, packedLight);
    }
}
