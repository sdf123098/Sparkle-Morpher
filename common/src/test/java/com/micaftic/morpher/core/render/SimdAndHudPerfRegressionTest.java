package com.micaftic.morpher.core.render;

import com.micaftic.morpher.architecture.RepoSourceLookup;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.resource.models.GeometryDescription;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimdAndHudPerfRegressionTest {

    @Test
    void nativeSimdPolicyReadsConfigAndDefaultsToOff() {
        assertEquals(NativeSimdPolicy.OFF, ConfigPolicies.graphics().nativeSimdPolicy(),
                "Default NativeSimdPolicy must be OFF so fallback rendering avoids per-frame DirectByteBuffer vertex staging");
    }

    @Test
    void builtModelCachesRenderBoundsWithoutRescanningGeometry() {
        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.bounds", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        BuiltModel.BakedBone bone = new BuiltModel.BakedBone();
        bone.name = "body";
        bone.parentIdx = -1;
        BuiltModel.BakedCube cube = new BuiltModel.BakedCube();
        BuiltModel.BakedQuad quad = new BuiltModel.BakedQuad();
        quad.setVertex(0, -0.4f, 0.1f, 0f, 0f, 0f);
        quad.setVertex(1, 0.6f, 0.1f, 0f, 1f, 0f);
        quad.setVertex(2, 0.6f, 1.8f, 0f, 1f, 1f);
        quad.setVertex(3, -0.4f, 1.8f, 0f, 0f, 1f);
        quad.setNormal(0f, 0f, 1f);
        cube.quads = List.of(quad);
        bone.cubes = List.of(cube);
        model.bakedBones = List.of(bone);

        float[] first = model.getOrComputeRenderBounds();
        float[] second = model.getOrComputeRenderBounds();
        assertSame(first, second, "Render bounds must be cached across HUD frames");
        assertArrayEquals(new float[]{-0.4f, 0.1f, 0.6f, 1.8f}, first, 1e-6f);
    }

    @Test
    void hudOverlayReusesFboBuffersAndRespectsSimdPolicy() throws Exception {
        String bridge = RepoSourceLookup.readSource("com/micaftic/morpher/client/render/ModelRendererBridge.java");
        assertTrue(bridge.contains("ConfigPolicies.graphics().nativeSimdPolicy()"),
                "1.21.1 ModelRendererBridge must gate RenderBackends.simd() on ConfigPolicies.graphics().nativeSimdPolicy()");

        String preview = RepoSourceLookup.readSource("com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java");
        assertTrue(preview.contains("borrowExtraPlayerFboBuffer()"),
                "Old HUD FBO redraws must reuse persistent ByteBufferBuilders rather than leaking unclosed native buffers");
        assertTrue(preview.contains("getOrComputeRenderBounds()"),
                "Old HUD bounds check must use cached BuiltModel bounds instead of rescanning all vertices per frame");
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
        int[] emitted = new int[1];
        com.mojang.blaze3d.vertex.VertexConsumer consumer = new com.mojang.blaze3d.vertex.VertexConsumer() {
            @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { emitted[0]++; return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        com.mojang.blaze3d.vertex.PoseStack stack = new com.mojang.blaze3d.vertex.PoseStack();

        assertTrue(com.micaftic.morpher.client.render.ModelRendererBridge.nativeRenderModel(
                consumer, stack.last(), new org.joml.Matrix4f(), false,
                model, params, new float[4], 0, 0, 0x00A0, 0, 1f, 1f, 1f, 1f, false));
        assertEquals(4, emitted[0]);

        emitted[0] = 0;
        params[9] = 1f;
        assertTrue(com.micaftic.morpher.client.render.ModelRendererBridge.nativeRenderModel(
                consumer, stack.last(), new org.joml.Matrix4f(), false,
                model, params, new float[4], 0, 0, 0x00A0, 0, 1f, 1f, 1f, 1f, false));
        assertEquals(0, emitted[0]);
    }
}
