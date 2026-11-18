package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.core.compat.simplehats.SimpleHatsHelper;
import com.micaftic.morpher.client.renderer.SpmLayerRenderer;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.client.renderer.SpmRenderMath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CustomPlayerArmorLayer extends SpmLayerRenderer<CustomPlayerEntity> {

    private final ItemInHandRenderer itemRenderer;

    public CustomPlayerArmorLayer(EntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemInHandRenderer();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Player player = entityLivingBaseIn.getEntity();
        ModelScenePose model = entityLivingBaseIn.getCurrentModel();
        if (model != null && !model.headBones().isEmpty()) {
            ItemStack itemBySlot = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!itemBySlot.isEmpty() && !isArmorItem(itemBySlot)) {
                renderArmorPiece(poseStack, bufferSource, packedLightIn, model, player, itemBySlot);
            }
            ItemStack stack = SimpleHatsHelper.getHatItem(player);
            if (stack != null && !stack.isEmpty()) {
                renderArmorPiece(poseStack, bufferSource, packedLightIn, model, player, stack);
            }
        }
    }

    private boolean isArmorItem(ItemStack stack) {
        Item item = stack.getItem();
        return (item instanceof ArmorItem) && ((ArmorItem) item).getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    private void renderArmorPiece(PoseStack poseStack, MultiBufferSource bufferSource, int i, ModelScenePose model, Player player, ItemStack stack) {
        poseStack.pushPose();
        SpmRenderMath.prepMatrixForLocator(poseStack, model.headBones());
        poseStack.scale(0.625f, 0.625f, 0.625f);
        poseStack.translate(0.0f, 0.25f, 0.0f);
        this.itemRenderer.renderItem(player, stack, ItemDisplayContext.HEAD, false, poseStack, bufferSource, i);
        poseStack.popPose();
    }
}