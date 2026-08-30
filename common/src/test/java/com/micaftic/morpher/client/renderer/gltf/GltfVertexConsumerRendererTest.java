package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.resource.gltf.GltfSceneEvaluator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GltfVertexConsumerRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsOneVertexPerIndexInCompleteTriangleGroups() throws Exception {
        GltfModel model = GltfLoader.load(fixture().getBytes(StandardCharsets.UTF_8), tempDir, "triangles.gltf");
        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        GltfSceneEvaluator.Pose pose = evaluator.evaluate(0, -1, 0.0f, false);
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();

        GltfVertexConsumerRenderer.render(model, evaluator, pose, new PoseStack(), material -> consumer,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f);

        assertEquals(6, consumer.vertexCount);
    }

    @Test
    void canRenderOnlyPrimitivesForOneMaterialPass() throws Exception {
        GltfModel model = GltfLoader.load(fixture().getBytes(StandardCharsets.UTF_8), tempDir, "triangles.gltf");
        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        GltfSceneEvaluator.Pose pose = evaluator.evaluate(0, -1, 0.0f, false);
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();

        GltfVertexConsumerRenderer.render(model, evaluator, pose, new PoseStack(), material -> consumer,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f,
                node -> true, material -> false);

        assertEquals(0, consumer.vertexCount);
    }

    @Test
    void emitsWithAAlreadyCapturedPoseStackPose() throws Exception {
        GltfModel model = GltfLoader.load(fixture().getBytes(StandardCharsets.UTF_8), tempDir, "triangles.gltf");
        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        GltfSceneEvaluator.Pose pose = evaluator.evaluate(0, -1, 0.0f, false);
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        PoseStack poseStack = new PoseStack();

        GltfVertexConsumerRenderer.render(model, evaluator, pose, poseStack.last(), material -> consumer,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f);

        assertEquals(6, consumer.vertexCount);
    }

    private static String fixture() {
        ByteBuffer buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        buffer.putShort((short) 0).putShort((short) 2).putShort((short) 1);
        String uri = "data:application/octet-stream;base64,"
                + Base64.getEncoder().encodeToString(buffer.array());
        return "{\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"byteLength\":48,\"uri\":\"" + uri + "\"}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":12}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":6,\"type\":\"SCALAR\"}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1}]}],"
                + "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0}";
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private int vertexCount;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertexCount++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

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
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        public VertexConsumer setLineWidth(float lineWidth) {
            return this;
        }
    }
}
