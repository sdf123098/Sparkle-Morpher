package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GltfLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void recommendsPixelScaleOnlyForNetEase4dExports() throws Exception {
        GltfModel netease = GltfLoader.load(
                "{\"asset\":{\"version\":\"2.0\",\"generator\":\"netease-4d-skin\"}}"
                        .getBytes(StandardCharsets.UTF_8), tempDir, "netease.gltf");
        GltfModel standard = GltfLoader.load(
                "{\"asset\":{\"version\":\"2.0\",\"generator\":\"Blender\"}}"
                        .getBytes(StandardCharsets.UTF_8), tempDir, "standard.gltf");

        assertEquals(1.0f / 16.0f, netease.recommendedMinecraftScale());
        assertEquals(1.0f, standard.recommendedMinecraftScale());
    }

    @Test
    void loadsDataUriGltfCoreMesh() throws Exception {
        byte[] bin = meshBuffer();
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":42,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                                  {"buffer":0,"byteOffset":36,"byteLength":6}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                               {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
                 "nodes":[{"name":"root","mesh":0}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(bin));

        GltfModel model = GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "triangle.gltf");

        assertEquals(1, model.meshes().size());
        assertEquals(3, model.meshes().get(0).primitives().get(0).vertexCount());
        assertEquals(1, model.meshes().get(0).primitives().get(0).triangleCount());
        assertArrayEquals(new int[]{0, 1, 2}, model.meshes().get(0).primitives().get(0).indices());
        assertEquals(-1, model.nodes().get(0).parentIndex());
    }

    @Test
    void loadsGlbBinChunkAndExternalImage() throws Exception {
        byte[] bin = meshBuffer();
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":42}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                                  {"buffer":0,"byteOffset":36,"byteLength":6}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                               {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}],
                 "images":[{"uri":"texture.bin","mimeType":"application/octet-stream"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}]}
                """;
        Files.write(tempDir.resolve("texture.bin"), new byte[]{4, 5, 6});
        Path glb = tempDir.resolve("triangle.glb");
        Files.write(glb, glb(json, bin));

        GltfModel model = GltfLoader.load(glb);

        assertEquals(1, model.images().size());
        assertArrayEquals(new byte[]{4, 5, 6}, model.images().get(0).data());
        assertEquals(1, model.meshes().get(0).primitives().get(0).triangleCount());
    }

    @Test
    void loadsSkinInverseBindMatricesAndStepAnimation() throws Exception {
        byte[] bin = skinAnimationBuffer();
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":%d,"uri":"%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":64},
                                  {"buffer":0,"byteOffset":64,"byteLength":8},
                                  {"buffer":0,"byteOffset":72,"byteLength":24}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":1,"type":"MAT4"},
                               {"bufferView":1,"componentType":5126,"count":2,"type":"SCALAR"},
                               {"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"}],
                 "nodes":[{"name":"root","children":[1]},{"name":"joint"}],
                 "skins":[{"joints":[1],"inverseBindMatrices":0}],
                 "animations":[{"name":"move","samplers":[{"input":1,"output":2,"interpolation":"STEP"}],
                                  "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(bin.length, uri);

        GltfModel model = GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "skin.gltf");

        assertEquals(1, model.skins().size());
        assertEquals(1, model.skins().get(0).joints().size());
        assertEquals(1f, model.skins().get(0).inverseBindMatrices()[0]);
        GltfModel.Animation animation = model.animations().get(0);
        assertEquals(1f, animation.duration());
        assertEquals(GltfModel.Channel.Interpolation.STEP, animation.channels().get(0).interpolation());
        assertEquals(2, animation.channels().get(0).times().length);
        GltfSceneEvaluator.Pose pose = new GltfSceneEvaluator(model).evaluate(0, 0, 0.5f, false);
        assertEquals(0.0f, pose.worldMatrix(1).m30(), 0.0001f);
        GltfSceneEvaluator.Pose finalPose = new GltfSceneEvaluator(model).evaluate(0, 0, 1.0f, false);
        assertEquals(1.0f, finalPose.worldMatrix(1).m30(), 0.0001f);
    }

    @Test
    void loadsVertexColorsAsFourComponents() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putFloat(0).putFloat(0).putFloat(1);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(buffer.array());
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":72,"uri":"%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                                  {"buffer":0,"byteOffset":36,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                               {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0,"COLOR_0":1}}]}],
                 "nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(uri);

        GltfModel model = GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "colors.gltf");

        assertArrayEquals(new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1},
                model.meshes().get(0).primitives().get(0).colors(), 0.0001f);
        GltfSceneEvaluator.Vertex[] vertices = new GltfSceneEvaluator(model)
                .transform(new GltfSceneEvaluator(model).evaluate(0, -1, 0, false), 0, 0);
        assertEquals(1, vertices[0].red());
        assertEquals(1, vertices[1].green());
        assertEquals(1, vertices[2].blue());
    }

    @Test
    void rejectsUnsupportedPrimitiveMode() {
        String json = """
                {"asset":{"version":"2.0"},"meshes":[{"primitives":[{"mode":5,"attributes":{"POSITION":0}}]}],
                 "accessors":[],"nodes":[]}
                """;
        assertThrows(GltfLoader.GltfParseException.class,
                () -> GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "strip.gltf"));
    }

    @Test
    void extensionsAreStrictByDefaultButCanBeRegistered() throws Exception {
        String json = """
                {"asset":{"version":"2.0"},"extensionsUsed":["TEST_future_extension"],
                 "extensions":{"TEST_future_extension":{"enabled":true}}}
                """;
        byte[] source = json.getBytes(StandardCharsets.UTF_8);
        assertThrows(GltfLoader.GltfParseException.class,
                () -> GltfLoader.load(source, tempDir, "extension.gltf"));

        GltfExtensionRegistry registry = GltfExtensionRegistry.builder()
                .register("TEST_future_extension", root -> { })
                .build();
        assertEquals(0, GltfLoader.load(source, tempDir, "extension.gltf", registry).meshes().size());
    }

    private static byte[] meshBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        return buffer.array();
    }

    private static byte[] skinAnimationBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 16; i++) buffer.putFloat(i % 5 == 0 ? 1 : 0);
        buffer.putFloat(0).putFloat(1);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        return buffer.array();
    }

    private static byte[] glb(String json, byte[] bin) throws IOException {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonPadded = align4(jsonBytes.length);
        int binPadded = align4(bin.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + 8 + jsonPadded + 8 + binPadded);
        putInt(out, 0x46546C67);
        putInt(out, 2);
        putInt(out, 12 + 8 + jsonPadded + 8 + binPadded);
        putInt(out, jsonPadded);
        putInt(out, 0x4E4F534A);
        out.write(jsonBytes);
        while (out.size() < 12 + 8 + jsonPadded) out.write(' ');
        putInt(out, binPadded);
        putInt(out, 0x004E4942);
        out.write(bin);
        while (out.size() < 12 + 8 + jsonPadded + 8 + binPadded) out.write(0);
        return out.toByteArray();
    }

    private static void putInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static int align4(int value) { return (value + 3) & ~3; }
}
