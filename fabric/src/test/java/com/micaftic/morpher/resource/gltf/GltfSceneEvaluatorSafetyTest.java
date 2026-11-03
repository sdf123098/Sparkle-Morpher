package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfSceneEvaluatorSafetyTest {
    @TempDir
    Path tempDir;

    @Test
    void clampsNonFiniteAnimationTimeBeforeSampling() throws Exception {
        GltfModel model = GltfLoader.load(animationFixture().getBytes(StandardCharsets.UTF_8), tempDir,
                "animation.gltf");

        GltfSceneEvaluator.Pose pose = new GltfSceneEvaluator(model).evaluate(0, 0, Float.NaN, false);

        assertEquals(0.0f, pose.timeSeconds());
        for (int node = 0; node < model.nodes().size(); node++) {
            assertTrue(isFinite(pose.worldMatrix(node)), "node matrix must remain finite: " + node);
        }
    }

    private static String animationFixture() {
        ByteBuffer buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(1);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        String uri = "data:application/octet-stream;base64,"
                + Base64.getEncoder().encodeToString(buffer.array());
        return "{\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"byteLength\":32,\"uri\":\"" + uri + "\"}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":8},"
                + "{\"buffer\":0,\"byteOffset\":8,\"byteLength\":24}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}],"
                + "\"nodes\":[{}],\"animations\":[{\"samplers\":[{\"input\":0,\"output\":1}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"translation\"}}]}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0}";
    }

    private static boolean isFinite(org.joml.Matrix4f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02())
                && Float.isFinite(matrix.m03()) && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
                && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13()) && Float.isFinite(matrix.m20())
                && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32())
                && Float.isFinite(matrix.m33());
    }
}
