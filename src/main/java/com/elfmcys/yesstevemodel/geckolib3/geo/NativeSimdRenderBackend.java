package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.gpu.GpuDebugLog;
import com.micaftic.morpher.core.render.NativeSimdValidator;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;

/**
 * R10.1 RenderBackend 实现：Native SIMD 路径（JNI 顶点/骨骼计算），
 * 委托 {@link ModelRendererBridge#nativeRenderModel}。返回 false 表示本后端未处理
 * （native 缓存不可用等），协调层回退到 Java。
 */
public final class NativeSimdRenderBackend implements RenderBackend {

    public static final NativeSimdRenderBackend INSTANCE = new NativeSimdRenderBackend();

    private NativeSimdRenderBackend() {
    }

    @Override
    public RenderBackendDecision.Backend backend() {
        return RenderBackendDecision.Backend.NATIVE_SIMD;
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        GpuDebugLog.verbose("entry rendered through native SIMD texture={} partMask={}", request.textureLocation(), request.renderPartMask());
        // Phase 2: run validation diagnostics (may force Java for the session
        // under STRICT_FALLBACK, or throw under CRASH_TEST). Does not change the
        // rendered path under LOG_MISMATCH.
        NativeSimdValidator.onNativeSimdRender(model, request.boneParams(), request.renderPartMask(), request.textureLocation());
        return ModelRendererBridge.nativeRenderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), model, request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview());
    }
}
