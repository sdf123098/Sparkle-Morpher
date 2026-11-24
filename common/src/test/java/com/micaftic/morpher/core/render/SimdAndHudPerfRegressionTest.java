package com.micaftic.morpher.core.render;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.client.render.ModelRendererBridge;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimdAndHudPerfRegressionTest {

    private static final class CountingVertexConsumer implements VertexConsumer {
        int vertices;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    @Test
    void nativeSimdPolicyReadsConfigAndDefaultsToOff() {
        assertEquals(NativeSimdPolicy.OFF, ConfigPolicies.graphics().nativeSimdPolicy(),
                "Default NativeSimdPolicy must be OFF so CPU submit/HUD frames avoid per-frame DirectByteBuffer vertex staging");
    }

    @Test
    void renderBackendDecisionAvoidsNativeSimdWhenPolicyIsOff() {
        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        BuiltModel.BakedBone bone = new BuiltModel.BakedBone();
        bone.name = "root";
        bone.parentIdx = -1;
        model.bakedBones = List.of(bone);

        RenderBackendDecision decision = RenderBackendDecision.choose(
                model, false, false, false, "sparkle_morpher:test", ConfigPolicies.graphics().nativeSimdPolicy());
        assertEquals(RenderBackendDecision.Backend.JAVA, decision.backend,
                "When GPU is unavailable, default policy must select JAVA instead of per-frame NATIVE_SIMD staging");
    }

    @Test
    void javaRenderModelProcessesLargeMeshCleanlyWithHoistedColor() {
        int boneCount = 8;
        int cubesPerBone = 200; // 8 * 200 * 6 quads = 9,600 quads = 38,400 vertices
        List<BuiltModel.BakedBone> bones = new ArrayList<>(boneCount);
        for (int b = 0; b < boneCount; b++) {
            BuiltModel.BakedBone bone = new BuiltModel.BakedBone();
            bone.name = "bone_" + b;
            bone.parentIdx = b - 1;
            bone.cubes = new ArrayList<>(cubesPerBone);
            for (int c = 0; c < cubesPerBone; c++) {
                BuiltModel.BakedCube cube = new BuiltModel.BakedCube();
                cube.quads = new ArrayList<>(6);
                for (int q = 0; q < 6; q++) {
                    BuiltModel.BakedQuad quad = new BuiltModel.BakedQuad();
                    quad.setVertex(0, 0f, 0f, 0f, 0f, 0f);
                    quad.setVertex(1, 1f, 0f, 0f, 1f, 0f);
                    quad.setVertex(2, 1f, 1f, 0f, 1f, 1f);
                    quad.setVertex(3, 0f, 1f, 0f, 0f, 1f);
                    quad.setNormal(0f, 0f, 1f);
                    cube.quads.add(quad);
                }
                bone.cubes.add(cube);
            }
            bones.add(bone);
        }
        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.bench", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = bones;
        model.bakedBoneOrder = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
        model.bakedBoneRenderOrderAll = model.bakedBoneOrder;

        float[] params = new float[boneCount * 12];
        for (int b = 0; b < boneCount; b++) {
            params[b * 12 + 6] = 1f;
            params[b * 12 + 7] = 1f;
            params[b * 12 + 8] = 1f;
        }
        CountingVertexConsumer consumer = new CountingVertexConsumer();
        PoseStack stack = new PoseStack();

        ModelRendererBridge.renderModel(consumer, stack.last(), new org.joml.Matrix4f(), false,
                model, params, new float[boneCount * 4], 0, 0, 0xF000F0, 0, 1f, 1f, 1f, 1f, false);
        assertEquals(boneCount * cubesPerBone * 6 * 4, consumer.vertices);
    }

    @Test
    void nativeRenderModelBypassesSlowCoreStagingAndHandlesHiddenBonesWithoutFallback() {
        BuiltModel.BakedBone visibleBone = new BuiltModel.BakedBone();
        visibleBone.name = "vis";
        visibleBone.parentIdx = -1;
        BuiltModel.BakedCube cube = new BuiltModel.BakedCube();
        BuiltModel.BakedQuad quad = new BuiltModel.BakedQuad();
        quad.setVertex(0, 0f, 0f, 0f, 0.1f, 0.2f);
        quad.setVertex(1, 1f, 0f, 0f, 0.3f, 0.2f);
        quad.setVertex(2, 1f, 1f, 0f, 0.3f, 0.4f);
        quad.setVertex(3, 0f, 1f, 0f, 0.1f, 0.4f);
        quad.setNormal(0f, 0f, 1f);
        assertArrayEquals(new float[]{0f, 1f, 1f, 0f}, quad.soaX, 1e-6f);
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, quad.soaY, 1e-6f);
        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, quad.soaZ, 1e-6f);
        cube.quads = List.of(quad);
        visibleBone.cubes = List.of(cube);

        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.native_bypass", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = List.of(visibleBone);
        model.bakedBoneOrder = new int[]{0};
        model.bakedBoneRenderOrderAll = new int[]{0};

        float[] params = new float[12];
        params[6] = 1f; params[7] = 1f; params[8] = 1f;
        CountingVertexConsumer consumer = new CountingVertexConsumer();
        PoseStack stack = new PoseStack();

        assertTrue(ModelRendererBridge.nativeRenderModel(
                consumer, stack.last(), new org.joml.Matrix4f(), false,
                model, params, new float[4], 0, 0, 0x00A0, 0, 1f, 1f, 1f, 1f, true));
        assertEquals(4, consumer.vertices);

        // Hidden bone: must still return true (zero emitted vertices is valid, not a failure)
        params[9] = 1f;
        CountingVertexConsumer hiddenConsumer = new CountingVertexConsumer();
        assertTrue(ModelRendererBridge.nativeRenderModel(
                hiddenConsumer, stack.last(), new org.joml.Matrix4f(), false,
                model, params, new float[4], 0, 0, 0x00A0, 0, 1f, 1f, 1f, 1f, false));
        assertEquals(0, hiddenConsumer.vertices);
    }

    @Test
    void vulkanBoneStorageTracksSsboDrawsInDiagnostics() {
        com.micaftic.morpher.core.gpu.VulkanBoneStorage.ssboDrawSubmitted();
        com.micaftic.morpher.core.gpu.VulkanBoneStorage.ssboDrawCompleted();
        String diag = com.micaftic.morpher.core.gpu.VulkanBoneStorage.diagnostics();
        assertTrue(diag.contains("ssbo_gpu_draws="), "diagnostics must report ssbo_gpu_draws");
        assertTrue(diag.contains("fence_completed_ssbo_snapshots="), "diagnostics must report fence_completed_ssbo_snapshots");
    }
}
