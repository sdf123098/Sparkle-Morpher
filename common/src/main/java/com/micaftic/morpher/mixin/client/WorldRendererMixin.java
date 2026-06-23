package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.entity.EntityRenderCache;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class WorldRendererMixin {
    @Inject(method = {"renderLevel"}, at = @At("HEAD"))
    private void renderLevelPre(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        AnimationFrameProfiler.beginRenderFrame(partialTick);
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setFirstPersonMode(true);
            EntityRenderCache.tick(partialTick);
        }
    }

    @Inject(method = {"renderLevel"}, at = @At("RETURN"))
    private void renderLevelPost(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            EntityRenderCache.clear();
            ModelPreviewRenderer.setFirstPersonMode(false);
        }
    }
}
