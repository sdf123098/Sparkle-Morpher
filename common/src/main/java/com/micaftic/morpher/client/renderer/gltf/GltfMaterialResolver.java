package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.resource.gltf.GltfModel;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

/** Resolves glTF material state for every Minecraft-side glTF render path. */
public final class GltfMaterialResolver {
    private GltfMaterialResolver() {
    }

    public static <T> ResolvedMaterial<T> resolve(@Nullable GltfModel.Material material,
                                                   T fallbackTexture,
                                                   @Nullable T overrideTexture,
                                                   IntFunction<T> textureResolver) {
        Objects.requireNonNull(fallbackTexture, "fallbackTexture");
        Objects.requireNonNull(textureResolver, "textureResolver");

        T texture = overrideTexture;
        if (texture == null && material != null && material.baseColorTextureIndex() >= 0) {
            texture = textureResolver.apply(material.baseColorTextureIndex());
        }
        if (texture == null) {
            texture = fallbackTexture;
        }

        String alphaModeName = material == null || material.alphaMode() == null
                ? "OPAQUE" : material.alphaMode().toUpperCase(Locale.ROOT);
        GltfRenderTypes.AlphaMode alphaMode;
        try {
            alphaMode = GltfRenderTypes.AlphaMode.valueOf(alphaModeName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported glTF alpha mode: " + alphaModeName, exception);
        }
        float alphaCutoff = material == null ? 0.5f : material.alphaCutoff();
        if (!Float.isFinite(alphaCutoff)) {
            alphaCutoff = 0.5f;
        }
        alphaCutoff = Math.max(0.0f, Math.min(1.0f, alphaCutoff));
        return new ResolvedMaterial<>(texture, alphaMode, alphaCutoff,
                material != null && material.doubleSided());
    }

    public record ResolvedMaterial<T>(T texture,
                                      GltfRenderTypes.AlphaMode alphaMode,
                                      float alphaCutoff,
                                      boolean doubleSided) {
        public ResolvedMaterial {
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(alphaMode, "alphaMode");
        }
    }
}
