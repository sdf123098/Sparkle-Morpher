package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.geckolib3.extended.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingRendererMixin extends EntityRenderer<LivingEntity> implements LivingEntityRendererAccessor {
    public LivingRendererMixin(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    @Unique
    public void tlm$renderNameTag(LivingEntity pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        if (this.shouldShowName(pEntity)) {
            this.renderNameTag(pEntity, pEntity.getDisplayName(), pPoseStack, pBuffer, pPackedLight, pPartialTick);
        }
    }
}