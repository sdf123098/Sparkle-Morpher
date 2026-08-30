package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.resource.gltf.GltfSceneEvaluator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Minecraft-side adapter for the independent glTF scene evaluator.
 *
 * <p>The caller chooses the RenderType and binds the material texture. This
 * class only emits indexed triangles, applies the current PoseStack, and
 * multiplies the material base-color factor into the vertex color. Keeping
 * RenderType/texture selection outside this adapter allows the same evaluator
 * to feed previews, entities, and a later GPU implementation.</p>
 */
public final class GltfVertexConsumerRenderer {
    private GltfVertexConsumerRenderer() {}

    /** Validates that an indexed primitive can be emitted as complete triangles. */
    public static int validateTriangleIndexCount(int indexCount) {
        if (indexCount < 0 || (indexCount % 3) != 0) {
            throw new IllegalArgumentException("glTF TRIANGLES index count must be a multiple of 3: " + indexCount);
        }
        return indexCount;
    }

    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
                              float red, float green, float blue, float alpha) {
        render(model, evaluator, pose, poseStack, material -> consumer, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    /** Emits each primitive through a material-aware consumer factory. */
    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack poseStack, Function<GltfModel.Material, VertexConsumer> consumerFactory,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        render(model, evaluator, pose, poseStack, consumerFactory, packedLight, packedOverlay,
                red, green, blue, alpha, node -> true);
    }

    /** Emits only nodes accepted by {@code nodeFilter}; descendants are still traversed. */
    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack poseStack, Function<GltfModel.Material, VertexConsumer> consumerFactory,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                              Predicate<GltfModel.Node> nodeFilter) {
        render(model, evaluator, pose, poseStack, consumerFactory, packedLight, packedOverlay,
                red, green, blue, alpha, nodeFilter, material -> true);
    }

    /** Emits only primitives accepted by {@code materialFilter}; useful for separate render passes. */
    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack poseStack, Function<GltfModel.Material, VertexConsumer> consumerFactory,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                              Predicate<GltfModel.Node> nodeFilter,
                              Predicate<GltfModel.Material> materialFilter) {
        if (model.scenes().isEmpty()) return;
        for (int root : model.scenes().get(pose.sceneIndex()).roots()) {
            renderNode(model, evaluator, pose, root, poseStack, consumerFactory, packedLight, packedOverlay,
                    red, green, blue, alpha, nodeFilter, materialFilter);
        }
    }

    /**
     * Emits through a pose already captured by a deferred render submission.
     * This is the adapter needed by 26.x SubmitNodeCollector callbacks, where
     * the callback receives PoseStack.Pose instead of the mutable PoseStack.
     */
    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack.Pose capturedPose,
                              Function<GltfModel.Material, VertexConsumer> consumerFactory,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        render(model, evaluator, pose, capturedPose, consumerFactory, packedLight, packedOverlay,
                red, green, blue, alpha, node -> true, material -> true);
    }

    public static void render(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                              PoseStack.Pose capturedPose,
                              Function<GltfModel.Material, VertexConsumer> consumerFactory,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                              Predicate<GltfModel.Node> nodeFilter,
                              Predicate<GltfModel.Material> materialFilter) {
        if (model.scenes().isEmpty()) return;
        for (int root : model.scenes().get(pose.sceneIndex()).roots()) {
            renderCapturedNode(model, evaluator, pose, root, capturedPose, consumerFactory,
                    packedLight, packedOverlay, red, green, blue, alpha, nodeFilter, materialFilter);
        }
    }

    private static void renderNode(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                                   int nodeIndex, PoseStack poseStack, Function<GltfModel.Material, VertexConsumer> consumerFactory, int packedLight,
                                   int packedOverlay, float red, float green, float blue, float alpha,
                                   Predicate<GltfModel.Node> nodeFilter,
                                   Predicate<GltfModel.Material> materialFilter) {
        GltfModel.Node node = model.nodes().get(nodeIndex);
        if (nodeFilter.test(node) && node.meshIndex() >= 0) {
            GltfModel.Mesh mesh = model.meshes().get(node.meshIndex());
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                GltfModel.Primitive primitive = mesh.primitives().get(primitiveIndex);
                GltfSceneEvaluator.Vertex[] vertices = evaluator.transform(pose, nodeIndex, primitiveIndex);
                GltfModel.Material material = primitive.materialIndex() < 0
                        ? null : model.materials().get(primitive.materialIndex());
                if (!materialFilter.test(material)) continue;
                VertexConsumer consumer = consumerFactory.apply(material);
                if (consumer == null) continue;
                emitPrimitive(primitive, vertices, material, poseStack, consumer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            }
        }
        for (int child : node.children()) {
            renderNode(model, evaluator, pose, child, poseStack, consumerFactory, packedLight, packedOverlay,
                    red, green, blue, alpha, nodeFilter, materialFilter);
        }
    }

    private static void renderCapturedNode(GltfModel model, GltfSceneEvaluator evaluator, GltfSceneEvaluator.Pose pose,
                                           int nodeIndex, PoseStack.Pose capturedPose,
                                           Function<GltfModel.Material, VertexConsumer> consumerFactory, int packedLight,
                                           int packedOverlay, float red, float green, float blue, float alpha,
                                           Predicate<GltfModel.Node> nodeFilter,
                                           Predicate<GltfModel.Material> materialFilter) {
        GltfModel.Node node = model.nodes().get(nodeIndex);
        if (nodeFilter.test(node) && node.meshIndex() >= 0) {
            GltfModel.Mesh mesh = model.meshes().get(node.meshIndex());
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                GltfModel.Primitive primitive = mesh.primitives().get(primitiveIndex);
                GltfSceneEvaluator.Vertex[] vertices = evaluator.transform(pose, nodeIndex, primitiveIndex);
                GltfModel.Material material = primitive.materialIndex() < 0
                        ? null : model.materials().get(primitive.materialIndex());
                if (!materialFilter.test(material)) continue;
                VertexConsumer consumer = consumerFactory.apply(material);
                if (consumer == null) continue;
                emitPrimitive(primitive, vertices, material, capturedPose, consumer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            }
        }
        for (int child : node.children()) {
            renderCapturedNode(model, evaluator, pose, child, capturedPose, consumerFactory,
                    packedLight, packedOverlay, red, green, blue, alpha, nodeFilter, materialFilter);
        }
    }

    private static void emitPrimitive(GltfModel.Primitive primitive, GltfSceneEvaluator.Vertex[] vertices,
                                      GltfModel.Material material, PoseStack poseStack, VertexConsumer consumer,
                                      int packedLight, int packedOverlay, float red, float green, float blue,
                                      float alpha) {
        emitPrimitive(primitive, vertices, material, poseStack.last(), consumer, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    private static void emitPrimitive(GltfModel.Primitive primitive, GltfSceneEvaluator.Vertex[] vertices,
                                      GltfModel.Material material, PoseStack.Pose pose, VertexConsumer consumer,
                                      int packedLight, int packedOverlay, float red, float green, float blue,
                                      float alpha) {
        float[] factor = material == null ? new float[]{1, 1, 1, 1} : material.baseColorFactor();
        int[] indices = primitive.indices();
        validateTriangleIndexCount(indices.length);
        for (int index : indices) {
            if (index < 0 || index >= vertices.length) {
                throw new IllegalArgumentException("glTF triangle index is outside the transformed vertex array: " + index);
            }
            GltfSceneEvaluator.Vertex vertex = vertices[index];
            if (!isFinite(vertex)) {
                throw new IllegalArgumentException("glTF vertex contains a non-finite component at index " + index);
            }
            int color = packColor(red * factor[0] * vertex.red(), green * factor[1] * vertex.green(),
                    blue * factor[2] * vertex.blue(), alpha * factor[3] * vertex.alpha());
            Vector3f position = new Vector3f(vertex.x(), vertex.y(), vertex.z());
            Vector3f normal = new Vector3f(vertex.normalX(), vertex.normalY(), vertex.normalZ());
            pose.pose().transformPosition(position);
            pose.normal().transform(normal).normalize();
            if (!isFinite(position) || !isFinite(normal)) {
                throw new IllegalArgumentException("glTF vertex transform produced a non-finite component at index " + index);
            }
            consumer.addVertex(position.x(), position.y(), position.z(), color,
                    vertex.u(), vertex.v(), packedOverlay, packedLight,
                    normal.x(), normal.y(), normal.z());
        }
    }

    private static boolean isFinite(GltfSceneEvaluator.Vertex vertex) {
        return Float.isFinite(vertex.x()) && Float.isFinite(vertex.y()) && Float.isFinite(vertex.z())
                && Float.isFinite(vertex.normalX()) && Float.isFinite(vertex.normalY())
                && Float.isFinite(vertex.normalZ()) && Float.isFinite(vertex.u()) && Float.isFinite(vertex.v())
                && Float.isFinite(vertex.red()) && Float.isFinite(vertex.green())
                && Float.isFinite(vertex.blue()) && Float.isFinite(vertex.alpha());
    }

    private static boolean isFinite(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    private static int packColor(float red, float green, float blue, float alpha) {
        int r = channel(red);
        int g = channel(green);
        int b = channel(blue);
        int a = channel(alpha);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(float value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
