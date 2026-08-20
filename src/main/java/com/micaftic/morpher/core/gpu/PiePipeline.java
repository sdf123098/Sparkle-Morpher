package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * R1.2.2 轮盘阶段 1：可移植（backend-neutral）pie pipeline。
 *
 * <p>几何在 CPU 侧预三角化（{@link PieMesh}），本 pipeline 只消费
 * Position(RG32_FLOAT) 顶点 + 两个 UNIFORM_BUFFER block：
 * <ul>
 *   <li>PieProjBlock —— mat4 GUI 正交投影（gui 尺寸变化时更新）；</li>
 *   <li>PieColorBlock —— vec4 片颜色（hover/selected/alpha 每帧更新）。</li>
 * </ul>
 *
 * <p>仿照 {@link Blaze3DBoneSkinPipeline} 的模式：经 RenderPipeline.builder 声明
 * shader/bind group/vertex binding/topology/blend，OpenGL 与 Vulkan 由
 * 26.2 Blaze3D 统一编译执行，不再区分 Raw GL 快路径与 fill 回退。
 */
final class PiePipeline {
    static final Identifier SHADER = Identifier.fromNamespaceAndPath("sparkle_morpher", "core/pie_portable");

    static final RenderPipeline PIPELINE = RenderPipeline
            .builder()
            .withLocation(Identifier.fromNamespaceAndPath("sparkle_morpher", "pipeline/pie_portable"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("PieProjBlock", UniformType.UNIFORM_BUFFER)
                    .withUniform("PieColorBlock", UniformType.UNIFORM_BUFFER)
                    .build())
            .withVertexBinding(0, VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RG32_FLOAT)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            // GUI 式 alpha 合成：SRC_ALPHA / ONE_MINUS_SRC_ALPHA（与旧 GUI 路径 770/771 等价）
            .withColorTargetState(new ColorTargetState(
                    Optional.of(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT),
                    GpuFormat.RGBA8_UNORM,
                    ColorTargetState.WRITE_ALL))
            // GUI 层不需要深度测试/写入
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private PiePipeline() {
    }
}