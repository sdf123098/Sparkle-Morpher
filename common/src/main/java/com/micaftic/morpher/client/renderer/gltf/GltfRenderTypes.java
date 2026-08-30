package com.micaftic.morpher.client.renderer.gltf;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import com.micaftic.morpher.util.data.MemoizationCache;

import java.util.function.Function;

/** Render types owned by the independent glTF renderer. */
public final class GltfRenderTypes {
    private static final Function<Key, RenderType> CACHE = MemoizationCache.memoize(GltfRenderTypes::create);

    private GltfRenderTypes() {
    }

    /** The only primitive topology accepted by the glTF render adapter. */
    public static VertexFormat.Mode topology() {
        return VertexFormat.Mode.TRIANGLES;
    }

    public static RenderType get(Identifier texture, AlphaMode alphaMode, boolean doubleSided) {
        if (texture == null) {
            throw new IllegalArgumentException("glTF texture must not be null");
        }
        return CACHE.apply(new Key(texture, alphaMode == null ? AlphaMode.OPAQUE : alphaMode, doubleSided));
    }

    private static RenderType create(Key key) {
        boolean translucent = key.alphaMode() == AlphaMode.BLEND;
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation("sparkle_morpher:pipeline/gltf_" + key.alphaMode().name().toLowerCase())
                .withShaderDefine("ALPHA_CUTOUT", key.alphaMode() == AlphaMode.MASK ? 0.5f : 0.0f)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withSampler("Sampler1")
                .withCull(!key.doubleSided())
                .withVertexFormat(DefaultVertexFormat.ENTITY, topology())
                .withColorTargetState(translucent ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", key.texture())
                .useLightmap()
                .useOverlay()
                .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
                .createRenderSetup();
        return RenderType.create("sparkle_morpher_gltf_" + key.alphaMode().name().toLowerCase(), setup);
    }

    public enum AlphaMode {
        OPAQUE,
        MASK,
        BLEND
    }

    private record Key(Identifier texture, AlphaMode alphaMode, boolean doubleSided) {
    }
}
