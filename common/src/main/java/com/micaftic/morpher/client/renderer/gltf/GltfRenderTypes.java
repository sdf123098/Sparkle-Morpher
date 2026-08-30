package com.micaftic.morpher.client.renderer.gltf;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Render types owned by the independent glTF renderer. */
public final class GltfRenderTypes {
    private static final Function<Key, RenderType> CACHE = Util.memoize(GltfRenderTypes::create);

    private GltfRenderTypes() {
    }

    /** The only primitive topology accepted by the glTF render adapter. */
    public static VertexFormat.Mode topology() {
        return VertexFormat.Mode.TRIANGLES;
    }

    public static RenderType get(ResourceLocation texture, AlphaMode alphaMode, boolean doubleSided) {
        if (texture == null) {
            throw new IllegalArgumentException("glTF texture must not be null");
        }
        return CACHE.apply(new Key(texture, alphaMode == null ? AlphaMode.OPAQUE : alphaMode, doubleSided));
    }

    private static RenderType create(Key key) {
        boolean translucent = key.alphaMode() == AlphaMode.BLEND;
        RenderStateShard.ShaderStateShard shader = translucent
                ? RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                : RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER;
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTextureState(new RenderStateShard.TextureStateShard(key.texture(), false, false))
                .setTransparencyState(translucent
                        ? RenderStateShard.TRANSLUCENT_TRANSPARENCY
                        : RenderStateShard.NO_TRANSPARENCY)
                .setCullState(key.doubleSided() ? RenderStateShard.NO_CULL : RenderStateShard.CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(false);
        return RenderType.create("sparkle_morpher_gltf_" + key.alphaMode().name().toLowerCase(),
                DefaultVertexFormat.NEW_ENTITY, topology(), 256, true, translucent, state);
    }

    public enum AlphaMode {
        OPAQUE,
        MASK,
        BLEND
    }

    private record Key(ResourceLocation texture, AlphaMode alphaMode, boolean doubleSided) {
    }
}
