package com.micaftic.morpher.core.bridge;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.resource.models.GeometryDescription;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.FormatHint;
import dev.spm.core.api.ImportRequest;
import dev.spm.core.api.ImportResult;
import dev.spm.core.api.ModelSource;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.ResourceBudget;
import dev.spm.core.api.ResourceKey;
import dev.spm.core.api.VertexBatch;
import dev.spm.core.format.figura.FiguraAvatarRuntime;
import dev.spm.core.lua.LuaBudget;
import dev.spm.core.parser.SpmModelParser;
import dev.spm.core.render.cpu.JavaComputeProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SpmCoreIntegrationTest {

    @Test
    void builtModelBuildsSkeletonAndEvaluatesThroughComputeProvider() {
        BuiltModel mesh = new BuiltModel(new ModelBone[0], new String[37][0], new boolean[]{false, false, false},
                new GeometryDescription("geometry.test", 64, 64, 1, 2, new double[]{0, 1, 0}), new boolean[]{false});
        BuiltModel.BakedBone bone = new BuiltModel.BakedBone();
        bone.name = "Head";
        bone.pivotX = 0f;
        bone.pivotY = 24f;
        bone.pivotZ = 0f;
        BuiltModel.BakedCube cube = new BuiltModel.BakedCube();
        BuiltModel.BakedQuad quad = new BuiltModel.BakedQuad();
        quad.setNormal(0f, 1f, 0f);
        quad.setVertex(0, 0f, 1f, 0f, 0f, 0f);
        quad.setVertex(1, 1f, 1f, 0f, 1f, 0f);
        quad.setVertex(2, 1f, 1f, 1f, 1f, 1f);
        quad.setVertex(3, 0f, 1f, 1f, 0f, 1f);
        cube.quads.add(quad);
        bone.cubes.add(cube);
        mesh.bakedBones = java.util.List.of(bone);
        mesh.bakedBoneOrder = BuiltModel.buildParentFirstBoneOrder(mesh.bakedBones);
        mesh.buildPartMaskBoneRenderOrders();

        BuiltModel.ComputeCache cache = mesh.ensureComputeCache();
        assertNotNull(cache);
        assertEquals(1, cache.skeleton.boneCount());
        BuiltModel.PartBatch batch = cache.batchForMask(mesh.bakedBones, mesh.bakedBoneOrder, 0);
        assertNotNull(batch);
        assertEquals(4, batch.vertices().vertexCount());

        JavaComputeProvider provider = new JavaComputeProvider();
        BonePose pose = BonePose.identity(1);
        BoneMatrices matrices = provider.evaluateBones(cache.skeleton, pose);
        VertexBatch out = provider.renderVertices(batch.vertices(), matrices);
        assertEquals(4, out.vertexCount());
        assertEquals(0, out.colorsRgba()[0]);
        assertEquals(3, out.colorsRgba()[3]);
    }

    @Test
    void spmGltfBridgeParsesAndEvaluatesCubicSplineAnimation() throws Exception {
        // Minimal self-contained glTF 2.0 with 1 bone node and CUBICSPLINE translation animation
        byte[] bin = new byte[12 * 3 + 8 + 12 * 3 * 2]; // 3 vec3 verts + 2 scalar times + 2*3 vec3 cubicspline
        var bb = java.nio.ByteBuffer.wrap(bin).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(0).putFloat(0).putFloat(0);
        bb.putFloat(1).putFloat(0).putFloat(0);
        bb.putFloat(0).putFloat(1).putFloat(0);
        bb.putFloat(0f).putFloat(1f); // times at t=0, t=1
        // keyframe 0: inTangent(0,0,0), value(0,0,0), outTangent(0,0,0)
        for (int i = 0; i < 9; i++) bb.putFloat(0f);
        // keyframe 1: inTangent(0,0,0), value(2,0,0), outTangent(0,0,0)
        bb.putFloat(0f).putFloat(0f).putFloat(0f);
        bb.putFloat(2f).putFloat(0f).putFloat(0f);
        bb.putFloat(0f).putFloat(0f).putFloat(0f);
        String b64 = Base64.getEncoder().encodeToString(bin);
        String gltfJson = """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "nodes": [{"name": "Root", "mesh": 0}],
                  "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}],
                  "buffers": [{"byteLength": %d, "uri": "data:application/octet-stream;base64,%s"}],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 8},
                    {"buffer": 0, "byteOffset": 44, "byteLength": 72}
                  ],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                    {"bufferView": 1, "componentType": 5126, "count": 2, "type": "SCALAR", "min": [0], "max": [1]},
                    {"bufferView": 2, "componentType": 5126, "count": 6, "type": "VEC3"}
                  ],
                  "animations": [{
                    "name": "slide",
                    "samplers": [{"input": 1, "interpolation": "CUBICSPLINE", "output": 2}],
                    "channels": [{"sampler": 0, "target": {"node": 0, "path": "translation"}}]
                  }]
                }
                """.formatted(bin.length, b64);

        try (SpmGltfBridge.ImportedGltf gltf = SpmGltfBridge.parse(
                gltfJson.getBytes(StandardCharsets.UTF_8), "cubic.gltf")) {
            assertTrue(gltf.animations().hasClips());
            assertEquals(java.util.List.of("slide"), gltf.clipNames());
            var frame = gltf.evaluateFrame("slide", 0.5, true,
                    new ResourceBudget(64L << 20), new CancellationToken());
            assertNotNull(frame);
        }
    }

    @Test
    void figuraRuntimeExecutesLua53ScriptAgainstImportedAvatar() throws Exception {
        String pngB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X2XkAAAAASUVORK5CYII=";
        String bbmodel = """
                {
                  "meta": {"format_version": "4.10", "model_format": "free"},
                  "name": "avatar",
                  "textures": [{"uuid": "skin", "source": "data:image/png;base64,%s"}],
                  "elements": [{"uuid": "c1", "name": "cube", "type": "cube", "from": [-4, 24, -4], "to": [4, 32, 4], "origin": [0, 24, 0], "faces": {"east": {"uv": [0, 0, 8, 8], "texture": 0}}}],
                  "outliner": [{"uuid": "g1", "name": "Head", "origin": [0, 24, 0], "rotation": [0, 0, 0], "children": ["c1"]}]
                }
                """.formatted(pngB64);
        String lua = """
                function events.render(delta, ctx)
                  models.model.Head:setRot(0, 45, 0)
                end
                """;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("avatar.json", "{\"name\":\"TestFigura\"}".getBytes(StandardCharsets.UTF_8));
        entries.put("model.bbmodel", bbmodel.getBytes(StandardCharsets.UTF_8));
        entries.put("script.lua", lua.getBytes(StandardCharsets.UTF_8));
        byte[] zip = zipBytes(entries);

        CancellationToken token = new CancellationToken();
        try (ImportResult imported = new SpmModelParser().importModel(new ImportRequest(
                new ModelSource("test:figura", new ResourceKey("avatar.zip"), k -> new ByteArrayInputStream(zip)),
                FormatHint.FIGURA,
                DecodePolicy.localModel(),
                new ResourceBudget(64L << 20),
                token))) {
            FiguraAvatarRuntime runtime = FiguraAvatarRuntime.create(
                    imported, LuaBudget.avatarDefault(token), FiguraAvatarRuntime.HostSnapshot.idle("Steve"));
            runtime.initialize();
            var frame = runtime.render(0.5f, FiguraAvatarRuntime.HostSnapshot.idle("Steve"));
            BonePose pose = frame.modelPoses().values().iterator().next();
            assertEquals((float) Math.toRadians(45.0), pose.rotations()[1], 1e-4f);
        }
    }

    private static byte[] zipBytes(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
