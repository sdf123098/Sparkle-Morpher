package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.entity.EntityRenderCache;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.WorldRenderState;
import com.micaftic.morpher.core.gpu.Blaze3DModelFramePass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class WorldRendererMixin {

    @Inject(method = {"render"}, at = @At("HEAD"))
    private void renderLevelPre(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        // 无条件清理上一帧的延迟绘制（即使本模组不可用，也不该留下陈旧引用）。
        Blaze3DModelFramePass.beginFrame();
        if (YesSteveModel.isAvailable()) {
            WorldRenderState.begin(projectionMatrix);
            ModelPreviewRenderer.setWorldRenderMode(true);
            EntityRenderCache.tick(deltaTracker.getGameTimeDeltaPartialTick(false));
        }
    }

    /**
     * 在 MC 帧图构建到 always-on-top pass 之后、{@code FrameGraphBuilder.execute} 之前，把本模组的
     * GPU 蒙皮 pass 挂进同一张帧图（见 {@link Blaze3DModelFramePass}）。
     *
     * <p>选 {@code addAlwaysOnTopPass} 的 TAIL：它的第一个参数就是 {@code FrameGraphBuilder}，
     * 无需脆弱的 {@code @Local} 捕获；且此时 {@code targets.main} 已由 main / alwaysOnTop pass
     * 更新为最新 handle，故我们的 {@code readsAndWrites(targets.main)} 会让 framegraph 自然把
     * 该 pass 排在其后。</p>
     */
    @Inject(method = {"addAlwaysOnTopPass"}, at = @At("TAIL"))
    private void sparkleMorpher$appendBlaze3DPass(FrameGraphBuilder builder, FeatureRenderDispatcher.PreparedFrame preparedFrame, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        Blaze3DModelFramePass.appendPass(builder, ((LevelRendererAccessor) this).sparkleMorpher$getTargets());
    }

    @Inject(method = {"render"}, at = @At("RETURN"))
    private void renderLevelPost(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState, Matrix4fc projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        WorldRenderState.end();
        // 兜底：若本帧未走到 framegraph pass（异常路径），清空残留。
        Blaze3DModelFramePass.endFrame();
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setWorldRenderMode(false);
        }
    }
}
