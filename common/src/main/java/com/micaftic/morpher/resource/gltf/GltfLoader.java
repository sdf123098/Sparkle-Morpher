package com.micaftic.morpher.resource.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Small dependency-free loader for the glTF 2.0 core used by the independent
 * renderer.  It supports JSON glTF and GLB files, external or data-URI
 * resources, triangle primitives, TRS node transforms, PBR base color data,
 * skinning, and LINEAR/STEP animation channels.
 *
 * <p>Unsupported optional features fail with a precise error instead of being
 * silently rendered incorrectly.  The first path intentionally excludes
 * extensions, morph targets, compressed geometry, and non-triangle topology.</p>
 */
public final class GltfLoader {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON = 0x4E4F534A;
    private static final int GLB_BIN = 0x004E4942;

    private static final int COMPONENT_BYTE = 5120;
    private static final int COMPONENT_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_SHORT = 5122;
    private static final int COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_UNSIGNED_INT = 5125;
    private static final int COMPONENT_FLOAT = 5126;

    private static final int MODE_TRIANGLES = 4;

    private GltfLoader() {}

    public static GltfModel load(Path file) throws IOException {
        return load(file, GltfExtensionRegistry.coreOnly());
    }

    public static GltfModel load(Path file, GltfExtensionRegistry extensions) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        byte[] source = Files.readAllBytes(normalized);
        ParsedContainer container = parseContainer(source, normalized.getFileName().toString());
        return new Parser(container.json(), normalized.getParent(), container.bin(), extensions).parse();
    }

    /** Loads JSON glTF/GLB bytes with external resources resolved relative to {@code baseDirectory}. */
    public static GltfModel load(byte[] source, Path baseDirectory, String name) throws IOException {
        return load(source, baseDirectory, name, GltfExtensionRegistry.coreOnly());
    }

    public static GltfModel load(byte[] source, Path baseDirectory, String name,
                                 GltfExtensionRegistry extensions) throws IOException {
        ParsedContainer container = parseContainer(source, name == null ? "model.gltf" : name);
        return new Parser(container.json(), baseDirectory == null ? null : baseDirectory.toAbsolutePath().normalize(),
                container.bin(), extensions).parse();
    }

    private static ParsedContainer parseContainer(byte[] source, String name) throws GltfParseException {
        if (source.length >= 4 && readInt(source, 0) == GLB_MAGIC) {
            if (source.length < 12) throw error("GLB header is truncated");
            int version = readInt(source, 4);
            long declaredLength = Integer.toUnsignedLong(readInt(source, 8));
            if (version != 2) throw error("Only GLB version 2 is supported, got " + version);
            if (declaredLength > source.length || declaredLength < 12) {
                throw error("GLB declared length is invalid: " + declaredLength);
            }
            String json = null;
            byte[] bin = null;
            int offset = 12;
            while (offset + 8 <= declaredLength) {
                long chunkLength = Integer.toUnsignedLong(readInt(source, offset));
                int chunkType = readInt(source, offset + 4);
                long end = offset + 8L + chunkLength;
                if (end > declaredLength || end > source.length) throw error("GLB chunk exceeds file length");
                if (chunkType == GLB_JSON) {
                    if (json != null) throw error("GLB contains more than one JSON chunk");
                    json = new String(source, offset + 8, (int) chunkLength, StandardCharsets.UTF_8)
                            .replace("\u0000", "").trim();
                } else if (chunkType == GLB_BIN && bin == null) {
                    bin = slice(source, offset + 8, (int) chunkLength);
                }
                offset = (int) end;
            }
            if (json == null) throw error("GLB has no JSON chunk");
            return new ParsedContainer(json, bin);
        }

        if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".glb")) {
            throw error("File has a .glb extension but no GLB magic header");
        }
        return new ParsedContainer(new String(source, StandardCharsets.UTF_8), null);
    }

    private static int readInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }

    private record ParsedContainer(String json, byte[] bin) {}

    public static final class GltfParseException extends IOException {
        public GltfParseException(String message) { super(message); }
    }

    private static GltfParseException error(String message) {
        return new GltfParseException(message);
    }

    private static final class Parser {
        private final JsonObject root;
        private final Path baseDirectory;
        private final byte[] glbBin;
        private final GltfExtensionRegistry extensions;
        private final List<byte[]> buffers = new ArrayList<>();
        private final List<BufferView> bufferViews = new ArrayList<>();
        private final List<Accessor> accessors = new ArrayList<>();
        private final List<JsonObject> nodeObjects = new ArrayList<>();
        private final List<Integer> parentIndices = new ArrayList<>();

        private Parser(String json, Path baseDirectory, byte[] glbBin, GltfExtensionRegistry extensions) throws GltfParseException {
            try {
                JsonElement parsed = JsonParser.parseString(json);
                if (!parsed.isJsonObject()) throw error("glTF root must be a JSON object");
                this.root = parsed.getAsJsonObject();
            } catch (GltfParseException e) {
                throw e;
            } catch (RuntimeException e) {
                throw error("Invalid glTF JSON: " + e.getMessage());
            }
            this.baseDirectory = baseDirectory;
            this.glbBin = glbBin;
            this.extensions = extensions == null ? GltfExtensionRegistry.coreOnly() : extensions;
        }

        private GltfModel parse() throws IOException {
            requireAssetVersion();
            extensions.apply(root);
            parseBuffers();
            parseBufferViews();
            parseAccessors();
            List<GltfModel.Scene> scenes = parseScenes();
            List<GltfModel.Node> nodes = parseNodes();
            List<GltfModel.Mesh> meshes = parseMeshes();
            List<GltfModel.Material> materials = parseMaterials();
            List<GltfModel.Image> images = parseImages();
            List<GltfModel.Texture> textures = parseTextures(images.size());
            List<GltfModel.Skin> skins = parseSkins();
            List<GltfModel.Animation> animations = parseAnimations();

            int defaultScene = integer(root, "scene", scenes.isEmpty() ? -1 : 0);
            checkIndex(defaultScene, scenes.size(), "scene", true);
            return new GltfModel(string(asset(), "generator", null), scenes, defaultScene, nodes, meshes,
                    materials, images, textures, skins, animations);
        }

        private JsonObject asset() throws GltfParseException {
            return object(root, "asset", true);
        }

        private void requireAssetVersion() throws GltfParseException {
            String version = string(asset(), "version", null);
            if (!"2.0".equals(version)) throw error("Only glTF asset version 2.0 is supported, got " + version);
        }

        private void parseBuffers() throws IOException {
            JsonArray values = array(root, "buffers");
            if (values == null) return;
            for (int i = 0; i < values.size(); i++) {
                JsonObject buffer = values.get(i).getAsJsonObject();
                int byteLength = positive(buffer, "byteLength", "buffer " + i);
                String uri = string(buffer, "uri", null);
                byte[] bytes;
                if (uri == null) {
                    if (glbBin == null) throw error("Buffer " + i + " has no URI and no GLB BIN chunk");
                    bytes = glbBin;
                } else {
                    bytes = readUri(uri, "buffer " + i);
                }
                if (bytes.length < byteLength) {
                    throw error("Buffer " + i + " is shorter than byteLength (" + bytes.length + " < " + byteLength + ")");
                }
                buffers.add(bytes);
            }
        }

        private void parseBufferViews() throws GltfParseException {
            JsonArray values = array(root, "bufferViews");
            if (values == null) return;
            for (int i = 0; i < values.size(); i++) {
                JsonObject view = values.get(i).getAsJsonObject();
                int buffer = requiredInt(view, "buffer", "bufferView " + i);
                checkIndex(buffer, buffers.size(), "bufferView.buffer", false);
                int offset = integer(view, "byteOffset", 0);
                int length = positive(view, "byteLength", "bufferView " + i);
                int stride = integer(view, "byteStride", 0);
                if (stride != 0 && (stride < 4 || stride > 252)) throw error("Invalid byteStride in bufferView " + i);
                checkRange(buffers.get(buffer).length, offset, length, "bufferView " + i);
                bufferViews.add(new BufferView(buffer, offset, length, stride));
            }
        }

        private void parseAccessors() throws GltfParseException {
            JsonArray values = array(root, "accessors");
            if (values == null) return;
            for (int i = 0; i < values.size(); i++) {
                JsonObject accessor = values.get(i).getAsJsonObject();
                if (accessor.has("sparse")) throw error("Sparse accessors are not supported (accessor " + i + ")");
                int view = integer(accessor, "bufferView", -1);
                if (view < 0) throw error("Sparse or bufferView-less accessors are not supported (accessor " + i + ")");
                checkIndex(view, bufferViews.size(), "accessor.bufferView", false);
                int componentType = requiredInt(accessor, "componentType", "accessor " + i);
                componentSize(componentType); // validates the type
                int count = positive(accessor, "count", "accessor " + i);
                String type = string(accessor, "type", null);
                int components = componentCount(type);
                int offset = integer(accessor, "byteOffset", 0);
                if (offset < 0) throw error("Negative accessor byteOffset at accessor " + i);
                accessors.add(new Accessor(view, offset, componentType, type, components, count,
                        bool(accessor, "normalized", false)));
            }
        }

        private List<GltfModel.Scene> parseScenes() throws GltfParseException {
            JsonArray values = array(root, "scenes");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Scene> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject scene = values.get(i).getAsJsonObject();
                List<Integer> roots = ints(array(scene, "nodes"));
                for (int node : roots) checkIndex(node, nodeObjectsCount(), "scene.nodes", false);
                result.add(new GltfModel.Scene(string(scene, "name", null), roots));
            }
            return result;
        }

        private List<GltfModel.Node> parseNodes() throws GltfParseException {
            JsonArray values = array(root, "nodes");
            if (values == null) return Collections.emptyList();
            nodeObjects.clear();
            parentIndices.clear();
            for (int i = 0; i < values.size(); i++) {
                nodeObjects.add(values.get(i).getAsJsonObject());
                parentIndices.add(-1);
            }
            for (int i = 0; i < nodeObjects.size(); i++) {
                for (int child : ints(array(nodeObjects.get(i), "children"))) {
                    checkIndex(child, nodeObjects.size(), "node.children", false);
                    if (parentIndices.get(child) >= 0 && parentIndices.get(child) != i) {
                        throw error("Node " + child + " has more than one parent");
                    }
                    parentIndices.set(child, i);
                }
            }
            List<GltfModel.Node> result = new ArrayList<>();
            for (int i = 0; i < nodeObjects.size(); i++) {
                JsonObject node = nodeObjects.get(i);
                int mesh = integer(node, "mesh", -1);
                int skin = integer(node, "skin", -1);
                if (mesh >= 0) checkIndex(mesh, arraySize(root, "meshes"), "node.mesh", false);
                if (skin >= 0) checkIndex(skin, arraySize(root, "skins"), "node.skin", false);
                float[] matrix = floats(array(node, "matrix"), 16, "node.matrix", false);
                float[] translation = floats(array(node, "translation"), 3, "node.translation", false);
                float[] rotation = floats(array(node, "rotation"), 4, "node.rotation", false);
                float[] scale = floats(array(node, "scale"), 3, "node.scale", false);
                if (matrix != null && (translation != null || rotation != null || scale != null)) {
                    throw error("Node " + i + " must not define both matrix and TRS");
                }
                if (translation == null) translation = new float[]{0, 0, 0};
                if (rotation == null) rotation = new float[]{0, 0, 0, 1};
                if (scale == null) scale = new float[]{1, 1, 1};
                result.add(new GltfModel.Node(string(node, "name", null), parentIndices.get(i),
                        ints(array(node, "children")), mesh, skin, translation, rotation, scale, matrix));
            }
            return result;
        }

        private List<GltfModel.Mesh> parseMeshes() throws IOException {
            JsonArray values = array(root, "meshes");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Mesh> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject mesh = values.get(i).getAsJsonObject();
                JsonArray primitives = array(mesh, "primitives");
                if (primitives == null || primitives.size() == 0) throw error("Mesh " + i + " has no primitives");
                List<GltfModel.Primitive> parsed = new ArrayList<>();
                for (int p = 0; p < primitives.size(); p++) parsed.add(parsePrimitive(primitives.get(p).getAsJsonObject(), i, p));
                if (mesh.has("weights") || mesh.has("targets")) throw error("Morph targets are not supported (mesh " + i + ")");
                result.add(new GltfModel.Mesh(string(mesh, "name", null), parsed));
            }
            return result;
        }

        private GltfModel.Primitive parsePrimitive(JsonObject primitive, int meshIndex, int primitiveIndex) throws GltfParseException {
            int mode = integer(primitive, "mode", MODE_TRIANGLES);
            if (mode != MODE_TRIANGLES) throw error("Only TRIANGLES primitives are supported (mesh " + meshIndex + ", primitive " + primitiveIndex + ")");
            if (primitive.has("targets")) throw error("Morph targets are not supported (mesh " + meshIndex + ")");
            JsonObject attributes = object(primitive, "attributes", true);
            int positionAccessor = requiredInt(attributes, "POSITION", "primitive POSITION");
            float[] positions = readFloat(positionAccessor, "VEC3", false);
            int vertexCount = positions.length / 3;
            float[] normals = optionalFloat(attributes, "NORMAL", "VEC3", vertexCount, 3);
            float[] texCoords = optionalFloat(attributes, "TEXCOORD_0", "VEC2", vertexCount, 2);
            float[] colors = optionalColors(attributes, "COLOR_0", vertexCount);
            int[] joints = optionalInt(attributes, "JOINTS_0", "VEC4", vertexCount, 4);
            float[] weights = optionalFloat(attributes, "WEIGHTS_0", "VEC4", vertexCount, 4);
            int indexAccessor = integer(primitive, "indices", -1);
            int[] indices;
            if (indexAccessor < 0) {
                indices = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) indices[i] = i;
            } else {
                indices = readIndices(indexAccessor);
            }
            if (indices.length == 0 || indices.length % 3 != 0) throw error("Triangle index count is not divisible by 3");
            for (int index : indices) if (index < 0 || index >= vertexCount) throw error("Primitive index out of range");
            int material = integer(primitive, "material", -1);
            checkIndex(material, arraySize(root, "materials"), "primitive.material", true);
            if (joints != null && weights == null || joints == null && weights != null) {
                throw error("JOINTS_0 and WEIGHTS_0 must be provided together");
            }
            return new GltfModel.Primitive(positions, normals, texCoords, colors, joints, weights, indices, material);
        }

        private List<GltfModel.Material> parseMaterials() throws GltfParseException {
            JsonArray values = array(root, "materials");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Material> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject material = values.get(i).getAsJsonObject();
                JsonObject pbr = object(material, "pbrMetallicRoughness", false);
                if (pbr == null) pbr = new JsonObject();
                float[] factor = floats(array(pbr, "baseColorFactor"), 4, "baseColorFactor", false);
                if (factor == null) factor = new float[]{1, 1, 1, 1};
                int texture = -1;
                JsonObject textureInfo = object(pbr, "baseColorTexture", false);
                if (textureInfo != null) texture = integer(textureInfo, "index", -1);
                checkIndex(texture, arraySize(root, "textures"), "baseColorTexture.index", true);
                String alphaMode = string(material, "alphaMode", "OPAQUE");
                if (!(alphaMode.equals("OPAQUE") || alphaMode.equals("MASK") || alphaMode.equals("BLEND"))) {
                    throw error("Unknown material alphaMode: " + alphaMode);
                }
                result.add(new GltfModel.Material(string(material, "name", null), factor, texture, alphaMode,
                        number(material, "alphaCutoff", 0.5f), bool(material, "doubleSided", false),
                        number(pbr, "metallicFactor", 1), number(pbr, "roughnessFactor", 1)));
            }
            return result;
        }

        private List<GltfModel.Image> parseImages() throws IOException {
            JsonArray values = array(root, "images");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Image> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject image = values.get(i).getAsJsonObject();
                String uri = string(image, "uri", null);
                String mimeType = string(image, "mimeType", null);
                byte[] bytes;
                if (uri != null) {
                    if (uri.startsWith("data:")) {
                        DataUri data = decodeDataUri(uri);
                        bytes = data.bytes();
                        if (mimeType == null) mimeType = data.mimeType();
                    } else {
                        bytes = readUri(uri, "image " + i);
                    }
                } else {
                    int view = requiredInt(image, "bufferView", "image " + i);
                    checkIndex(view, bufferViews.size(), "image.bufferView", false);
                    BufferView bufferView = bufferViews.get(view);
                    bytes = slice(buffers.get(bufferView.buffer()), bufferView.offset(), bufferView.length());
                }
                result.add(new GltfModel.Image(string(image, "name", null), mimeType, bytes));
            }
            return result;
        }

        private List<GltfModel.Texture> parseTextures(int imageCount) throws GltfParseException {
            JsonArray values = array(root, "textures");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Texture> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject texture = values.get(i).getAsJsonObject();
                int source = requiredInt(texture, "source", "texture " + i);
                checkIndex(source, imageCount, "texture.source", false);
                int sampler = integer(texture, "sampler", -1);
                checkIndex(sampler, arraySize(root, "samplers"), "texture.sampler", true);
                result.add(new GltfModel.Texture(string(texture, "name", null), source, sampler));
            }
            return result;
        }

        private List<GltfModel.Skin> parseSkins() throws GltfParseException {
            JsonArray values = array(root, "skins");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Skin> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject skin = values.get(i).getAsJsonObject();
                List<Integer> joints = ints(array(skin, "joints"));
                if (joints.isEmpty()) throw error("Skin " + i + " has no joints");
                for (int joint : joints) checkIndex(joint, nodeObjectsCount(), "skin.joints", false);
                int skeleton = integer(skin, "skeleton", -1);
                checkIndex(skeleton, nodeObjectsCount(), "skin.skeleton", true);
                int accessor = integer(skin, "inverseBindMatrices", -1);
                float[] matrices;
                if (accessor < 0) {
                    matrices = new float[joints.size() * 16];
                    for (int j = 0; j < joints.size(); j++) {
                        matrices[j * 16] = 1;
                        matrices[j * 16 + 5] = 1;
                        matrices[j * 16 + 10] = 1;
                        matrices[j * 16 + 15] = 1;
                    }
                } else {
                    matrices = readFloat(accessor, "MAT4", false);
                    if (matrices.length != joints.size() * 16) throw error("Skin inverseBindMatrices count does not match joints");
                }
                result.add(new GltfModel.Skin(string(skin, "name", null), joints, skeleton, matrices));
            }
            return result;
        }

        private List<GltfModel.Animation> parseAnimations() throws GltfParseException {
            JsonArray values = array(root, "animations");
            if (values == null) return Collections.emptyList();
            List<GltfModel.Animation> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                JsonObject animation = values.get(i).getAsJsonObject();
                JsonArray samplers = array(animation, "samplers");
                JsonArray channels = array(animation, "channels");
                if (samplers == null || channels == null) throw error("Animation " + i + " is missing samplers or channels");
                List<GltfModel.Channel> parsedChannels = new ArrayList<>();
                float duration = 0;
                for (int c = 0; c < channels.size(); c++) {
                    JsonObject channel = channels.get(c).getAsJsonObject();
                    int samplerIndex = requiredInt(channel, "sampler", "animation channel");
                    checkIndex(samplerIndex, samplers.size(), "animation.channel.sampler", false);
                    JsonObject target = object(channel, "target", true);
                    int node = requiredInt(target, "node", "animation target");
                    checkIndex(node, nodeObjectsCount(), "animation.target.node", false);
                    GltfModel.Channel.Path path;
                    try {
                        path = switch (string(target, "path", "")) {
                            case "translation" -> GltfModel.Channel.Path.TRANSLATION;
                            case "rotation" -> GltfModel.Channel.Path.ROTATION;
                            case "scale" -> GltfModel.Channel.Path.SCALE;
                            default -> throw error("Unsupported animation path in animation " + i);
                        };
                    } catch (GltfParseException e) {
                        throw e;
                    }
                    JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
                    String interpolationName = string(sampler, "interpolation", "LINEAR");
                    GltfModel.Channel.Interpolation interpolation;
                    if (interpolationName.equals("LINEAR")) interpolation = GltfModel.Channel.Interpolation.LINEAR;
                    else if (interpolationName.equals("STEP")) interpolation = GltfModel.Channel.Interpolation.STEP;
                    else throw error("Only LINEAR and STEP animation interpolation are supported");
                    int input = requiredInt(sampler, "input", "animation sampler");
                    int output = requiredInt(sampler, "output", "animation sampler");
                    float[] times = readFloat(input, "SCALAR", false);
                    String outputType = path == GltfModel.Channel.Path.ROTATION ? "VEC4" : "VEC3";
                    float[] outputValues = readFloat(output, outputType, false);
                    int components = outputType.equals("VEC4") ? 4 : 3;
                    if (times.length == 0 || outputValues.length != times.length * components) {
                        throw error("Animation input/output counts do not match");
                    }
                    if (outputValues.length != times.length * components) throw error("Animation output count does not match input count");
                    for (float time : times) duration = Math.max(duration, time);
                    parsedChannels.add(new GltfModel.Channel(node, path, interpolation, times, outputValues, components));
                }
                result.add(new GltfModel.Animation(string(animation, "name", "animation_" + i), parsedChannels, duration));
            }
            return result;
        }

        private float[] optionalFloat(JsonObject attributes, String key, String type, int expectedVertices, int components) throws GltfParseException {
            int index = integer(attributes, key, -1);
            if (index < 0) return null;
            float[] result = readFloat(index, type, false);
            if (result.length != expectedVertices * components) throw error(key + " count does not match POSITION");
            return result;
        }

        private int[] optionalInt(JsonObject attributes, String key, String type, int expectedVertices, int components) throws GltfParseException {
            int index = integer(attributes, key, -1);
            if (index < 0) return null;
            int[] result = readInt(index, type);
            if (result.length != expectedVertices * components) throw error(key + " count does not match POSITION");
            return result;
        }

        private float[] readFloat(int accessorIndex, String expectedType, boolean requireInteger) throws GltfParseException {
            Accessor accessor = accessor(accessorIndex);
            if (!accessor.type().equals(expectedType)) throw error("Accessor " + accessorIndex + " must be " + expectedType + ", got " + accessor.type());
            if (requireInteger && accessor.componentType() == COMPONENT_FLOAT) throw error("Accessor " + accessorIndex + " must use integer components");
            return read(accessor, true);
        }

        private int[] readInt(int accessorIndex, String expectedType) throws GltfParseException {
            Accessor accessor = accessor(accessorIndex);
            if (!accessor.type().equals(expectedType)) throw error("Accessor " + accessorIndex + " must be " + expectedType + ", got " + accessor.type());
            if (accessor.componentType() == COMPONENT_FLOAT) throw error("Accessor " + accessorIndex + " must use integer components");
            float[] values = read(accessor, false);
            int[] result = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                if (values[i] > Integer.MAX_VALUE) throw error("Integer accessor value exceeds Java int range");
                result[i] = (int) values[i];
            }
            return result;
        }

        private int[] readIndices(int accessorIndex) throws GltfParseException {
            Accessor accessor = accessor(accessorIndex);
            if (!accessor.type().equals("SCALAR")) throw error("Index accessor must be SCALAR");
            if (!(accessor.componentType() == COMPONENT_UNSIGNED_BYTE || accessor.componentType() == COMPONENT_UNSIGNED_SHORT
                    || accessor.componentType() == COMPONENT_UNSIGNED_INT)) throw error("Index accessor must be unsigned integer");
            float[] values = read(accessor, false);
            int[] result = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                if (values[i] > Integer.MAX_VALUE) throw error("Index exceeds Java int range");
                result[i] = (int) values[i];
            }
            return result;
        }

        private float[] optionalColors(JsonObject attributes, String key, int expectedVertices) throws GltfParseException {
            int index = integer(attributes, key, -1);
            if (index < 0) return null;
            Accessor accessor = accessor(index);
            if (!(accessor.type().equals("VEC3") || accessor.type().equals("VEC4"))) {
                throw error(key + " must use VEC3 or VEC4");
            }
            float[] values = read(accessor, true);
            if (values.length != expectedVertices * accessor.components()) {
                throw error(key + " count does not match POSITION");
            }
            if (accessor.components() == 4) return values;
            float[] colors = new float[expectedVertices * 4];
            for (int vertex = 0; vertex < expectedVertices; vertex++) {
                colors[vertex * 4] = values[vertex * 3];
                colors[vertex * 4 + 1] = values[vertex * 3 + 1];
                colors[vertex * 4 + 2] = values[vertex * 3 + 2];
                colors[vertex * 4 + 3] = 1;
            }
            return colors;
        }

        private float[] read(Accessor accessor, boolean asFloat) throws GltfParseException {
            int elementSize = accessor.components() * componentSize(accessor.componentType());
            BufferView view = accessor.viewIndex() < 0 ? null : bufferViews.get(accessor.viewIndex());
            byte[] data;
            int base;
            int stride;
            if (view == null) {
                data = new byte[accessor.count() * elementSize];
                base = 0;
                stride = elementSize;
            } else {
                data = buffers.get(view.buffer());
                base = view.offset() + accessor.byteOffset();
                stride = view.stride() == 0 ? elementSize : view.stride();
                long end = (long) base + (long) Math.max(0, accessor.count() - 1) * stride + elementSize;
                if (base < view.offset() || end > (long) view.offset() + view.length()) {
                    throw error("Accessor exceeds its bufferView");
                }
            }
            float[] result = new float[accessor.count() * accessor.components()];
            for (int i = 0; i < accessor.count(); i++) {
                for (int c = 0; c < accessor.components(); c++) {
                    int offset = base + i * stride + c * componentSize(accessor.componentType());
                    result[i * accessor.components() + c] = componentValue(data, offset, accessor.componentType(), accessor.normalized());
                }
            }
            return result;
        }

        private byte[] readUri(String uri, String context) throws IOException {
            if (uri.startsWith("data:")) return decodeDataUri(uri).bytes();
            if (baseDirectory == null) throw error("External " + context + " cannot be resolved without a base directory: " + uri);
            Path resolved = baseDirectory.resolve(percentDecode(uri)).normalize();
            if (!Files.isRegularFile(resolved)) throw error("External " + context + " does not exist: " + resolved);
            return Files.readAllBytes(resolved);
        }

        private Accessor accessor(int index) throws GltfParseException {
            checkIndex(index, accessors.size(), "accessor", false);
            return accessors.get(index);
        }

        private int nodeObjectsCount() {
            if (!nodeObjects.isEmpty()) return nodeObjects.size();
            JsonElement nodes = root.get("nodes");
            return nodes != null && nodes.isJsonArray() ? nodes.getAsJsonArray().size() : 0;
        }

        private static float componentValue(byte[] data, int offset, int componentType, boolean normalized) {
            ByteBuffer buffer = ByteBuffer.wrap(data, offset, componentSize(componentType)).order(ByteOrder.LITTLE_ENDIAN);
            return switch (componentType) {
                case COMPONENT_BYTE -> normalized ? Math.max(-1, buffer.get() / 127f) : buffer.get();
                case COMPONENT_UNSIGNED_BYTE -> normalized ? (buffer.get() & 0xff) / 255f : buffer.get() & 0xff;
                case COMPONENT_SHORT -> normalized ? Math.max(-1, buffer.getShort() / 32767f) : buffer.getShort();
                case COMPONENT_UNSIGNED_SHORT -> normalized ? (buffer.getShort() & 0xffff) / 65535f : buffer.getShort() & 0xffff;
                case COMPONENT_UNSIGNED_INT -> (float) (Integer.toUnsignedLong(buffer.getInt()));
                case COMPONENT_FLOAT -> buffer.getFloat();
                default -> throw new IllegalArgumentException("Unsupported component type " + componentType);
            };
        }
    }

    private record BufferView(int buffer, int offset, int length, int stride) {}
    private record Accessor(int viewIndex, int byteOffset, int componentType, String type,
                            int components, int count, boolean normalized) {}
    private record DataUri(String mimeType, byte[] bytes) {}

    private static DataUri decodeDataUri(String uri) throws GltfParseException {
        int comma = uri.indexOf(',');
        if (!uri.startsWith("data:") || comma < 0) throw error("Malformed data URI");
        String header = uri.substring(5, comma);
        String payload = uri.substring(comma + 1);
        String mime = null;
        int semicolon = header.indexOf(';');
        if (semicolon < 0) mime = header.isEmpty() ? null : header;
        else mime = header.substring(0, semicolon).isEmpty() ? null : header.substring(0, semicolon);
        try {
            byte[] bytes = header.endsWith(";base64") ? Base64.getDecoder().decode(payload) : percentDecodeBytes(payload);
            return new DataUri(mime, bytes);
        } catch (IllegalArgumentException e) {
            throw error("Malformed data URI payload: " + e.getMessage());
        }
    }

    private static byte[] percentDecodeBytes(String value) {
        byte[] result = new byte[value.length()];
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                result[count++] = (byte) Integer.parseInt(value.substring(i + 1, i + 3), 16);
                i += 2;
            } else {
                result[count++] = (byte) c;
            }
        }
        return java.util.Arrays.copyOf(result, count);
    }

    private static String percentDecode(String value) {
        return new String(percentDecodeBytes(value), StandardCharsets.UTF_8);
    }

    private static int componentCount(String type) throws GltfParseException {
        return switch (type == null ? "" : type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw error("Unsupported accessor type: " + type);
        };
    }

    private static int componentSize(int type) {
        return switch (type) {
            case COMPONENT_BYTE, COMPONENT_UNSIGNED_BYTE -> 1;
            case COMPONENT_SHORT, COMPONENT_UNSIGNED_SHORT -> 2;
            case COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4;
            default -> throw new IllegalArgumentException("Unsupported accessor componentType: " + type);
        };
    }

    private static void checkRange(int size, int offset, int length, String context) throws GltfParseException {
        if (offset < 0 || length < 0 || (long) offset + length > size) throw error("Invalid byte range for " + context);
    }

    private static void checkIndex(int index, int size, String context, boolean optional) throws GltfParseException {
        if (optional && index < 0) return;
        if (index < 0 || index >= size) throw error("Invalid " + context + " index " + index + " (size " + size + ")");
    }

    private static int positive(JsonObject object, String key, String context) throws GltfParseException {
        int value = requiredInt(object, key, context);
        if (value <= 0) throw error(context + " requires a positive " + key);
        return value;
    }

    private static int requiredInt(JsonObject object, String key, String context) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) throw error("Missing " + key + " in " + context);
        try { return object.get(key).getAsInt(); }
        catch (RuntimeException e) { throw error("Invalid integer " + key + " in " + context); }
    }

    private static int integer(JsonObject object, String key, int fallback) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsInt(); }
        catch (RuntimeException e) { throw error("Invalid integer property " + key); }
    }

    private static float number(JsonObject object, String key, float fallback) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsFloat(); }
        catch (RuntimeException e) { throw error("Invalid numeric property " + key); }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsBoolean(); }
        catch (RuntimeException e) { throw error("Invalid boolean property " + key); }
    }

    private static String string(JsonObject object, String key, String fallback) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsString(); }
        catch (RuntimeException e) { throw error("Invalid string property " + key); }
    }

    private static JsonObject object(JsonObject object, String key, boolean required) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            if (required) throw error("Missing object " + key);
            return null;
        }
        if (!object.get(key).isJsonObject()) throw error("Property " + key + " must be an object");
        return object.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject object, String key) throws GltfParseException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        if (!object.get(key).isJsonArray()) throw error("Property " + key + " must be an array");
        return object.getAsJsonArray(key);
    }

    private static int arraySize(JsonObject object, String key) throws GltfParseException {
        JsonArray array = array(object, key);
        return array == null ? 0 : array.size();
    }

    private static List<Integer> ints(JsonArray array) throws GltfParseException {
        if (array == null) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (JsonElement element : array) {
            try { result.add(element.getAsInt()); }
            catch (RuntimeException e) { throw error("Expected an integer array"); }
        }
        return result;
    }

    private static float[] floats(JsonArray array, int expected, String context, boolean required) throws GltfParseException {
        if (array == null) {
            if (required) throw error("Missing array " + context);
            return null;
        }
        if (array.size() != expected) throw error(context + " must contain " + expected + " values");
        float[] result = new float[expected];
        for (int i = 0; i < expected; i++) {
            try { result[i] = array.get(i).getAsFloat(); }
            catch (RuntimeException e) { throw error("Invalid number in " + context); }
        }
        return result;
    }

}
