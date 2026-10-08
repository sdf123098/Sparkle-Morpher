package com.micaftic.morpher.core.gpu;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Blaze3DBoneSkinPipeline {
    static final Identifier SHADER = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId("sparkle_morpher", "core/blaze3d_bone_skin");
    static final Identifier TRANSLUCENT_SHADER = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(
            "sparkle_morpher", "core/blaze3d_bone_skin_translucent");
    /** 现代 HUD（client.renderer.modernhud 包）复用。 */
    public static final RenderPipeline PIPELINE = buildPipeline(
            com.micaftic.morpher.core.api.resource.ResourceApi.nativeId("sparkle_morpher", "pipeline/blaze3d_bone_skin"),
                    SHADER, ColorTargetState.DEFAULT);

    /** Same skinning path with the entity-translucent blend state used by the world renderer. */
    public static final RenderPipeline TRANSLUCENT_PIPELINE = buildPipeline(
            com.micaftic.morpher.core.api.resource.ResourceApi.nativeId("sparkle_morpher", "pipeline/blaze3d_bone_skin_translucent"),
            TRANSLUCENT_SHADER, new ColorTargetState(BlendFunction.TRANSLUCENT));

    private static final AtomicBoolean pipelinesPrecompiled = new AtomicBoolean(false);
    private static final AtomicBoolean precompileWarned = new AtomicBoolean(false);

    /**
     * 预热两条骨骼皮肤管线，避免首次使用时在渲染热路径上编译。
     *
     * <p>必须在渲染线程调用（{@link GpuDevice} 已就绪）。失败不影响后续渲染——
     * {@code setPipeline} 仍会惰性编译；失败时保留重试（成功前每帧重试，日志只打一次）。</p>
     */
    public static void precompile(GpuDevice device) {
        if (device == null || pipelinesPrecompiled.get()) {
            return;
        }
        try {
            RenderSystem.getCompiledPipeline(PIPELINE);
            RenderSystem.getCompiledPipeline(TRANSLUCENT_PIPELINE);
            pipelinesPrecompiled.set(true);
        } catch (Throwable t) {
            if (precompileWarned.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D pipeline precompile failed (will retry lazily): {}", t.toString());
            }
        }
    }

    private static RenderPipeline buildPipeline(Identifier location, Identifier fragmentShader,
                                                ColorTargetState colorTargetState) {
        return RenderPipeline
                .builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(location)
                .withVertexShader(SHADER)
                .withFragmentShader(fragmentShader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("BoneMatrices", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
                        .build())
                .withVertexBinding(0, VertexFormat.builder(0)
                        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
                        .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
                        .addAttribute("BoneId", GpuFormat.R32_UINT)
                        .addAttribute("Cullable", GpuFormat.R32_UINT)
                        .build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .withColorTargetState(colorTargetState)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();
    }

    private Blaze3DBoneSkinPipeline() {
    }
}
