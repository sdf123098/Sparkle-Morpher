package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class Blaze3DBoneSkinPipeline {
    static final Identifier SHADER = Identifier.fromNamespaceAndPath("sparkle_morpher", "core/blaze3d_bone_skin");
    static final RenderPipeline PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("sparkle_morpher", "pipeline/blaze3d_bone_skin"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("BoneMatrices", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
                    .build())
            .withVertexBinding(0, VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("UV0", GpuFormat.RG32_FLOAT)
                    .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
                    .addAttribute("BoneId", GpuFormat.R16_UINT)
                    .addAttribute("Cullable", GpuFormat.R8_UINT)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    private Blaze3DBoneSkinPipeline() {
    }
}
