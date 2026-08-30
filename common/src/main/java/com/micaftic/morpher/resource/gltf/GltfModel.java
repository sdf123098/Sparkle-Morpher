package com.micaftic.morpher.resource.gltf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The platform-independent representation of the glTF 2.0 core model data.
 *
 * <p>This model deliberately does not depend on Minecraft, GeckoLib, or the
 * existing YSM cube representation.  Rendering and animation can therefore
 * evolve independently from the legacy import path.</p>
 */
public final class GltfModel {
    private final String generator;
    private final List<Scene> scenes;
    private final int defaultScene;
    private final List<Node> nodes;
    private final List<Mesh> meshes;
    private final List<Material> materials;
    private final List<Texture> textures;
    private final List<Image> images;
    private final List<Skin> skins;
    private final List<Animation> animations;

    GltfModel(String generator, List<Scene> scenes, int defaultScene, List<Node> nodes,
              List<Mesh> meshes, List<Material> materials, List<Image> images,
              List<Texture> textures, List<Skin> skins, List<Animation> animations) {
        this.generator = generator;
        this.scenes = immutable(scenes);
        this.defaultScene = defaultScene;
        this.nodes = immutable(nodes);
        this.meshes = immutable(meshes);
        this.materials = immutable(materials);
        this.textures = immutable(textures);
        this.images = immutable(images);
        this.skins = immutable(skins);
        this.animations = immutable(animations);
    }

    public String generator() { return generator; }

    /**
     * Returns the Minecraft render scale for known exporters.
     *
     * <p>The NetEase 4D skin exporter writes pixel-space coordinates while
     * standard glTF uses scene units. A Minecraft block is 16 model pixels,
     * so only that explicitly identified exporter receives the conversion.
     * Unknown and ordinary glTF files retain a scale of {@code 1}.</p>
     */
    public float recommendedMinecraftScale() {
        String source = generator == null ? "" : generator.toLowerCase(Locale.ROOT);
        return source.contains("netease") && source.contains("4d") ? 1.0f / 16.0f : 1.0f;
    }

    public List<Scene> scenes() { return scenes; }
    public int defaultScene() { return defaultScene; }
    public List<Node> nodes() { return nodes; }
    public List<Mesh> meshes() { return meshes; }
    public List<Material> materials() { return materials; }
    public List<Texture> textures() { return textures; }
    public List<Image> images() { return images; }
    public List<Skin> skins() { return skins; }
    public List<Animation> animations() { return animations; }

    private static <T> List<T> immutable(List<T> value) {
        return Collections.unmodifiableList(new ArrayList<>(value));
    }

    public static final class Scene {
        private final String name;
        private final List<Integer> roots;

        Scene(String name, List<Integer> roots) {
            this.name = name;
            this.roots = immutable(roots);
        }

        public String name() { return name; }
        public List<Integer> roots() { return roots; }
    }

    public static final class Node {
        private final String name;
        private final int parentIndex;
        private final List<Integer> children;
        private final int meshIndex;
        private final int skinIndex;
        private final float[] translation;
        private final float[] rotation;
        private final float[] scale;
        private final float[] matrix;

        Node(String name, int parentIndex, List<Integer> children, int meshIndex, int skinIndex,
             float[] translation, float[] rotation, float[] scale, float[] matrix) {
            this.name = name;
            this.parentIndex = parentIndex;
            this.children = immutable(children);
            this.meshIndex = meshIndex;
            this.skinIndex = skinIndex;
            this.translation = copy(translation);
            this.rotation = copy(rotation);
            this.scale = copy(scale);
            this.matrix = matrix == null ? null : copy(matrix);
        }

        public String name() { return name; }
        public int parentIndex() { return parentIndex; }
        public List<Integer> children() { return children; }
        public int meshIndex() { return meshIndex; }
        public int skinIndex() { return skinIndex; }
        public float[] translation() { return copy(translation); }
        public float[] rotation() { return copy(rotation); }
        public float[] scale() { return copy(scale); }
        public float[] matrix() { return matrix == null ? null : copy(matrix); }
    }

    public static final class Mesh {
        private final String name;
        private final List<Primitive> primitives;

        Mesh(String name, List<Primitive> primitives) {
            this.name = name;
            this.primitives = immutable(primitives);
        }

        public String name() { return name; }
        public List<Primitive> primitives() { return primitives; }
    }

    public static final class Primitive {
        private final float[] positions;
        private final float[] normals;
        private final float[] texCoords;
        private final float[] colors;
        private final int[] joints;
        private final float[] weights;
        private final int[] indices;
        private final int materialIndex;

        Primitive(float[] positions, float[] normals, float[] texCoords, float[] colors, int[] joints,
                  float[] weights, int[] indices, int materialIndex) {
            this.positions = copy(positions);
            this.normals = normals == null ? null : copy(normals);
            this.texCoords = texCoords == null ? null : copy(texCoords);
            this.colors = colors == null ? null : copy(colors);
            this.joints = joints == null ? null : copy(joints);
            this.weights = weights == null ? null : copy(weights);
            this.indices = copy(indices);
            this.materialIndex = materialIndex;
        }

