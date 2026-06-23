package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.entity.EntityRenderCache;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class WorldRendererMixin {
    @Inject(method = {"renderLevel"}, at = @At("HEAD"))
    private void renderLevelPre(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setWorldRenderMode(true);
            EntityRenderCache.tick(deltaTracker.getGameTimeDeltaPartialTick(false));
        }
    }

    @Inject(method = {"renderLevel"}, at = @At("RETURN"))
    private void renderLevelPost(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            EntityRenderCache.clear();
            ModelPreviewRenderer.setWorldRenderMode(false);
        }
    }
}
