package com.micaftic.morpher.core.render;

/**
 * R1.2.2 §8 Backend Capability Model（1.2.2 MUST-3）。
 *
 * <p>业务渲染代码按「能力」选择路径，而不是按后端名字硬编码策略（RULE-GFX-5）。
 * 本 record 定义在 common 的 render API 层，六分支同构；具体能力值由各分支的
 * GPU/Render Adapter 提供（{@link #current()}）。
 *
 * <p>26.1.2 变体：基于 {@link Blaze3D26_1_2Capability}（前一代 Blaze3D API，无
 * GpuFormat/RGBA32F）探测。26.1.2 无 Vulkan（仅 GL 后端），轮盘阶段 1 的
 * CommandEncoder 可移植路径未移植到本分支（低收益，见 1.2.2 同步台账）；
 * supportsPortablePipeline 报告的是 Blaze3D pipeline 结构能力。
 */
public record SmGraphicsCapabilities(
        boolean supportsPortablePipeline,
        boolean supportsGpuMesh,
        boolean supportsGpuSkinning,
        boolean supportsCustomShader,
        boolean supportsRawOpenGl,
        boolean supportsGpuTimestamp,
        boolean supportsAsyncUpload,
        boolean supportsStorageBuffer
) {
    /** 全部能力缺失的哨兵值：探测尚未就绪 / 平台完全不支持时使用。 */
    public static final SmGraphicsCapabilities NONE =
            new SmGraphicsCapabilities(false, false, false, false, false, false, false, false);

    public static SmGraphicsCapabilities current() {
        Blaze3D26_1_2Capability.Report r = Blaze3D26_1_2Capability.report();
        boolean stable = r.stableGraphicsApiPresent();
        boolean mesh = stable && r.createBufferPresent();
        boolean portable = mesh
                && r.precompilePipelinePresent()
                && r.createRenderPassPresent()
                && r.drawIndexedPresent();
        boolean customShader = stable
                && r.vertexShaderBuilderPresent()
                && r.fragmentShaderBuilderPresent();
        return new SmGraphicsCapabilities(
                portable,
                mesh,
                portable && customShader,
                customShader,
                SmGraphicsBackendDetector.isRawOpenGlAllowed(),
                false,
                false,
                mesh
        );
    }

    /** 单行诊断摘要（调试/日志用）。 */
    public String summary() {
        return "portablePipeline=" + supportsPortablePipeline
                + ", gpuMesh=" + supportsGpuMesh
                + ", gpuSkinning=" + supportsGpuSkinning
                + ", customShader=" + supportsCustomShader
                + ", rawOpenGl=" + supportsRawOpenGl
                + ", gpuTimestamp=" + supportsGpuTimestamp
                + ", asyncUpload=" + supportsAsyncUpload
                + ", storageBuffer=" + supportsStorageBuffer;
    }
}
