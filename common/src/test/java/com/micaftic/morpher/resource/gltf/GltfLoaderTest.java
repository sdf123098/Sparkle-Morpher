package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void loadWithManifestReportsSortedExternalDependenciesAndTheirRoles() throws Exception {
        byte[] dependency = meshBuffer();
        Files.write(tempDir.resolve("shared.bin"), dependency);
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":42,"uri":"shared.bin"}],
                 "images":[{"uri":"shared.bin","mimeType":"application/octet-stream"}]}
                """;

        Path source = tempDir.resolve("shared.gltf");
        Files.writeString(source, json, StandardCharsets.UTF_8);
        GltfLoadResult parsed = GltfLoader.loadWithManifest(source);
        GltfModel compatibilityResult = GltfLoader.load(
                json.getBytes(StandardCharsets.UTF_8), tempDir, "shared.gltf");

        assertEquals(0, parsed.model().meshes().size());
        assertEquals(compatibilityResult.images().size(), parsed.model().images().size());
        assertEquals(1, parsed.dependencies().entries().size());
        ResourceDependencyManifest.Entry entry = parsed.dependencies().entries().get(0);
        assertEquals("shared.bin", entry.relativePath());
        assertEquals(Set.of(ResourceDependencyManifest.Role.BUFFER, ResourceDependencyManifest.Role.IMAGE), entry.roles());
        assertEquals(dependency.length, entry.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dependency)), entry.sha256());
        assertEquals(Files.getLastModifiedTime(tempDir.resolve("shared.bin")).toMillis(), entry.lastModifiedMillis());
        assertEquals(GltfLoader.sourceFingerprint(source), parsed.sourceFingerprint());

        byte[] changedDependency = dependency.clone();
        changedDependency[0] ^= 1;
        Files.write(tempDir.resolve("shared.bin"), changedDependency);
        assertNotEquals(parsed.sourceFingerprint(), GltfLoader.loadWithManifest(source).sourceFingerprint());
    }

    @Test
    void bytesOnlyImportManifestDoesNotInventExternalDependenciesForDataUri() throws Exception {
        String json = """
                {"asset":{"version":"2.0"},"images":[{"uri":"data:image/png;base64,AQID"}]}
                """;

        GltfLoadResult parsed = GltfLoader.loadWithManifest(
                json.getBytes(StandardCharsets.UTF_8), null, "picked.gltf");

        assertEquals(1, parsed.model().images().size());
        assertEquals(0, parsed.dependencies().entries().size());
    }

    @Test
    void rejectsExternalResourcesWithEscapingOrNonFileUris() {
        List<String> unsafeUris = List.of(
                "../outside.png",
                "%2e%2e/outside.png",
                "%2e%2e%2foutside.png",
                "/outside.png",
                "C:/outside.png",
                "//server/share/texture.png",
                "file:///outside.png",
                "https://example.invalid/texture.png",
                "texture.png?variant=1",
                "texture.png#fragment",
                "texture%zz.png");

        for (String uri : unsafeUris) {
            String json = "{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"" + uri + "\"}]}";
            assertThrows(GltfLoader.GltfParseException.class,
                    () -> GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "unsafe-uri.gltf"),
                    () -> "URI must be rejected before file access: " + uri);
        }
    }

    @Test
    void rejectsExternalSymlinkThatEscapesTheAuthorizedModelRoot() throws Exception {
        Path outsideDirectory = tempDir.resolveSibling(tempDir.getFileName() + "-outside");
        Files.createDirectories(outsideDirectory);
        Path outsideImage = Files.write(outsideDirectory.resolve("outside.png"), new byte[]{1, 2, 3});
        Path link = tempDir.resolve("linked.png");
        try {
            try {
                Files.createSymbolicLink(link, outsideImage);
            } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
                Assumptions.assumeTrue(false, "symbolic links are unavailable in this test environment: " + unavailable);
            }

            String json = "{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"linked.png\"}]}";
            assertThrows(GltfLoader.GltfParseException.class,
                    () -> GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "symlink.gltf"));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outsideImage);
            Files.deleteIfExists(outsideDirectory);
        }
    }

    @Test
    void bytesImportCannotResolveSiblingFilesWithoutAnAuthorizedRoot() throws Exception {
        Files.write(tempDir.resolve("texture.bin"), new byte[]{4, 5, 6});
        String json = """
                {"asset":{"version":"2.0"},"images":[{"uri":"texture.bin"}]}
                """;
        assertThrows(GltfLoader.GltfParseException.class,
                () -> GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), null, "picked.gltf"));
    }

    @Test
    void rejectsOversizedDataUriBeforeDecodingItsPayload() {
        String json = "{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"data:application/octet-stream;base64,AQID\"}]}";
        GltfLoader.GltfParseException failure = assertThrows(GltfLoader.GltfParseException.class,
                () -> loadWithLimits(json, 2, 4));
        assertTrue(failure.getMessage().contains("remaining resource size limit"));
    }

    @Test
    void rejectsExternalAggregateBudgetBeforeReadingTheNextResource() throws Exception {
        Files.write(tempDir.resolve("first.bin"), new byte[]{1, 2});
        Files.write(tempDir.resolve("second.bin"), new byte[]{3, 4});
        String json = """
                {"asset":{"version":"2.0"},"buffers":[
                  {"byteLength":2,"uri":"first.bin"},{"byteLength":2,"uri":"second.bin"}]}
                """;

        GltfLoader.GltfParseException failure = assertThrows(GltfLoader.GltfParseException.class,
                () -> loadWithLimits(json, 3, 8));
        assertTrue(failure.getMessage().contains("aggregate size limit"));
    }

    @Test
    void enforcesResourceCountAcrossDataUris() {
        String json = """
                {"asset":{"version":"2.0"},"images":[
                  {"uri":"data:application/octet-stream;base64,AQ=="},
                  {"uri":"data:application/octet-stream;base64,Ag=="}]}
                """;
        GltfLoader.GltfParseException failure = assertThrows(GltfLoader.GltfParseException.class,
                () -> loadWithLimits(json, 8, 1));
        assertTrue(failure.getMessage().contains("too many external/data resources"));
    }

    private GltfLoadResult loadWithLimits(String json, long maxBytes, int maxResources) throws IOException {
        return GltfLoader.loadWithManifest(json.getBytes(StandardCharsets.UTF_8), tempDir, "limits.gltf",
                GltfExtensionRegistry.coreOnly(), new GltfLoader.ResourceLimits(1_000_000, maxBytes, maxResources));
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

    @Test
    void loadsAndEvaluatesCubicSplineAndMorphTargetViaSpmCore() throws Exception {
        // 3 vertices triangle + morph target shifting y by +2 + CUBICSPLINE translation + LINEAR weight animation
        ByteBuffer buf = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        // 0..35: positions (3 * VEC3)
        buf.putFloat(0).putFloat(0).putFloat(0);
        buf.putFloat(1).putFloat(0).putFloat(0);
        buf.putFloat(0).putFloat(1).putFloat(0);
        // 36..71: morph target 0 POSITION delta (3 * VEC3) -> +2 on Y for vertex 0
        buf.putFloat(0).putFloat(2).putFloat(0);
        buf.putFloat(0).putFloat(0).putFloat(0);
        buf.putFloat(0).putFloat(0).putFloat(0);
        // 72..79: time inputs [0, 1]
        buf.putFloat(0f).putFloat(1f);
        // 80..151: CUBICSPLINE translation (2 keys * 3 vec3 = 18 floats = 72 bytes):
        for (int i = 0; i < 9; i++) buf.putFloat(0f);
        buf.putFloat(0f).putFloat(0f).putFloat(0f);
        buf.putFloat(4f).putFloat(0f).putFloat(0f);
        buf.putFloat(0f).putFloat(0f).putFloat(0f);
        // 152..159: LINEAR weights [0.0, 1.0] (8 bytes)
        buf.putFloat(0f).putFloat(1f);
        byte[] bin = new byte[160];
        buf.flip();
        buf.get(bin);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        String json = """
                {
                  "asset": {"version": "2.0"},
                  "buffers": [{"byteLength": 160, "uri": "%s"}],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 8},
                    {"buffer": 0, "byteOffset": 80, "byteLength": 72},
                    {"buffer": 0, "byteOffset": 152, "byteLength": 8}
                  ],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [0,2,0]},
                    {"bufferView": 2, "componentType": 5126, "count": 2, "type": "SCALAR", "min": [0], "max": [1]},
                    {"bufferView": 3, "componentType": 5126, "count": 6, "type": "VEC3"},
                    {"bufferView": 4, "componentType": 5126, "count": 2, "type": "SCALAR"}
                  ],
                  "meshes": [{
                    "name": "tri",
                    "weights": [0.0],
                    "primitives": [{"attributes": {"POSITION": 0}, "targets": [{"POSITION": 1}]}]
                  }],
                  "nodes": [{"name": "root", "mesh": 0}],
                  "scenes": [{"nodes": [0]}],
                  "scene": 0,
                  "animations": [{
                    "name": "morph_slide",
                    "samplers": [
                      {"input": 2, "interpolation": "CUBICSPLINE", "output": 3},
                      {"input": 2, "interpolation": "LINEAR", "output": 4}
                    ],
                    "channels": [
                      {"sampler": 0, "target": {"node": 0, "path": "translation"}},
                      {"sampler": 1, "target": {"node": 0, "path": "weights"}}
                    ]
                  }]
                }
                """.formatted(uri);

        GltfModel model = GltfLoader.load(json.getBytes(StandardCharsets.UTF_8), tempDir, "cubic-morph.gltf");
        try {
            assertNotNull(model.coreBridge());
            assertEquals(GltfModel.Channel.Interpolation.CUBICSPLINE, model.animations().get(0).channels().get(0).interpolation());
            assertEquals(GltfModel.Channel.Path.WEIGHTS, model.animations().get(0).channels().get(1).path());

            GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
            GltfSceneEvaluator.Pose pose = evaluator.evaluate(0, 0, 0.5f, false);
            assertEquals(2.0f, pose.worldMatrix(0).m30(), 1e-3f);
            GltfSceneEvaluator.Vertex[] verts = evaluator.transform(pose, 0, 0);
            // Vertex 0: x shifted +2 by CUBICSPLINE translation, y shifted +1 (0.5 * +2 morph delta)
            assertEquals(2.0f, verts[0].x(), 1e-3f);
            assertEquals(1.0f, verts[0].y(), 1e-3f);
        } finally {
            model.closeCoreBridge();
        }
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
