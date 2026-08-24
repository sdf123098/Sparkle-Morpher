package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.render.RenderBackend;

/**
 * R10.1 RenderBackend 实现：Native SIMD 路径（1.21.1 分支版，JNI 顶点/骨骼计算），
 * 委托 {@link ModelRendererBridge#nativeRenderModel}。返回 false 表示本后端未处理
 * （native 缓存不可用等），协调层回退到 Java。
 */
public final class NativeSimdRenderBackend implements RenderBackend {

    public static final NativeSimdRenderBackend INSTANCE = new NativeSimdRenderBackend();

    private NativeSimdRenderBackend() {
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        return ModelRendererBridge.nativeRenderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), model, request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview());
    }
}
