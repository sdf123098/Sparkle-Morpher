package com.micaftic.morpher.core.render;

/**
 * R1.2.2 §8 Backend Capability Model（1.2.2 MUST-3）。
 *
 * <p>业务渲染代码按「能力」选择路径，而不是按后端名字硬编码策略（RULE-GFX-5）。
 * 本 record 定义在 common 的 render API 层，六分支同构；具体能力值由各分支的
 * GPU/Render Adapter 提供（{@link #current()}）。
 *
 * <p>1.21.1 变体：<b>常量集</b>（§37-Q2 决策，RULE-BRANCH-5 记录）——1.21.1 无
 * Blaze3D CommandEncoder/RenderPipeline 抽象，按实际可用 API 静态给出；
 * 业务代码无需感知分支差异。Raw GL 是 1.21.1 的主渲染路径（非 legacy）。
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

    /** 1.21.1 常量集（按实际 API 静态给出）。 */
    public static SmGraphicsCapabilities current() {
        return new SmGraphicsCapabilities(
                false,  // supportsPortablePipeline —— 无 CommandEncoder/RenderPipeline
                true,   // supportsGpuMesh —— GpuRenderPath 静态网格
                true,   // supportsGpuSkinning —— BoneSkinShader + SSBO 蒙皮
                true,   // supportsCustomShader —— PieShader/BlurShader 等 Raw GL shader
                true,   // supportsRawOpenGl —— 1.21.1 GL 主路径
                false,  // supportsGpuTimestamp —— 未接入
                false,  // supportsAsyncUpload —— 未接入
                true    // supportsStorageBuffer —— SSBO 蒙皮
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

