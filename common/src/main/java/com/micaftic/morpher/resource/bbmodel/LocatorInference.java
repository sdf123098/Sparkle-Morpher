package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.bundle.GeometryBaker;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.util.List;

/**
 * 1.2.7 §24.3：从 {@code BBToRawConverter} 外提的 locator 推断职责（行为等价，纯搬运）。
 *
 * <p>属于 <b>SPM 导入策略</b>而非格式转换：bbmodel / Figura 模型常常缺少 YSM 渲染端
 * 需要的挂点骨骼（手部、鞘翅）。本类在导入时按候选父骨推断并补齐这些 locator，
 * 并根据父骨几何包围盒估算挂点 pivot。
 *
 * <p>与 {@link GeometryBaker} 的分工（§24.3 Q2）：
 * <ul>
 *   <li>{@code GeometryBaker} = 读取侧查表（给定 parentMap，解析渲染用骨名路径）</li>
 *   <li>{@code LocatorInference} = 写入侧合成（导入时向 geometry 补建缺失 locator）</li>
 *   <li>两者共用 {@link GeometryBaker#normalizeBoneName(String)} 作为唯一归一化实现</li>
 * </ul>
 */
public final class LocatorInference {

    private LocatorInference() {
    }

    static final String ELYTRA_LOCATOR = "ElytraLocator";
    static final String LEFT_HAND_LOCATOR = "LeftHandLocator";
    static final String RIGHT_HAND_LOCATOR = "RightHandLocator";

    private static final String[] ELYTRA_PARENT_CANDIDATES = {
            "body", "torso", "chest", "upperbody", "vanillabody", "waist"
    };
    private static final String[] LEFT_HAND_PARENT_CANDIDATES = {
            "lefthand", "leftpalm", "leftwrist", "leftforearm", "leftlowerarm", "leftarm"
    };
    private static final String[] RIGHT_HAND_PARENT_CANDIDATES = {
            "righthand", "rightpalm", "rightwrist", "rightforearm", "rightlowerarm", "rightarm"
    };

