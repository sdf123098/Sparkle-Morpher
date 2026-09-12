package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.charset.StandardCharsets;

import static com.micaftic.morpher.util.DigestUtil.sha256Hex;

/**
 * 1.2.7 §24.2：从 {@code YSMFolderDeserializer} 外提的几何解析职责（行为等价，纯搬运）。
 *
 * 负责 Bedrock {@code minecraft:geometry} JSON → RawGeometry 的解析（含面 UV 归一与
 * cube 烘焙），以及 {@code geometry.*} identifier 选择。不读任何 zip/目录资源。
 */
public final class YsmGeometryParsing {

    private YsmGeometryParsing() {
    }

    /**
     * legacy 元数据回填回调。
     *
     * <p>拆分前 {{@code parseGeometry}} 在解析到 {{@code ysm_extra_info}} 时直接写调用方的
     * model 状态（{@code parseLegacyMetadata}）。为保持行为完全一致：
     * <ul>
     *   <li>实例路径（{{@code deserialize}}）传入真实回调，副作用照旧写入 model；</li>
     *   <li>静态探测路径（{{@code parseBedrockGeometry}}）传入 {{@code null}}，等同于拆分前
     *       在一次性 probe 实例上写入后被丢弃的行为。</li>
     * </ul>
     */
    @FunctionalInterface
    public interface LegacyMetadataSink {
        void accept(JsonObject infoObj, boolean overwrite);
    }

    /**
     * 解析 Bedrock {@code minecraft:geometry} JSON。
     *
     * @param identifier 非空时按 {@code description.identifier} 选择几何（支持
     *                   {@code geometry.xxx} 与 {@code xxx} 两种写法，大小写不敏感）；
     *                   为空或找不到时回退到第一个（与旧行为一致，YSM 包不受影响）。
     */
    public static RawYsmModel.RawGeometry parseGeometry(byte[] data, int modelType, String identifier) {
        return parseGeometry(data, modelType, identifier, null);
    }

