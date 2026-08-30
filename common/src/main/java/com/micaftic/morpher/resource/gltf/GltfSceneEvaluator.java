package com.micaftic.morpher.resource.gltf;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Evaluates a glTF scene without knowing anything about Minecraft rendering.
 * The future VertexConsumer/GPU adapters consume the transformed vertices
 * produced here, keeping node, animation, and skin rules in one place.
 */
public final class GltfSceneEvaluator {
    private final GltfModel model;

    public GltfSceneEvaluator(GltfModel model) {
        this.model = model;
    }

    public GltfModel model() {
        return model;
    }

    /** Evaluates the default scene at the supplied animation time. */
    public Pose evaluate(float timeSeconds) {
        return evaluate(model.defaultScene(), -1, timeSeconds, true);
    }

    /** Evaluates one scene and animation. A negative animation index means bind pose. */
    public Pose evaluate(int sceneIndex, int animationIndex, float timeSeconds, boolean loop) {
        if (sceneIndex < 0 || sceneIndex >= model.scenes().size()) {
            throw new IllegalArgumentException("Invalid glTF scene index: " + sceneIndex);
        }
        if (animationIndex >= model.animations().size()) {
            throw new IllegalArgumentException("Invalid glTF animation index: " + animationIndex);
        }
        int nodeCount = model.nodes().size();
        float[][] translations = new float[nodeCount][];
        float[][] rotations = new float[nodeCount][];
        float[][] scales = new float[nodeCount][];
        for (int i = 0; i < nodeCount; i++) {
            GltfModel.Node node = model.nodes().get(i);
            translations[i] = node.translation();
            rotations[i] = node.rotation();
            scales[i] = node.scale();
        }

        float animationTime = Float.isFinite(timeSeconds) ? timeSeconds : 0.0f;
        if (animationIndex >= 0) {
            GltfModel.Animation animation = model.animations().get(animationIndex);
            if (loop && animation.duration() > 0) {
                animationTime = animationTime % animation.duration();
                if (animationTime < 0) animationTime += animation.duration();
            } else {
                animationTime = Math.max(0, Math.min(animation.duration(), animationTime));
            }
            for (GltfModel.Channel channel : animation.channels()) {
                GltfModel.Node node = model.nodes().get(channel.nodeIndex());
                if (node.matrix() != null) {
                    throw new IllegalArgumentException("Animated matrix nodes are not supported; use TRS transforms: " + node.name());
                }
                float[] target = switch (channel.path()) {
                    case TRANSLATION -> translations[channel.nodeIndex()];
                    case ROTATION -> rotations[channel.nodeIndex()];
                    case SCALE -> scales[channel.nodeIndex()];
                };
                sample(channel, animationTime, target);
            }
        }

        Matrix4f[] world = new Matrix4f[nodeCount];
        int[] visitState = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            computeWorld(i, translations, rotations, scales, world, visitState);
        }
        return new Pose(sceneIndex, animationIndex, animationTime, world);
    }

    /** Transforms one mesh primitive to world-space vertices using a computed pose. */
    public Vertex[] transform(Pose pose, int nodeIndex, int primitiveIndex) {
        GltfModel.Node node = model.nodes().get(nodeIndex);
        if (node.meshIndex() < 0) return new Vertex[0];
        GltfModel.Primitive primitive = model.meshes().get(node.meshIndex()).primitives().get(primitiveIndex);
        float[] positions = primitive.positions();
        float[] normals = primitive.normals();
        float[] texCoords = primitive.texCoords();
        float[] colors = primitive.colors();
        int[] joints = primitive.joints();
        float[] weights = primitive.weights();
        Vertex[] result = new Vertex[primitive.vertexCount()];

        GltfModel.Skin skin = node.skinIndex() < 0 ? null : model.skins().get(node.skinIndex());
        Matrix4f[] jointMatrices = skin == null ? null : jointMatrices(pose, nodeIndex, skin);
        Matrix4f nodeWorld = pose.worldMatrices[nodeIndex];
        for (int vertex = 0; vertex < result.length; vertex++) {
            Vector3f position = new Vector3f(
                    positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2]);
            Vector3f normal = normals == null ? null : new Vector3f(
                    normals[vertex * 3], normals[vertex * 3 + 1], normals[vertex * 3 + 2]);
            if (jointMatrices == null) {
                nodeWorld.transformPosition(position);
                if (normal != null) nodeWorld.transformDirection(normal).normalize();
            } else {
                position = skinPosition(position, joints, weights, vertex, jointMatrices, nodeWorld);
                if (normal != null) normal = skinDirection(normal, joints, weights, vertex, jointMatrices, nodeWorld);
            }
            float u = texCoords == null ? 0 : texCoords[vertex * 2];
            float v = texCoords == null ? 0 : texCoords[vertex * 2 + 1];
            float r = colors == null ? 1 : colors[vertex * 4];
            float g = colors == null ? 1 : colors[vertex * 4 + 1];
            float b = colors == null ? 1 : colors[vertex * 4 + 2];
            float a = colors == null ? 1 : colors[vertex * 4 + 3];
            result[vertex] = new Vertex(position.x, position.y, position.z,
                    normal == null ? 0 : normal.x, normal == null ? 0 : normal.y, normal == null ? 1 : normal.z,
                    u, v, r, g, b, a);
        }
        return result;
    }

    private Matrix4f[] jointMatrices(Pose pose, int meshNodeIndex, GltfModel.Skin skin) {
        Matrix4f inverseMeshWorld = new Matrix4f(pose.worldMatrices[meshNodeIndex]).invert();
        float[] inverseBind = skin.inverseBindMatrices();
        Matrix4f[] result = new Matrix4f[skin.joints().size()];
        for (int i = 0; i < result.length; i++) {
            int jointNode = skin.joints().get(i);
            result[i] = new Matrix4f(inverseMeshWorld)
                    .mul(pose.worldMatrices[jointNode])
                    .mul(new Matrix4f().set(Arrays.copyOfRange(inverseBind, i * 16, i * 16 + 16)));
        }
        return result;
    }

    private static Vector3f skinPosition(Vector3f source, int[] joints, float[] weights, int vertex,
                                         Matrix4f[] jointMatrices, Matrix4f nodeWorld) {
        Vector3f result = new Vector3f();
        for (int influence = 0; influence < 4; influence++) {
            float weight = weights[vertex * 4 + influence];
            if (weight == 0) continue;
            int joint = joints[vertex * 4 + influence];
            if (joint < 0 || joint >= jointMatrices.length) continue;
            Vector3f transformed = new Vector3f(source);
            jointMatrices[joint].transformPosition(transformed).mul(weight);
            result.add(transformed);
        }
        nodeWorld.transformPosition(result);
        return result;
    }

    private static Vector3f skinDirection(Vector3f source, int[] joints, float[] weights, int vertex,
                                           Matrix4f[] jointMatrices, Matrix4f nodeWorld) {
        Vector3f result = new Vector3f();
        for (int influence = 0; influence < 4; influence++) {
            float weight = weights[vertex * 4 + influence];
            if (weight == 0) continue;
            int joint = joints[vertex * 4 + influence];
            if (joint < 0 || joint >= jointMatrices.length) continue;
            Vector3f transformed = new Vector3f(source);
            jointMatrices[joint].transformDirection(transformed).mul(weight);
            result.add(transformed);
        }
        nodeWorld.transformDirection(result).normalize();
        return result;
    }

    private void computeWorld(int index, float[][] translations, float[][] rotations, float[][] scales,
                              Matrix4f[] world, int[] visitState) {
        if (visitState[index] == 2) return;
        if (visitState[index] == 1) throw new IllegalArgumentException("Cycle in glTF node hierarchy at node " + index);
        visitState[index] = 1;
        GltfModel.Node node = model.nodes().get(index);
        Matrix4f local;
        if (node.matrix() != null) {
            local = new Matrix4f().set(node.matrix());
        } else {
            float[] t = translations[index];
            float[] r = rotations[index];
            float[] s = scales[index];
            local = new Matrix4f().translation(t[0], t[1], t[2])
                    .rotate(new Quaternionf(r[0], r[1], r[2], r[3]))
                    .scale(s[0], s[1], s[2]);
        }
        int parent = node.parentIndex();
        if (parent >= 0) {
            computeWorld(parent, translations, rotations, scales, world, visitState);
            world[index] = new Matrix4f(world[parent]).mul(local);
        } else {
            world[index] = local;
        }
        visitState[index] = 2;
    }

    private static void sample(GltfModel.Channel channel, float time, float[] target) {
        float[] times = channel.times();
        float[] values = channel.values();
        if (times.length == 0) return;
        int next = Arrays.binarySearch(times, time);
        if (next >= 0) {
            copySample(values, next, channel.components(), target);
            return;
        }
        next = -next - 1;
        if (next <= 0) {
            copySample(values, 0, channel.components(), target);
            return;
        }
        if (next >= times.length) {
            copySample(values, times.length - 1, channel.components(), target);
            return;
        }
        int previous = next - 1;
        if (channel.interpolation() == GltfModel.Channel.Interpolation.STEP) {
            copySample(values, previous, channel.components(), target);
            return;
        }
        float amount = (time - times[previous]) / (times[next] - times[previous]);
        int components = channel.components();
        if (channel.path() == GltfModel.Channel.Path.ROTATION) {
            Quaternionf a = new Quaternionf(values[previous * 4], values[previous * 4 + 1],
                    values[previous * 4 + 2], values[previous * 4 + 3]);
            Quaternionf b = new Quaternionf(values[next * 4], values[next * 4 + 1],
                    values[next * 4 + 2], values[next * 4 + 3]);
            a.slerp(b, amount).normalize();
            target[0] = a.x;
            target[1] = a.y;
            target[2] = a.z;
            target[3] = a.w;
        } else {
            for (int component = 0; component < components; component++) {
                float a = values[previous * components + component];
                float b = values[next * components + component];
                target[component] = a + (b - a) * amount;
            }
        }
    }

    private static void copySample(float[] values, int sample, int components, float[] target) {
        System.arraycopy(values, sample * components, target, 0, components);
    }

    public static final class Pose {
        private final int sceneIndex;
        private final int animationIndex;
        private final float timeSeconds;
        private final Matrix4f[] worldMatrices;

        private Pose(int sceneIndex, int animationIndex, float timeSeconds, Matrix4f[] worldMatrices) {
            this.sceneIndex = sceneIndex;
            this.animationIndex = animationIndex;
            this.timeSeconds = timeSeconds;
            this.worldMatrices = worldMatrices;
        }

        public int sceneIndex() { return sceneIndex; }
        public int animationIndex() { return animationIndex; }
        public float timeSeconds() { return timeSeconds; }
        public Matrix4f worldMatrix(int nodeIndex) { return new Matrix4f(worldMatrices[nodeIndex]); }
    }

    public record Vertex(float x, float y, float z, float normalX, float normalY, float normalZ,
                         float u, float v, float red, float green, float blue, float alpha) {}
}
