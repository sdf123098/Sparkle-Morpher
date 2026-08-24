package com.micaftic.morpher.core.render;

/**
 * R1.2.2 §8 Backend Capability Model（1.2.2 MUST-3）。
 *
 * <p>业务渲染代码按「能力」选择路径，而不是按后端名字（OpenGL/Vulkan）硬编码策略
 * （RULE-GFX-5）。本 record 定义在 common 的 render API 层，六分支同构；
 * 具体能力值由各分支的 GPU/Render Adapter 提供（{@link #current()}）：
 * <ul>
 *   <li>26.2 —— 基于 {@link Blaze3D26_2Capability} 探测 + {@link SmGraphicsBackendDetector}；</li>
 *   <li>26.1.2 —— 基于 {@code Blaze3D26_1_2Capability}（前一代 Blaze3D API，无 GpuFormat）；</li>
 *   <li>1.21.1 —— 常量集（无 CommandEncoder/RenderPipeline，按实际可用 API 静态给出）。</li>
 * </ul>
 *
 * <p>调用方范式：{@code if (SmGraphicsCapabilities.current().supportsPortablePipeline()) { ... }}
 * 而不是 {@code if (backend == VULKAN) { ... }}。
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

    /**
     * 当前分支的实际能力集。
     *
     * <p>探测分两层：Blaze3D API 存在性是编译期固定的（结果可永久缓存），
     * raw OpenGL 许可随运行时后端判定（{@link SmGraphicsBackendDetector} 内部缓存，
     * device 未创建时自动重探测）。
     */
    public static SmGraphicsCapabilities current() {
        Blaze3D26_2Capability.Report r = Blaze3D26_2Capability.report();
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
                // GPU timestamp / 异步上传：尚未接入（无探测、无使用点），如实报告 false
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