        public float[] positions() { return copy(positions); }
        public float[] normals() { return normals == null ? null : copy(normals); }
        public float[] texCoords() { return texCoords == null ? null : copy(texCoords); }
        /** Four color components per vertex, or {@code null} when COLOR_0 is absent. */
        public float[] colors() { return colors == null ? null : copy(colors); }
        /** Four joint indices per vertex, or {@code null} for an unskinned primitive. */
        public int[] joints() { return joints == null ? null : copy(joints); }
        /** Four joint weights per vertex, or {@code null} for an unskinned primitive. */
        public float[] weights() { return weights == null ? null : copy(weights); }
        public int[] indices() { return copy(indices); }
        public int materialIndex() { return materialIndex; }
        public int vertexCount() { return positions.length / 3; }
        public int triangleCount() { return indices.length / 3; }
    }

    public static final class Material {
        private final String name;
        private final float[] baseColorFactor;
        private final int baseColorTextureIndex;
        private final String alphaMode;
        private final float alphaCutoff;
        private final boolean doubleSided;
        private final float metallicFactor;
        private final float roughnessFactor;

        Material(String name, float[] baseColorFactor, int baseColorTextureIndex, String alphaMode,
                 float alphaCutoff, boolean doubleSided, float metallicFactor, float roughnessFactor) {
            this.name = name;
            this.baseColorFactor = copy(baseColorFactor);
            this.baseColorTextureIndex = baseColorTextureIndex;
            this.alphaMode = alphaMode;
            this.alphaCutoff = alphaCutoff;
            this.doubleSided = doubleSided;
            this.metallicFactor = metallicFactor;
            this.roughnessFactor = roughnessFactor;
        }

        public String name() { return name; }
        public float[] baseColorFactor() { return copy(baseColorFactor); }
        public int baseColorTextureIndex() { return baseColorTextureIndex; }
        public String alphaMode() { return alphaMode; }
        public float alphaCutoff() { return alphaCutoff; }
        public boolean doubleSided() { return doubleSided; }
        public float metallicFactor() { return metallicFactor; }
        public float roughnessFactor() { return roughnessFactor; }
    }

    public static final class Image {
        private final String name;
        private final String mimeType;
        private final byte[] data;

        Image(String name, String mimeType, byte[] data) {
            this.name = name;
            this.mimeType = mimeType;
            this.data = data.clone();
        }

        public String name() { return name; }
        public String mimeType() { return mimeType; }
        public byte[] data() { return data.clone(); }
    }

    public static final class Texture {
        private final String name;
        private final int imageIndex;
        private final int samplerIndex;

        Texture(String name, int imageIndex, int samplerIndex) {
            this.name = name;
            this.imageIndex = imageIndex;
            this.samplerIndex = samplerIndex;
        }

        public String name() { return name; }
        public int imageIndex() { return imageIndex; }
        public int samplerIndex() { return samplerIndex; }
    }

    public static final class Skin {
        private final String name;
        private final List<Integer> joints;
        private final int skeletonRoot;
        private final float[] inverseBindMatrices;

        Skin(String name, List<Integer> joints, int skeletonRoot, float[] inverseBindMatrices) {
            this.name = name;
            this.joints = immutable(joints);
            this.skeletonRoot = skeletonRoot;
            this.inverseBindMatrices = copy(inverseBindMatrices);
        }

        public String name() { return name; }
        public List<Integer> joints() { return joints; }
        public int skeletonRoot() { return skeletonRoot; }
        /** One column-major 4x4 matrix per joint. */
        public float[] inverseBindMatrices() { return copy(inverseBindMatrices); }
    }

    public static final class Animation {
        private final String name;
        private final List<Channel> channels;
        private final float duration;

        Animation(String name, List<Channel> channels, float duration) {
            this.name = name;
            this.channels = immutable(channels);
            this.duration = duration;
        }

        public String name() { return name; }
        public List<Channel> channels() { return channels; }
        public float duration() { return duration; }
    }

    public static final class Channel {
        public enum Path { TRANSLATION, ROTATION, SCALE }
        public enum Interpolation { LINEAR, STEP }

        private final int nodeIndex;
        private final Path path;
        private final Interpolation interpolation;
        private final float[] times;
        private final float[] values;
        private final int components;

        Channel(int nodeIndex, Path path, Interpolation interpolation, float[] times,
                float[] values, int components) {
            this.nodeIndex = nodeIndex;
            this.path = path;
            this.interpolation = interpolation;
            this.times = copy(times);
            this.values = copy(values);
            this.components = components;
        }

        public int nodeIndex() { return nodeIndex; }
        public Path path() { return path; }
        public Interpolation interpolation() { return interpolation; }
        public float[] times() { return copy(times); }
        public float[] values() { return copy(values); }
        public int components() { return components; }
    }

    private static float[] copy(float[] value) { return value == null ? null : value.clone(); }
    private static int[] copy(int[] value) { return value == null ? null : value.clone(); }
}
