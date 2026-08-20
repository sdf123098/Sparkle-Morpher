package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

public final class Blaze3DBoneSkinPipeline {
    static final Identifier SHADER = Identifier.fromNamespaceAndPath("sparkle_morpher", "core/blaze3d_bone_skin");
    /**
     * 26.1.2 骨骼矩阵 UBO 上限：TextureFormat 无 RGBA32F（26.2 用 texel buffer），
     * 26.1.2 走 std140 UBO + shader 内固定大小数组。每骨骼 80B（mat4 transform + vec4 meta，
     * 法线矩阵不放 UBO，shader 内 inverse(transpose(mat3)) 求）——681 骨骼模型实测 54.5KB，
     * 低于 OpenGL 64KB maxUniformBlockSize；768 × 80B = 61.4KB 为安全上限。
     * 必须与 blaze3d_bone_skin.vsh 的 BONE_CAP 一致；骨骼数超过上限时回退经典 HUD。
     */
    public static final int BONE_CAP = 768;

    /**
     * 26.1.2 无 GpuFormat，顶点元素必须经 VertexFormatElement.register 注册进 BY_ID 表。
     * vanilla 静态只占 id 0-6，但 Iris 等渲染 mod 会在运行时注册额外元素（实测 id 8 已被
     * Iris 占用，硬编码 id 直接抛 Duplicate element registration 包成 EIIE → HUD 不显示）。
     * 这里反射读 BY_ID 找第一个空槽，对任意 mod 组合都稳；反射失败回退从高位往下试注册。
     */
    private static final VertexFormatElement BONE_ID = registerSparse(VertexFormatElement.Type.UINT, 1, "BoneId");
    private static final VertexFormatElement CULLABLE = registerSparse(VertexFormatElement.Type.UINT, 1, "Cullable");

    /** 现代 HUD（client.renderer.modernhud 包）复用。 */
    public static final RenderPipeline PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("sparkle_morpher", "pipeline/blaze3d_bone_skin"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withUniform("BoneMatrices", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("UV0", VertexFormatElement.UV0)
                    .add("Normal", VertexFormatElement.NORMAL)
                    .padding(1)
                    .add("BoneId", BONE_ID)
                    .add("Cullable", CULLABLE)
                    .build(), VertexFormat.Mode.TRIANGLES)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    private static VertexFormatElement registerSparse(VertexFormatElement.Type type, int count, String what) {
        try {
            Field byId = VertexFormatElement.class.getDeclaredField("BY_ID");
            byId.setAccessible(true);
            Object[] elements = (Object[]) byId.get(null);
            for (int i = 0; i < elements.length; i++) {
                if (elements[i] == null) {
                    return VertexFormatElement.register(i, 0, type, false, count);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 反射不可用时回退：从高位往下试注册
        }
        for (int i = 31; i >= 7; i--) {
            try {
                return VertexFormatElement.register(i, 0, type, false, count);
            } catch (IllegalArgumentException ignored) {
                // 槽被占，试下一个
            }
        }
        throw new IllegalStateException("No free VertexFormatElement id for " + what);
    }

    private Blaze3DBoneSkinPipeline() {
    }
}

