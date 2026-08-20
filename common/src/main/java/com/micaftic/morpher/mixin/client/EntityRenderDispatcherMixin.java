package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.event.ClientResourceLifecycleEvent;
import com.micaftic.morpher.client.renderer.CustomFishingHookRenderer;
import com.micaftic.morpher.client.renderer.CustomVehicleRenderer;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.CustomProjectileRenderer;
import com.micaftic.morpher.client.renderer.MaidEntityRenderer;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void ysm$captureEntity(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state != null) {
            int packedLight = ((EntityRenderDispatcher) (Object) this).getPackedLightCoords(entity, partialTick);
            ClientResourceLifecycleEvent.captureEntity(state, entity, partialTick, packedLight);
        }
    }

    @WrapWithCondition(method = {"submit"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")})
    private boolean ysm$renderCustom(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        ClientResourceLifecycleEvent.CapturedEntity captured = ClientResourceLifecycleEvent.consumeCapturedEntity(state);
        if (captured == null) {
            // 26.2 GUI 预览：InventoryScreen.extractEntityInInventoryFollowsMouse 用自建
            // extractRenderState（不经 EntityRenderDispatcher.extractEntity），CAPTURED 无 entry。
            // 用 InventoryScreenMixin 记录的 GUI 预览实体补做 maid 替换（TLM MaidScreen 女仆预览）。
            Entity guiEntity = ModelPreviewRenderer.getAndClearGuiPreviewEntity();
            if (guiEntity != null && TouhouMaidCompat.isMaidEntity(guiEntity)) {
                float guiYaw = state instanceof LivingEntityRenderState lrs ? lrs.yRot : guiEntity.getYRot();
                MultiBufferSource.BufferSource guiBuffer = ModelPreviewRenderer.getLegacyBufferSourceOrNull();
                if (guiBuffer == null) {
                    return true;
                }
                boolean maidVanilla = MaidEntityRenderer.tryRender(guiEntity, guiYaw,
                        ModelPreviewRenderer.getGuiPreviewPartialTick(), poseStack, guiBuffer, 0xF000F0);
                if (!maidVanilla) {
                    guiBuffer.endBatch();
                    return false;
                }
            }
            return true;
        }
        Entity entity = captured.entity();
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        float partialTick = captured.partialTick();
        float entityYaw = entity.getYRot();
        int packedLight = captured.packedLight();
        MultiBufferSource.BufferSource bufferSource = ModelPreviewRenderer.getLegacyBufferSourceOrNull();
        if (bufferSource == null) {
            return true;
        }
        if (entity instanceof Projectile projectile) {
            if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                if (projectile instanceof FishingHook fishingHook) {
                    boolean shouldRenderVanilla = CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                    if (!shouldRenderVanilla) {
                        bufferSource.endBatch();
                    }
                    return shouldRenderVanilla;
                }
                boolean shouldRenderVanilla = CustomProjectileRenderer.renderProjectile(projectile, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                if (!shouldRenderVanilla) {
                    bufferSource.endBatch();
                }
                return shouldRenderVanilla;
            }
        }
        boolean maidVanilla = MaidEntityRenderer.tryRender(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (!maidVanilla) {
            bufferSource.endBatch();
            return false;
        }
        if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get().booleanValue()) {
            ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
            boolean shouldRenderVanilla = CustomVehicleRenderer.renderVehicle(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            if (!shouldRenderVanilla) {
                bufferSource.endBatch();
            }
            return shouldRenderVanilla;
        }
        return true;
    }
}
