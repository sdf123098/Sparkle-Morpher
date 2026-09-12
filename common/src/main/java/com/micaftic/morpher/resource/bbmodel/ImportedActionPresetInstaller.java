package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.bundle.GeometryBaker;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 1.2.7 §24.3：从 {@code BBToRawConverter} 外提的导入动作预设安装职责（行为等价，纯搬运）。
 *
 * <p>属于 <b>SPM 导入策略</b>而非格式转换：bbmodel 通常只带少量动画，
 * 而 SPM 的运行时会请求 idle/walk/run/attacked/death/swim/climb/sleep 等状态。
 * 本类为缺失状态补出 vanilla 风格的兜底动画，避免出现「无法正常播放 / 状态不齐全」。
 *
 * <p>全部使用 {@code putIfAbsent}，不覆盖模型自带动画。
 */
public final class ImportedActionPresetInstaller {

    private ImportedActionPresetInstaller() {
    }

    public static void ensureVanillaFallbackAnimations(RawYsmModel raw) {
        RawYsmModel.RawGeometry geometry = raw.mainEntity.mainModel;
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }

        RawYsmModel.RawAnimationFile animFile = raw.mainEntity.animationFiles.get("animation-main");
        if (animFile == null) {
            animFile = new RawYsmModel.RawAnimationFile();
            animFile.animType = 1;
            animFile.fileHash = UUID.randomUUID().toString();
            raw.mainEntity.animationFiles.put("animation-main", animFile);
        }

        Map<String, String> bones = collectNormalizedBoneNames(geometry);
        animFile.animations.putIfAbsent("idle", createVanillaFallbackAnimation("idle", bones, 0f, 0f));
        animFile.animations.putIfAbsent("walk", createVanillaFallbackAnimation("walk", bones, 25f, 35f));
        animFile.animations.putIfAbsent("run", createVanillaFallbackAnimation("run", bones, 35f, 45f));
        // 补充 AnimationRegister 注册但 bbmodel 无自带动画的高频状态，
        // 避免这些状态「无法正常播放 / 不齐全」。
        animFile.animations.putIfAbsent("attacked", createFallbackShakeAnimation("attacked", bones, 20f, 12f));
        animFile.animations.putIfAbsent("death", createFallbackPoseAnimation("death", bones, 90f, 90f));
        animFile.animations.putIfAbsent("swim", createVanillaFallbackAnimation("swim", bones, 45f, 30f));
        animFile.animations.putIfAbsent("climb", createVanillaFallbackAnimation("climb", bones, 35f, 40f));
        animFile.animations.putIfAbsent("climbing", createVanillaFallbackAnimation("climbing", bones, 30f, 35f));
        animFile.animations.putIfAbsent("sleep", createVanillaFallbackAnimation("sleep", bones, 8f, 8f));
    }

    private static Map<String, String> collectNormalizedBoneNames(RawYsmModel.RawGeometry geometry) {
        Map<String, String> out = new HashMap<>();
        for (RawYsmModel.RawBone bone : geometry.bones) {
            String normalized = GeometryBaker.normalizeBoneName(bone.name);
            if (!normalized.isEmpty()) {
                out.putIfAbsent(normalized, bone.name);
            }
        }
        return out;
    }

    private static RawYsmModel.RawAnimation createVanillaFallbackAnimation(String name, Map<String, String> bones,
                                                                         float armAmplitude, float legAmplitude) {
        RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
        anim.name = name;
        anim.length = 1.0f;
        anim.loopMode = 1;
        addFallbackBoneAnimation(anim, firstBone(bones, "leftarm", "leftupperarm", "leftshoulder", "leftuparm", "leftbicep", "armleft"), swingExpression(armAmplitude, false));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightarm", "rightupperarm", "rightshoulder", "rightuparm", "rightbicep", "armright"), swingExpression(armAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "leftleg", "leftupperleg", "leftthigh", "leftupleg", "legleft"), swingExpression(legAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightleg", "rightupperleg", "rightthigh", "rightupleg", "legright"), swingExpression(legAmplitude, false));
        return anim;
    }

    private static String firstBone(Map<String, String> bones, String... candidates) {
        for (String candidate : candidates) {
            String bone = bones.get(candidate);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }

    // 受击抖动：四肢快速小幅度高频摆动，配合 PLAY_ONCE 播放一次。
    private static RawYsmModel.RawAnimation createFallbackShakeAnimation(String name, Map<String, String> bones,
                                                                        float armAmplitude, float legAmplitude) {
        RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
        anim.name = name;
        anim.length = 1.0f;
        anim.loopMode = 0; // once
        addFallbackBoneAnimation(anim, firstBone(bones, "leftarm", "leftupperarm", "leftshoulder", "leftuparm", "leftbicep", "armleft"), shakeExpression(armAmplitude, false));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightarm", "rightupperarm", "rightshoulder", "rightuparm", "rightbicep", "armright"), shakeExpression(armAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "leftleg", "leftupperleg", "leftthigh", "leftupleg", "legleft"), shakeExpression(legAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightleg", "rightupperleg", "rightthigh", "rightupleg", "legright"), shakeExpression(legAmplitude, false));
        return anim;
    }

    // 固定姿态（如死亡前倾）：绕 X 固定角度，播放一次后保持。
    private static RawYsmModel.RawAnimation createFallbackPoseAnimation(String name, Map<String, String> bones,
                                                                       float armXRot, float legXRot) {
        RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
        anim.name = name;
        anim.length = 1.0f;
        anim.loopMode = 0; // once
        addFallbackBoneAnimation(anim, firstBone(bones, "leftarm", "leftupperarm", "leftshoulder", "leftuparm", "leftbicep", "armleft"), armXRot);
        addFallbackBoneAnimation(anim, firstBone(bones, "rightarm", "rightupperarm", "rightshoulder", "rightuparm", "rightbicep", "armright"), armXRot);
        addFallbackBoneAnimation(anim, firstBone(bones, "leftleg", "leftupperleg", "leftthigh", "leftupleg", "legleft"), legXRot);
        addFallbackBoneAnimation(anim, firstBone(bones, "rightleg", "rightupperleg", "rightthigh", "rightupleg", "legright"), legXRot);
        return anim;
    }

    private static String swingExpression(float amplitude, boolean oppositePhase) {
        if (amplitude == 0f) {
            return "0";
        }
        return "math.cos(query.anim_time * 360" + (oppositePhase ? " + 180" : "") + ") * " + amplitude;
    }

    // 受击抖动：更高频（4 倍速）的小幅摆动。
    private static String shakeExpression(float amplitude, boolean oppositePhase) {
        if (amplitude == 0f) {
            return "0";
        }
        return "math.cos(query.anim_time * 1440" + (oppositePhase ? " + 180" : "") + ") * " + amplitude;
    }

    private static void addFallbackBoneAnimation(RawYsmModel.RawAnimation anim, String boneName, Object xRotation) {
        if (boneName == null) {
            return;
        }
        RawYsmModel.RawBoneAnimation boneAnim = new RawYsmModel.RawBoneAnimation();
        boneAnim.boneName = boneName;
        RawYsmModel.RawKeyframe keyframe = new RawYsmModel.RawKeyframe();
        keyframe.timestamp = 0.0f;
        keyframe.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
        keyframe.postData = new Object[]{xRotation, 0f, 0f};
        boneAnim.rotation.add(keyframe);
        anim.boneAnimations.add(boneAnim);
    }
}
