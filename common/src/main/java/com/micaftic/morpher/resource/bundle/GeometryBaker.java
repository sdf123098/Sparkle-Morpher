package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.RuntimeAccelerationLoader;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.model.SpecialHandLocatorProfile;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.resource.ModelOptimizationStats;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的几何烘焙职责（行为等价，纯搬运）。
 *
 * 负责 raw YSM 几何 → {@link GeoModel} 的烘焙：骨骼/立方体/面展开、透明度剔除、
 * cullable 判定、locator 骨名数组构建、优化统计。不含动画、贴图解码或元数据映射。
 */
public final class GeometryBaker {

    private GeometryBaker() {
    }

    public static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount, TextureAlphaAnalyzer scanner, boolean allCutout) {
        return buildMesh(rawGeo, context, textureCount, scanner, allCutout, SpecialHandLocatorProfile.NONE);
    }

    public static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount, TextureAlphaAnalyzer scanner, boolean allCutout, SpecialHandLocatorProfile specialHandLocatorProfile) {
        long bakeStart = System.nanoTime();
        ModelOptimizationStats stats = new ModelOptimizationStats();
        stats.textures = textureCount;
        if (rawGeo == null || rawGeo.bones.isEmpty()) {
            boolean[] fallbackArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
            GeoModel mesh = buildMesh(new GeoBone[0], new HashMap<>(), context, fallbackArray);
            stats.importBakeMillis = (System.nanoTime() - bakeStart) / 1_000_000L;
            mesh.optimizationStats = stats;
            logModelOptimizationStats(stats);
            return mesh;
        }

        List<GeoBone> geoBones = new ArrayList<>();
        List<GeoModel.BakedBone> bakedBones = new ArrayList<>();
        Map<String, String> parentMap = new HashMap<>();

        for (RawYsmModel.RawBone rb : rawGeo.bones) {
            stats.bones++;
            parentMap.put(rb.name, rb.parentName);
            geoBones.add(new GeoBone(rb.name, false, false, false, rb.pivot[0], rb.pivot[1], rb.pivot[2], rb.rotation[0], rb.rotation[1], rb.rotation[2]));

            GeoModel.BakedBone bb = new GeoModel.BakedBone();
            bb.name = rb.name;
            if (rb.name.startsWith("ysmGlow")) bb.glow = true;
            bb.pivotX = rb.pivot[0];
            bb.pivotY = rb.pivot[1];
            bb.pivotZ = rb.pivot[2];
            bb.rotX = rb.rotation[0];
            bb.rotY = rb.rotation[1];
            bb.rotZ = rb.rotation[2];
            bb.parentIdx = -1;

            // TODO: 优化算法

            boolean forceCull = allCutout;

            for (RawYsmModel.RawCube rc : rb.cubes) {
                stats.cubes++;
                GeoModel.BakedCube bc = new GeoModel.BakedCube();

                int validFaceCount = 0;
                boolean hasTranslucentFace = false;

                for (RawYsmModel.RawFace rf : rc.faces) {
                    stats.quadsBefore++;
                    int faceState = scanner != null ? scanner.scan(rf) : TextureAlphaAnalyzer.STATE_OPAQUE;

                    if (faceState == TextureAlphaAnalyzer.STATE_INVISIBLE) {
                        stats.prunedInvisibleFaces++;
                        continue;
                    }

                    if (faceState == TextureAlphaAnalyzer.STATE_TRANSLUCENT) {
                        hasTranslucentFace = true;
                        stats.translucentFaces++;
                    } else {
                        stats.opaqueFaces++;
                    }

                    if (!forceCull && isNegativeSizedFace(rf)) {
                        forceCull = true;
                    }
                    if (forceCull) {
                        stats.cutoutFaces++;
                    }
                    if (bb.glow) {
                        stats.glowFaces++;
                    }

                    GeoModel.BakedQuad bq = new GeoModel.BakedQuad();
                    bq.setNormal(rf.normal[0], rf.normal[1], rf.normal[2]);
                    for (int i = 0; i < 4; i++) {
                        float px = rf.positions[i][0];
                        float py = rf.positions[i][1];
                        float pz = rf.positions[i][2];

                        bq.setVertex(i, px, py, pz, rf.u[i], rf.v[i]);
                    }
                    bc.quads.add(bq);
                    stats.quadsAfter++;
                    validFaceCount++;
                }

                boolean isZeroThickness = true;
                if (!bc.quads.isEmpty()) {
                    GeoModel.BakedQuad baseQuad = bc.quads.get(0);
                    float baseNormalX = baseQuad.normalX;
                    float baseNormalY = baseQuad.normalY;
                    float baseNormalZ = baseQuad.normalZ;
                    float baseX = baseQuad.x(0);
                    float baseY = baseQuad.y(0);
                    float baseZ = baseQuad.z(0);

                    for (GeoModel.BakedQuad q : bc.quads) {
                        for (int i = 0; i < 4; i++) {
                            float dx = q.x(i) - baseX;
                            float dy = q.y(i) - baseY;
                            float dz = q.z(i) - baseZ;

                            float distance = dx * baseNormalX + dy * baseNormalY + dz * baseNormalZ;

                            if (Math.abs(distance) > 1e-3f) {
                                isZeroThickness = false;
                                break;
                            }
                        }
                        if (!isZeroThickness) break;
                    }
                } else {
                    isZeroThickness = false;
                }

                if (ConfigPolicies.disableModelFaceCulling()) {
                    bc.cullable = false;
                } else if (forceCull) {
                    bc.cullable = true;
                } else if (hasTranslucentFace) {
                    bc.cullable = false;
                } else if (isZeroThickness && validFaceCount > 1) {
                    bc.cullable = true;
                    stats.zeroThicknessFaces += validFaceCount;
                } else {
                    bc.cullable = validFaceCount >= 5;
                }

                if (!bc.quads.isEmpty()) {
                    bb.cubes.add(bc);
                }
            }
            bakedBones.add(bb);
        }

        // 回填父级索引
        for (GeoModel.BakedBone b : bakedBones) {
            String parentName = parentMap.get(b.name);
            if (parentName != null && !parentName.isEmpty()) {
                for (int i = 0; i < bakedBones.size(); i++) {
                    if (bakedBones.get(i).name.equals(parentName)) {
                        b.parentIdx = i;
                        break;
                    }
                }
            }
            if (b.name.equalsIgnoreCase("LeftArm")) b.partMask = 1;
            else if (b.name.equalsIgnoreCase("RightArm")) b.partMask = 2;
            else if (b.name.equals("Background")) b.partMask = 3;
            else if (b.parentIdx != -1) b.partMask = bakedBones.get(b.parentIdx).partMask;
            else b.partMask = 0;
        }
        finalizeOptimizationStats(bakedBones, stats);

        boolean[] translucencyArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
        GeoModel mesh = buildMesh(geoBones.toArray(new GeoBone[0]), parentMap, context, translucencyArray, specialHandLocatorProfile);

        mesh.bakedBones = bakedBones;
        mesh.bakedBoneOrder = GeoModel.buildParentFirstBoneOrder(bakedBones);
        mesh.buildPartMaskBoneRenderOrders();
        stats.importBakeMillis = (System.nanoTime() - bakeStart) / 1_000_000L;
        mesh.optimizationStats = stats;
        logModelOptimizationStats(stats);
        if (RuntimeAccelerationLoader.isLoaded()) mesh.buildNativeCache();
        return mesh;
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray) {
        return buildMesh(bones, parentMap, context, translucencyArray, SpecialHandLocatorProfile.NONE);
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray, SpecialHandLocatorProfile specialHandLocatorProfile) {
        String[][] boneNameArrays = buildBoneNameArrays(parentMap, specialHandLocatorProfile);
        boolean[] flags = new boolean[]{parentMap.containsKey("LeftArm"), parentMap.containsKey("RightArm"), parentMap.containsKey("Background")};
        return new GeoModel(bones, boneNameArrays, flags, context, translucencyArray);
    }

    public static GeometryDescription buildContext(RawYsmModel.RawGeometry model) {
        return new GeometryDescription(
                model.identifier,
                model.textureWidth, // default texture width ratio
                model.textureHeight, // default texture height ratio
                model.visibleBoundsWidth, // offset X
                model.visibleBoundsHeight, // offset Y
                model.visibleBoundsOffset == null
                        ? new double[]{0, 1.5, 0}
                        : IntStream.range(0, model.visibleBoundsOffset.length)
                                .mapToDouble(i -> model.visibleBoundsOffset[i])
                                .toArray()
        );
    }

    public static OrderedStringMap<String, OuterFileTexture> buildTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        String[] keys = textures.keySet().toArray(new String[0]);
        OuterFileTexture[] values = textures.values().toArray(new OuterFileTexture[0]);
        return new OrderedStringMap<>(keys, values);
    }

    public static Map<String, String> buildParentMap(RawYsmModel.RawGeometry rawGeo) {
        Map<String, String> parentMap = new HashMap<>();
        if (rawGeo == null || rawGeo.bones == null) {
            return parentMap;
        }
        for (RawYsmModel.RawBone bone : rawGeo.bones) {
            if (bone != null && bone.name != null && !bone.name.isEmpty()) {
                parentMap.put(bone.name, bone.parentName);
            }
        }
        return parentMap;
    }

    // ---- locator / 骨名解析（assembly policy 依赖的纯几何查表） ----

    public static String[] buildPath(String targetBone, Map<String, String> parentMap) {
        String resolvedTarget = findBoneName(targetBone, parentMap);
        if (resolvedTarget == null) {
            return new String[0];
        }
        List<String> path = new ArrayList<>();
        String current = resolvedTarget;
        while (current != null && !current.isEmpty()) {
            path.add(current);
            current = parentMap.get(current);
        }
        Collections.reverse(path);
        return path.toArray(new String[0]);
    }

    public static String[] buildPathFirst(Map<String, String> parentMap, String... targetBones) {
        for (String targetBone : targetBones) {
            String[] path = buildPath(targetBone, parentMap);
            if (path.length > 0) {
                return path;
            }
        }
        return new String[0];
    }

    public static String[] buildSwordPath(Map<String, String> parentMap, String baseName) {
        String[] path = buildPath(baseName, parentMap);
        if (path.length > 0) {
            return path;
        }
        String normalizedBase = normalizeBoneName(baseName);
        for (String boneName : parentMap.keySet()) {
            String normalizedName = normalizeBoneName(boneName);
            if (normalizedName.startsWith(normalizedBase) && normalizedName.substring(normalizedBase.length()).chars().allMatch(Character::isDigit)) {
                return buildPath(boneName, parentMap);
            }
        }
        return new String[0];
    }

    public static String findBoneName(String targetBone, Map<String, String> parentMap) {
        if (parentMap.containsKey(targetBone)) {
            return targetBone;
        }
        String normalizedTarget = normalizeBoneName(targetBone);
        for (String boneName : parentMap.keySet()) {
            if (normalizeBoneName(boneName).equals(normalizedTarget)) {
                return boneName;
            }
        }
        return null;
    }

    public static String normalizeBoneName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static boolean hasNormalizedExactBone(Map<String, String> parentMap, String targetBone) {
        return findBoneName(targetBone, parentMap) != null;
    }

    public static boolean hasNormalizedSwordBone(Map<String, String> parentMap, String baseName) {
        String normalizedBase = normalizeBoneName(baseName);
        for (String boneName : parentMap.keySet()) {
            String normalizedName = normalizeBoneName(boneName);
            if (normalizedName.equals(normalizedBase)
                    || (normalizedName.startsWith(normalizedBase)
                    && normalizedName.substring(normalizedBase.length()).chars().allMatch(Character::isDigit))) {
                return true;
            }
        }
        return false;
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap) {
        return buildBoneNameArrays(parentMap, SpecialHandLocatorProfile.NONE);
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap, SpecialHandLocatorProfile specialHandLocatorProfile) {
        String[][] arrays = new String[37][];

        // 模型骨骼大全
        String[] targetLocators = new String[]{
                "LeftHandLocator",
                "RightHandLocator",
                "ElytraLocator",
                "PistolLocator",
                "RifleLocator",
                "LeftWaistLocator",
                "RightWaistLocator",
                "LeftShoulderLocator",
                "RightShoulderLocator",
                "BladeLocator",
                "SheathLocator",
                "Head",
                "BackpackLocator",
                "LeftHandLocator2",
                "LeftHandLocator3",
                "LeftHandLocator4",
                "LeftHandLocator5",
                "LeftHandLocator6",
                "LeftHandLocator7",
                "LeftHandLocator8",
                "RightHandLocator2",
                "RightHandLocator3",
                "RightHandLocator4",
                "RightHandLocator5",
                "RightHandLocator6",
                "RightHandLocator7",
                "RightHandLocator8",
                "PassengerLocator",
                "PassengerLocator2",
                "PassengerLocator3",
                "PassengerLocator4",
                "PassengerLocator5",
                "PassengerLocator6",
                "PassengerLocator7",
                "PassengerLocator8",
                "LeftSword",
                "RightSword"
        };

        for (int i = 0; i < arrays.length; i++) {
            if (i == 0) {
                arrays[i] = buildPathFirst(parentMap,
                        "LeftHandLocator", "LeftItem", "LeftHand", "LeftPalm", "LeftWrist",
                        "LeftForeArm", "LeftLowerArm", "LeftArm");
            } else if (i == 1) {
                arrays[i] = buildPathFirst(parentMap,
                        "RightHandLocator", "RightItem", "RightHand", "RightPalm", "RightWrist",
                        "RightForeArm", "RightLowerArm", "RightArm");
            } else if (i == 35) {
                arrays[i] = specialHandLocatorProfile == SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON
                        ? buildPath("LeftHandLocator", parentMap)
                        : buildSwordPath(parentMap, "LeftSword");
            } else if (i == 36) {
                arrays[i] = specialHandLocatorProfile == SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON
                        ? buildPath("RightHandLocator", parentMap)
                        : buildSwordPath(parentMap, "RightSword");
            } else if (targetLocators[i] != null && !targetLocators[i].isEmpty()) {
                arrays[i] = buildPath(targetLocators[i], parentMap);
            } else {
                arrays[i] = new String[0];
            }
        }

        return arrays;
    }

    private static boolean isNegativeSizedFace(RawYsmModel.RawFace f) {
        float[] p0 = f.positions[0];
        float[] p1 = f.positions[1];
        float[] p2 = f.positions[2];

        float ax = p1[0] - p0[0];
        float ay = p1[1] - p0[1];
        float az = p1[2] - p0[2];

        float bx = p2[0] - p0[0];
        float by = p2[1] - p0[1];
        float bz = p2[2] - p0[2];

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float len2 = nx * nx + ny * ny + nz * nz;
        if (len2 <= 1e-10f) {
            float[] p3 = f.positions[3];

            bx = p3[0] - p0[0];
            by = p3[1] - p0[1];
            bz = p3[2] - p0[2];

            nx = ay * bz - az * by;
            ny = az * bx - ax * bz;
            nz = ax * by - ay * bx;

            len2 = nx * nx + ny * ny + nz * nz;
            if (len2 <= 1e-10f) {
                return false;
            }
        }

        float dot = nx * f.normal[0] + ny * f.normal[1] + nz * f.normal[2];
        return dot < -1e-5f;
    }

    private static void finalizeOptimizationStats(List<GeoModel.BakedBone> bakedBones, ModelOptimizationStats stats) {
        int totalQuads = 0;
        int totalCubes = 0;
        for (int boneIdx = 0; boneIdx < bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = bakedBones.get(boneIdx);
            totalCubes += bone.cubes.size();
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
                switch (bone.partMask) {
                    case 1 -> stats.partMaskLeftArmQuads += cube.quads.size();
                    case 2 -> stats.partMaskRightArmQuads += cube.quads.size();
                    default -> stats.partMaskAllQuads += cube.quads.size();
                }
            }
        }
        stats.estimatedBakedBytes = estimateBakedBytes(bakedBones.size(), totalCubes, totalQuads);
        stats.estimatedGpuMeshBytes = estimateGpuMeshBytes(totalQuads, bakedBones.size());
        stats.internalFaceCandidatePairs = countConservativeInternalFaceCandidates(bakedBones);
    }

    private static long estimateBakedBytes(int bones, int cubes, int quads) {
        return (long) bones * 96L + (long) cubes * 32L + (long) quads * 160L;
    }

    private static long estimateGpuMeshBytes(int quads, int bones) {
        long vertexBytes = (long) quads * 4L * 32L;
        long indexBytes = (long) quads * 6L * Integer.BYTES;
        long boneBytes = (long) bones * 144L * 2L;
        return vertexBytes + indexBytes + boneBytes;
    }

    private static int countConservativeInternalFaceCandidates(List<GeoModel.BakedBone> bakedBones) {
        Map<FaceAuditKey, int[]> buckets = new HashMap<>();
        for (int boneIdx = 0; boneIdx < bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = bakedBones.get(boneIdx);
            if (bone.glow) {
                continue;
            }
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    int axis = dominantAxis(quad.normalX, quad.normalY, quad.normalZ);
                    if (axis < 0 || isZeroAreaQuad(quad, axis)) {
                        continue;
                    }
                    int sign = normalSign(quad, axis);
                    if (sign == 0) {
                        continue;
                    }
                    FaceAuditKey key = FaceAuditKey.from(boneIdx, bone.partMask, axis, quad);
                    int[] counts = buckets.computeIfAbsent(key, ignored -> new int[2]);
                    counts[sign > 0 ? 1 : 0]++;
                }
            }
        }
        int pairs = 0;
        for (int[] counts : buckets.values()) {
            pairs += Math.min(counts[0], counts[1]);
        }
        return pairs;
    }

    private static int dominantAxis(float x, float y, float z) {
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float az = Math.abs(z);
        if (ax < 0.999f && ay < 0.999f && az < 0.999f) {
            return -1;
        }
        if (ax >= ay && ax >= az) return 0;
        if (ay >= az) return 1;
        return 2;
    }

    private static int normalSign(GeoModel.BakedQuad quad, int axis) {
        float value = switch (axis) {
            case 0 -> quad.normalX;
            case 1 -> quad.normalY;
            default -> quad.normalZ;
        };
        if (value > 0.999f) return 1;
        if (value < -0.999f) return -1;
        return 0;
    }

    private static boolean isZeroAreaQuad(GeoModel.BakedQuad quad, int axis) {
        float minA = Float.POSITIVE_INFINITY;
        float maxA = Float.NEGATIVE_INFINITY;
        float minB = Float.POSITIVE_INFINITY;
        float maxB = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float a;
            float b;
            if (axis == 0) {
                a = quad.y(i);
                b = quad.z(i);
            } else if (axis == 1) {
                a = quad.x(i);
                b = quad.z(i);
            } else {
                a = quad.x(i);
                b = quad.y(i);
            }
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        return Math.abs(maxA - minA) <= 1.0e-4f || Math.abs(maxB - minB) <= 1.0e-4f;
    }

    private record FaceAuditKey(int boneIndex, int partMask, int axis, int plane, int minA, int maxA, int minB, int maxB) {
        static FaceAuditKey from(int boneIndex, int partMask, int axis, GeoModel.BakedQuad quad) {
            float planeValue;
            float minA = Float.POSITIVE_INFINITY;
            float maxA = Float.NEGATIVE_INFINITY;
            float minB = Float.POSITIVE_INFINITY;
            float maxB = Float.NEGATIVE_INFINITY;
            if (axis == 0) {
                planeValue = quad.x(0);
            } else if (axis == 1) {
                planeValue = quad.y(0);
            } else {
                planeValue = quad.z(0);
            }
            for (int i = 0; i < 4; i++) {
                float a;
                float b;
                if (axis == 0) {
                    a = quad.y(i);
                    b = quad.z(i);
                } else if (axis == 1) {
                    a = quad.x(i);
                    b = quad.z(i);
                } else {
                    a = quad.x(i);
                    b = quad.y(i);
                }
                minA = Math.min(minA, a);
                maxA = Math.max(maxA, a);
                minB = Math.min(minB, b);
                maxB = Math.max(maxB, b);
            }
            return new FaceAuditKey(
                    boneIndex,
                    partMask,
                    axis,
                    quantize(planeValue),
                    quantize(minA),
                    quantize(maxA),
                    quantize(minB),
                    quantize(maxB)
            );
        }

        private static int quantize(float value) {
            return Math.round(value * 1000.0f);
        }
    }

    private static void logModelOptimizationStats(ModelOptimizationStats stats) {
        if (ConfigPolicies.diagnostics().modelImportPerformanceLog()) {
            YesSteveModel.LOGGER.info("[SM][Perf] model optimization {}", stats.toLogString());
        }
    }
}
