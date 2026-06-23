package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.core.compat.slashblade.SlashBladeRenderer;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.geo.GeoLayerRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.micaftic.morpher.util.accessors.BufferSourceAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;

public class CustomPlayerItemInHandLayer extends GeoLayerRenderer<CustomPlayerEntity> {

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
            boolean importedPlayerModel = entityLivingBaseIn.getModelAssembly().getAnimationBundle().isImportedPlayerModel();
            HumanoidArm mainArm = entity.getMainArm();
            HumanoidArm offArm = mainArm.getOpposite();
            if (hasHandAnchor(animatedGeoModel, mainArm)) {
                if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
                    SlashBladeRenderer.renderOnEntity(entity, animatedGeoModel, poseStack, bufferSource, packedLightIn, mainHandItem, partialTick);
                } else {
                    TacCompat.handleGunSound(entity, mainHandItem);
                    renderItem(animatedGeoModel, entity, mainHandItem, getDisplayContext(mainArm), mainArm, poseStack, bufferSource, packedLightIn, importedPlayerModel);
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
                        renderItem(animatedGeoModel, entity, offhandItem, getDisplayContext(offArm), offArm, poseStack, bufferSource, packedLightIn, importedPlayerModel);
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

    public void renderItem(AnimatedGeoModel model, LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, boolean importedPlayerModel) {
        if (!itemStack.isEmpty()) {
            boolean isLeftHand = humanoidArm == HumanoidArm.LEFT;
            boolean renderedDirectly = false;
            boolean hasDirectAnchor = hasDirectHandAnchor(model, humanoidArm);
            boolean hasChainAnchor = hasHandChainAnchor(model, humanoidArm);
            if (hasDirectAnchor || !hasChainAnchor) {
                poseStack.pushPose();
                if (hasDirectAnchor && applyItemBoneTransform(humanoidArm, poseStack, model, importedPlayerModel)) {
                    if (importedPlayerModel) {
                        applyFallbackHandTransform(poseStack);
                    }
                    this.itemRenderer.renderItem(livingEntity, itemStack, itemDisplayContext, isLeftHand, poseStack, multiBufferSource, i);
                } else {
                    applyFallbackHandTransform(poseStack);
                    if (SWarfareCompat.isGunItem(itemStack)) {
                        poseStack.translate(0.1d, 0.0d, 0.0d);
                        poseStack.scale(1.25f, 1.25f, 1.25f);
                    }
                    this.itemRenderer.renderItem(livingEntity, itemStack, itemDisplayContext, isLeftHand, poseStack, multiBufferSource, i);
                }
                poseStack.popPose();
                renderedDirectly = true;
            }
            if (renderedDirectly) {
                return;
            }
            getHandChains(model, humanoidArm).forEach(list -> {
                poseStack.pushPose();
                if (!list.isEmpty() && applyItemBoneTransform(poseStack, list, importedPlayerModel)) {
                    applyFallbackHandTransform(poseStack);
                    if (SWarfareCompat.isGunItem(itemStack)) {
                        poseStack.scale(1.25f, 1.25f, 1.25f);
                    }
                    this.itemRenderer.renderItem(livingEntity, itemStack, itemDisplayContext, isLeftHand, poseStack, multiBufferSource, i);
                }
                poseStack.popPose();
            });
        }
    }

    public boolean applyItemBoneTransform(HumanoidArm humanoidArm, PoseStack poseStack, AnimatedGeoModel model) {
        return applyItemBoneTransform(humanoidArm, poseStack, model, false);
    }

    private boolean applyItemBoneTransform(HumanoidArm humanoidArm, PoseStack poseStack, AnimatedGeoModel model, boolean importedPlayerModel) {
        if (!hasDirectHandAnchor(model, humanoidArm)) {
            return false;
        }
        if (humanoidArm == HumanoidArm.LEFT) {
            return applyItemBoneTransform(poseStack, model.leftHandBones(), importedPlayerModel);
        }
        return applyItemBoneTransform(poseStack, model.rightHandBones(), importedPlayerModel);
    }

    private boolean applyItemBoneTransform(PoseStack poseStack, java.util.List<? extends com.micaftic.morpher.geckolib3.core.processor.IBone> locatorHierarchy, boolean importedPlayerModel) {
        if (locatorHierarchy == null || locatorHierarchy.isEmpty()) {
            return false;
        }
        if (importedPlayerModel) {
            RenderUtils.prepMatrixForEquipmentLocator(poseStack, locatorHierarchy);
            return true;
        }
        return RenderUtils.prepMatrixForLocator(poseStack, locatorHierarchy);
    }

    private boolean hasHandAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return hasDirectHandAnchor(model, humanoidArm) || hasHandChainAnchor(model, humanoidArm);
    }

    private boolean hasDirectHandAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return humanoidArm == HumanoidArm.LEFT ? !model.leftHandBones().isEmpty() : !model.rightHandBones().isEmpty();
    }

    private boolean hasHandChainAnchor(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return getHandChains(model, humanoidArm).stream().anyMatch(list -> list != null && !list.isEmpty());
    }

    private java.util.List<java.util.List<com.micaftic.morpher.geckolib3.core.processor.IBone>> getHandChains(AnimatedGeoModel model, HumanoidArm humanoidArm) {
        return humanoidArm == HumanoidArm.LEFT ? model.rightHandChain() : model.leftHandChains();
    }

    private void applyFallbackHandTransform(PoseStack poseStack) {
        poseStack.translate(0.0d, -0.0625d, -0.1d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
    }

    private ItemDisplayContext getDisplayContext(HumanoidArm humanoidArm) {
        return humanoidArm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