    public static RawYsmModel.RawGeometry parseGeometry(byte[] data, int modelType, String identifier, LegacyMetadataSink legacyMetadataSink) {
        String json = new String(data, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray geometries = root.has("minecraft:geometry") ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geometries == null || geometries.isEmpty()) return new RawYsmModel.RawGeometry();

        JsonObject geoObj = selectGeometry(geometries, identifier);
        RawYsmModel.RawGeometry geo = new RawYsmModel.RawGeometry();
        geo.sha256 = sha256Hex(data);

        geo.modelType = modelType;
        geo.unkFloat1 = 0.7f;
        geo.unkFloat2 = 0.7f;

        if (geoObj.has("description")) {
            JsonObject desc = geoObj.getAsJsonObject("description");
            geo.identifier = YsmJsonSupport.getStr(desc, "identifier", "");
            geo.textureWidth = (float) YsmJsonSupport.getDouble(desc, "texture_width", 64.0);
            geo.textureHeight = (float) YsmJsonSupport.getDouble(desc, "texture_height", 64.0);
            geo.visibleBoundsWidth = (float) YsmJsonSupport.getDouble(desc, "visible_bounds_width", 0);
            geo.visibleBoundsHeight = (float) YsmJsonSupport.getDouble(desc, "visible_bounds_height", 0);
            if (desc.has("visible_bounds_offset") && desc.get("visible_bounds_offset").isJsonArray()) {
                JsonArray offsetArr = desc.getAsJsonArray("visible_bounds_offset");
                geo.visibleBoundsOffset = new float[offsetArr.size()];
                for (int i = 0; i < offsetArr.size(); i++) geo.visibleBoundsOffset[i] = offsetArr.get(i).getAsFloat();
            } else {
                geo.visibleBoundsOffset = new float[0];
            }

            if (modelType == 1 && desc.has("ysm_extra_info")) { // legacy
                if (legacyMetadataSink != null) legacyMetadataSink.accept(desc.getAsJsonObject("ysm_extra_info"), false);
            }
        }

        if (geoObj.has("bones") && geoObj.get("bones").isJsonArray()) {
            for (JsonElement boneElem : geoObj.getAsJsonArray("bones")) {
                if (!boneElem.isJsonObject()) continue;
                JsonObject bObj = boneElem.getAsJsonObject();
                RawYsmModel.RawBone bone = new RawYsmModel.RawBone();
                bone.name = YsmJsonSupport.getStr(bObj, "name", "");
                bone.parentName = YsmJsonSupport.getStr(bObj, "parent", "");

                if (bObj.has("pivot")) {
                    JsonArray pivot = bObj.getAsJsonArray("pivot");
                    bone.pivot = new float[]{-pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat()};
                }
                if (bObj.has("rotation")) {
                    JsonArray rot = bObj.getAsJsonArray("rotation");
                    bone.rotation = new float[]{(float) -Math.toRadians(rot.get(0).getAsFloat()), (float) -Math.toRadians(rot.get(1).getAsFloat()), (float) Math.toRadians(rot.get(2).getAsFloat())};
                }

                float boneInflate = (float) YsmJsonSupport.getDouble(bObj, "inflate", 0.0);
                boolean boneMirror = YsmJsonSupport.getBool(bObj, "mirror", false);

                if (bObj.has("cubes") && bObj.get("cubes").isJsonArray()) {
                    for (JsonElement cElem : bObj.getAsJsonArray("cubes")) {
                        if (!cElem.isJsonObject()) continue;
                        JsonObject cObj = cElem.getAsJsonObject();
                        RawYsmModel.RawCube cube = new RawYsmModel.RawCube();

                        float inflate = cObj.has("inflate") ? cObj.get("inflate").getAsFloat() : boneInflate;
                        boolean mirror = cObj.has("mirror") ? cObj.get("mirror").getAsBoolean() : boneMirror;

                        float[] origin = YsmJsonSupport.getFloatArray(cObj, "origin", 3);
                        float[] size = YsmJsonSupport.getFloatArray(cObj, "size", 3);

                        float cx = -origin[0] - size[0] - inflate;
                        float cy = origin[1] - inflate;
                        float cz = origin[2] - inflate;
                        float cw = size[0] + inflate * 2;
                        float ch = size[1] + inflate * 2;
                        float cd = size[2] + inflate * 2;

                        Matrix4f cubeBakeMat = new Matrix4f();
                        if (cObj.has("rotation") || cObj.has("pivot")) {
                            float[] cpvt = YsmJsonSupport.getFloatArray(cObj, "pivot", 3);
                            float[] crot = YsmJsonSupport.getFloatArray(cObj, "rotation", 3);
                            cubeBakeMat.translate(-cpvt[0] / 16f, cpvt[1] / 16f, cpvt[2] / 16f);
                            cubeBakeMat.rotateZ((float) Math.toRadians(crot[2]));
                            cubeBakeMat.rotateY((float) -Math.toRadians(crot[1]));
                            cubeBakeMat.rotateX((float) -Math.toRadians(crot[0]));
                            cubeBakeMat.translate(cpvt[0] / 16f, -cpvt[1] / 16f, -cpvt[2] / 16f);
                        }
                        Matrix3f cubeNormalMat = new Matrix3f();
                        cubeBakeMat.normal(cubeNormalMat);

                        if (cObj.has("uv")) {
                            JsonElement uvElem = cObj.get("uv");
                            if (uvElem.isJsonObject()) {
                                JsonObject uvObj = uvElem.getAsJsonObject();
                                bakeFaceToRaw(cube, uvObj, "north", "north", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "south", "south", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "east", mirror ? "west" : "east", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "west", mirror ? "east" : "west", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "up", "up", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "down", "down", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
                            } else if (uvElem.isJsonArray()) {
                                JsonArray uvArr = uvElem.getAsJsonArray();
                                float uvX = uvArr.get(0).getAsFloat();
                                float uvY = uvArr.get(1).getAsFloat();
                                float dx = (float) Math.floor(size[0]);
                                float dy = (float) Math.floor(size[1]);
                                float dz = (float) Math.floor(size[2]);

                                JsonObject fakeUvObj = new JsonObject();
                                fakeUvObj.add("north", createFaceUVNode(uvX + dz, uvY + dz, dx, dy));
                                fakeUvObj.add("south", createFaceUVNode(uvX + dz + dx + dz, uvY + dz, dx, dy));
                                fakeUvObj.add("east", createFaceUVNode(uvX, uvY + dz, dz, dy));
                                fakeUvObj.add("west", createFaceUVNode(uvX + dz + dx, uvY + dz, dz, dy));
                                fakeUvObj.add("up", createFaceUVNode(uvX + dz, uvY, dx, dz));
                                fakeUvObj.add("down", createFaceUVNode(uvX + dz + dx, uvY + dz, dx, -dz));

                                bakeFaceToRaw(cube, fakeUvObj, "north", "north", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "south", "south", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "east", mirror ? "west" : "east", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "west", mirror ? "east" : "west", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "up", "up", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "down", "down", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
                            }
                        }
                        bone.cubes.add(cube);
                    }
                }
                geo.bones.add(bone);
            }
        }
        return geo;
    }

    public static RawYsmModel.RawGeometry parseGeometry(byte[] data, int modelType) {
        return parseGeometry(data, modelType, null);
    }

    /** 按 identifier 选择几何；identifier 为空或未命中时回退到第一个。 */
    private static JsonObject selectGeometry(JsonArray geometries, String identifier) {
        if (identifier != null && !identifier.isEmpty()) {
            String want = identifier.trim();
            String wantWithPrefix = want.startsWith("geometry.") ? want : "geometry." + want;
            for (JsonElement element : geometries) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                if (!candidate.has("description") || !candidate.get("description").isJsonObject()) continue;
                String id = YsmJsonSupport.getJsonString(candidate.getAsJsonObject("description").get("identifier"));
                if (id == null || id.isEmpty()) continue;
                if (id.equalsIgnoreCase(want) || id.equalsIgnoreCase(wantWithPrefix)
                        || id.endsWith("." + want)) {
                    return candidate;
                }
            }
        }
        return geometries.get(0).getAsJsonObject();
    }

    /**
     * 静态入口：解析裸 Bedrock geo JSON（供 Bedrock 直读导入用，不依赖任何 zip/目录资源）。
     *
     * @param identifier 按 {@code description.identifier} 选择几何，可为 null（取第一个）。
     */
    public static RawYsmModel.RawGeometry parseBedrockGeometry(byte[] data, String identifier) {
        if (data == null || data.length == 0) {
            return new RawYsmModel.RawGeometry();
        }
        try {
            return parseGeometry(data, 1, identifier);
        } catch (Exception e) {
            System.err.println("[SM] Failed to parse Bedrock geometry: " + e);
            return new RawYsmModel.RawGeometry();
        }
    }

    private static void bakeFaceToRaw(RawYsmModel.RawCube cube, JsonObject uvObj, String faceType, String uvFaceName, boolean mirror, float x, float y, float z, float w, float h, float d, float tw, float th, Vector3f rawNormal, Matrix4f cubeBakeMat, Matrix3f cubeNormalMat) {
        if (!uvObj.has(uvFaceName)) return;
        JsonObject faceData = uvObj.getAsJsonObject(uvFaceName);
        float[] uv = YsmJsonSupport.getFloatArray(faceData, "uv", 2);
        float[] uvSize = YsmJsonSupport.getFloatArray(faceData, "uv_size", 2);

        float u0 = uv[0] / tw;
        float v0 = uv[1] / th;
        float u1 = (uv[0] + uvSize[0]) / tw;
        float v1 = (uv[1] + uvSize[1]) / th;

        if (!mirror) {
            float temp = u0; u0 = u1; u1 = temp;
        }

        RawYsmModel.RawFace face = new RawYsmModel.RawFace();
        Vector3f bakedNormal = new Vector3f(rawNormal).mul(cubeNormalMat).normalize();
        face.normal = new float[]{bakedNormal.x, bakedNormal.y, bakedNormal.z};

        float x1 = x / 16f, x2 = (x + w) / 16f;
        float y1 = y / 16f, y2 = (y + h) / 16f;
        float z1 = z / 16f, z2 = (z + d) / 16f;

        Vector3f p1 = new Vector3f(x1, y1, z1);
        Vector3f p2 = new Vector3f(x1, y1, z2);
        Vector3f p3 = new Vector3f(x1, y2, z1);
        Vector3f p4 = new Vector3f(x1, y2, z2);
        Vector3f p5 = new Vector3f(x2, y1, z1);
        Vector3f p6 = new Vector3f(x2, y1, z2);
        Vector3f p7 = new Vector3f(x2, y2, z1);
        Vector3f p8 = new Vector3f(x2, y2, z2);

        Vector3f[] positions = switch (faceType) {
            case "west" -> new Vector3f[]{p4, p3, p1, p2};
            case "east" -> new Vector3f[]{p7, p8, p6, p5};
            case "north" -> new Vector3f[]{p3, p7, p5, p1};
            case "south" -> new Vector3f[]{p8, p4, p2, p6};
            case "up" -> new Vector3f[]{p4, p8, p7, p3};
            case "down" -> new Vector3f[]{p1, p5, p6, p2};
            default -> null;
        };

        Vector4f tempPos = new Vector4f();
        for (int i = 0; i < 4; i++) {
            tempPos.set(positions[i].x(), positions[i].y(), positions[i].z(), 1.0f).mul(cubeBakeMat);
            face.positions[i] = new float[]{tempPos.x(), tempPos.y(), tempPos.z()};
        }

        face.u = new float[]{u0, u1, u1, u0};
        face.v = new float[]{v0, v0, v1, v1};
        cube.faces.add(face);
    }

    private static JsonObject createFaceUVNode(float u, float v, float w, float h) {
        JsonObject node = new JsonObject();
        JsonArray uv = new JsonArray(); uv.add(u); uv.add(v);
        JsonArray size = new JsonArray(); size.add(w); size.add(h);
        node.add("uv", uv);
        node.add("uv_size", size);
        return node;
    }
}
