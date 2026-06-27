package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.CustomFishingHookRenderer;
import com.micaftic.morpher.client.renderer.CustomVehicleRenderer;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.CustomProjectileRenderer;
import com.micaftic.morpher.config.GeneralConfig;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

@Mixin({EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {
    private static final Map<EntityRenderState, CapturedEntity> CAPTURED_ENTITIES = Collections.synchronizedMap(new IdentityHashMap<>());

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void ysm$captureEntity(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state != null) {
            int packedLight = ((EntityRenderDispatcher) (Object) this).getPackedLightCoords(entity, partialTick);
            CAPTURED_ENTITIES.put(state, new CapturedEntity(entity, partialTick, packedLight));
        }
    }

    @WrapWithCondition(method = {"submit"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")})
    private boolean ysm$renderCustom(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        CapturedEntity captured = CAPTURED_ENTITIES.remove(state);
        if (captured == null) {
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

    private record CapturedEntity(Entity entity, float partialTick, int packedLight) {
    }
}