    public static void ensureHandLocators(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }
        ensureHandLocator(geometry, LEFT_HAND_LOCATOR, LEFT_HAND_PARENT_CANDIDATES);
        ensureHandLocator(geometry, RIGHT_HAND_LOCATOR, RIGHT_HAND_PARENT_CANDIDATES);
    }

    public static void ensureElytraLocator(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }
        if (findBoneByName(geometry.bones, ELYTRA_LOCATOR) != null) {
            return;
        }
        RawYsmModel.RawBone parent = findPreferredParentBone(geometry.bones, ELYTRA_PARENT_CANDIDATES);
        if (parent == null) {
            return;
        }

        RawYsmModel.RawBone locator = new RawYsmModel.RawBone();
        locator.name = ELYTRA_LOCATOR;
        locator.parentName = parent.name == null ? "" : parent.name;
        locator.pivot = estimateElytraLocatorPivot(parent);
        locator.rotation = new float[]{0, 0, 0};
        geometry.bones.add(locator);
    }

    private static void ensureHandLocator(RawYsmModel.RawGeometry geometry, String locatorName, String[] parentCandidates) {
        if (findBoneByName(geometry.bones, locatorName) != null) {
            return;
        }
        RawYsmModel.RawBone parent = findPreferredParentBone(geometry.bones, parentCandidates);
        if (parent == null) {
            return;
        }

        RawYsmModel.RawBone locator = new RawYsmModel.RawBone();
        locator.name = locatorName;
        locator.parentName = parent.name == null ? "" : parent.name;
        locator.pivot = estimateLocatorPivot(parent);
        locator.rotation = new float[]{0, 0, 0};
        geometry.bones.add(locator);
    }

    private static RawYsmModel.RawBone findBoneByName(List<RawYsmModel.RawBone> bones, String name) {
        for (RawYsmModel.RawBone bone : bones) {
            if (name.equals(bone.name)) {
                return bone;
            }
        }
        return null;
    }

    private static RawYsmModel.RawBone findPreferredParentBone(List<RawYsmModel.RawBone> bones, String[] candidates) {
        for (String candidate : candidates) {
            for (RawYsmModel.RawBone bone : bones) {
                if (candidate.equals(GeometryBaker.normalizeBoneName(bone.name))) {
                    return bone;
                }
            }
        }

        RawYsmModel.RawBone best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RawYsmModel.RawBone bone : bones) {
            String normalized = GeometryBaker.normalizeBoneName(bone.name);
            int score = handParentScore(normalized, candidates);
            if (score > bestScore) {
                bestScore = score;
                best = bone;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int handParentScore(String normalizedName, String[] candidates) {
        int score = 0;
        for (int i = 0; i < candidates.length; i++) {
            if (normalizedName.contains(candidates[i])) {
                score = Math.max(score, 100 - i * 10);
            }
        }
        if (score == 0) {
            return 0;
        }
        if (normalizedName.contains("locator") || normalizedName.contains("cloth")
                || normalizedName.contains("sleeve") || normalizedName.contains("item")) {
            score -= 50;
        }
        return score;
    }

    private static float[] estimateElytraLocatorPivot(RawYsmModel.RawBone parent) {
        Bounds bounds = Bounds.from(parent);
        float[] pivot = parent.pivot == null ? new float[]{0, 24, 2} : parent.pivot.clone();
        if (pivot.length < 3) {
            pivot = new float[]{0, 24, 2};
        }
        // 鞘翅挂在躯干的“上背/肩颈”处，与原版一致：
        //   X 取躯干水平中心；Y 取躯干顶部（肩线）——原版鞘翅模型自带向下延展的翼面，从肩线垂下才是正确观感；
        //   Z 取躯干背面再略微后移，让翼面贴在背后。
        // 旧实现此处用 pivot[1] - 6，会把挂点压到躯干中部，导致鞘翅整体偏低、像裙子一样垂到腿上。
        if (bounds.valid) {
            return new float[]{
                    (bounds.minX + bounds.maxX) * 0.5f,
                    bounds.maxY,
                    bounds.maxZ + 1.5f
            };
        }
        // 无几何包围盒时退回 body 骨骼 pivot：标准人形 body 的 pivot 位于颈部（上背），
        // 直接使用该高度并略微后移即可，不再向下偏移。
        pivot[2] += 1.5f;
        return pivot;
    }

    private static float[] estimateLocatorPivot(RawYsmModel.RawBone parent) {
        Bounds bounds = Bounds.from(parent);
        if (bounds.valid) {
            String normalized = GeometryBaker.normalizeBoneName(parent.name);
            boolean armBone = normalized.contains("arm") && !normalized.contains("hand")
                    && !normalized.contains("palm") && !normalized.contains("wrist");
            float y = armBone ? bounds.minY : (bounds.minY + bounds.maxY) * 0.5f;
            return new float[]{
                    (bounds.minX + bounds.maxX) * 0.5f,
                    y,
                    (bounds.minZ + bounds.maxZ) * 0.5f
            };
        }
        return parent.pivot == null ? new float[]{0, 0, 0} : parent.pivot.clone();
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean valid;

        private static Bounds from(RawYsmModel.RawBone bone) {
            Bounds bounds = new Bounds();
            if (bone == null || bone.cubes == null) {
                return bounds;
            }
            for (RawYsmModel.RawCube cube : bone.cubes) {
                if (cube == null || cube.faces == null) {
                    continue;
                }
                for (RawYsmModel.RawFace face : cube.faces) {
                    if (face == null || face.positions == null) {
                        continue;
                    }
                    for (float[] position : face.positions) {
                        if (position == null || position.length < 3) {
                            continue;
                        }
                        bounds.include(position[0] * 16f, position[1] * 16f, position[2] * 16f);
                    }
                }
            }
            return bounds;
        }

        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            valid = true;
        }
    }
}
