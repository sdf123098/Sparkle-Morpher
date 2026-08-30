package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.gltf.GltfModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GltfMaterialResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesTextureAlphaCutoffAndDoubleSidedFromMaterial() throws Exception {
        GltfModel.Material material = loadMaterial("MASK", 0.37f, true, true);
        String fallback = "fallback";

        GltfMaterialResolver.ResolvedMaterial<String> resolved = GltfMaterialResolver.resolve(
                material, fallback, null, index -> id("texture-" + index));

        assertEquals(id("texture-0"), resolved.texture());
        assertEquals(GltfRenderTypes.AlphaMode.MASK, resolved.alphaMode());
        assertEquals(0.37f, resolved.alphaCutoff());
        assertEquals(true, resolved.doubleSided());
    }

    @Test
    void explicitOverrideAndFallbackAreOnlyUsedWhenRequested() throws Exception {
        GltfModel.Material textured = loadMaterial("BLEND", 0.5f, false, true);
        String override = "override";

        GltfMaterialResolver.ResolvedMaterial<String> overridden = GltfMaterialResolver.resolve(
                textured, id("fallback"), override, index -> id("texture-" + index));
        GltfMaterialResolver.ResolvedMaterial<String> fallback = GltfMaterialResolver.resolve(
                null, id("fallback"), null, index -> id("texture-" + index));

        assertEquals(override, overridden.texture());
        assertEquals(GltfRenderTypes.AlphaMode.BLEND, overridden.alphaMode());
        assertEquals(id("fallback"), fallback.texture());
        assertEquals(GltfRenderTypes.AlphaMode.OPAQUE, fallback.alphaMode());
    }

    private GltfModel.Material loadMaterial(String alphaMode, float cutoff, boolean doubleSided,
                                             boolean withTexture) throws Exception {
        String resources = withTexture
                ? ",\"images\":[{\"uri\":\"data:application/octet-stream;base64,AA==\"}],"
                        + "\"textures\":[{\"source\":0}]"
                : "";
        String textureInfo = withTexture ? ",\"baseColorTexture\":{\"index\":0}" : "";
        String json = "{\"asset\":{\"version\":\"2.0\"},\"materials\":[{"
                + "\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,1,1,1]"
                + textureInfo + "},\"alphaMode\":\"" + alphaMode + "\","
                + "\"alphaCutoff\":" + cutoff + ",\"doubleSided\":" + doubleSided
                + "}]" + resources + "}";
        return GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "material.gltf")
                .materials().get(0);
    }

    private static String id(String path) {
        return path;
    }
}
