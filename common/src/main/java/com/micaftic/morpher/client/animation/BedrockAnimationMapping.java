package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基岩版动画名 → mod 动作名映射（阶段 3 方案 B）。
 *
 * <p>Bedrock 玩家动画由 animation controller 调度（animation.player.* / animation.humanoid.*），
 * 而 mod 的播放器按动作名（idle/walk/swim/sneak/sleep/ride/swing_hand…）查动画。本表把
 * 基岩版动画重命名到 mod 动作名：</p>
 * <ul>
 *   <li>Bedrock 直读入口（{@code ClientModelManager.parseBedrockPackImport}）导入时合并改名；</li>
 *   <li>运行时兜底（{@code LivingAnimatable.getAnimation}）：bbmodel 内嵌基岩名动画也能被动作名命中。</li>
 * </ul>
 * <p>矛（spear）相关动画（brandish_spear/charging 等）按计划跳过、复用 Java 原版，不在此表。</p>
 */
public final class BedrockAnimationMapping {

    private BedrockAnimationMapping() {
    }

    /** Bedrock 动画名 → mod 动作名（LinkedHashMap 保证候选顺序确定）。 */
    private static final Map<String, String> BEDROCK_TO_ACTION = buildToActionMap();

    /** 动作名 → Bedrock 候选动画名（顺序 = 优先级）。 */
    private static final Map<String, List<String>> ACTION_CANDIDATES = buildActionCandidates();

    private static Map<String, String> buildToActionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        // walk
        map.put("animation.player.move.arms", "walk");
        map.put("animation.player.move.legs", "walk");
        map.put("animation.player.move.arms.single", "walk");
        map.put("animation.player.move.legs.single", "walk");
        map.put("animation.player.move.arms.stationary", "walk");
        map.put("animation.player.move.legs.stationary", "walk");
        map.put("animation.humanoid.move", "walk");
        map.put("animation.humanoid.move_fixed", "walk");
        // swim
        map.put("animation.player.swim", "swim");
        map.put("animation.player.swim.legs", "swim");
        map.put("animation.player.swim.legs.single", "swim");
        map.put("animation.player.swim.legs.stationary", "swim");
        map.put("animation.humanoid.swimming", "swim");
        // sneak
        map.put("animation.player.sneaking", "sneak");
        map.put("animation.player.sneaking.inverted", "sneak");
        map.put("animation.humanoid.sneaking", "sneak");
        // sleep
        map.put("animation.player.sleeping", "sleep");
        // ride
        map.put("animation.player.riding.arms", "ride");
        map.put("animation.player.riding.legs", "ride");
        map.put("animation.humanoid.riding.arms", "ride");
        map.put("animation.humanoid.riding.legs", "ride");
        // attack
        map.put("animation.player.attack.rotations", "swing_hand");
        map.put("animation.player.attack.positions", "swing_hand");
        map.put("animation.humanoid.attack.rotations", "swing_hand");
        return map;
    }

    private static Map<String, List<String>> buildActionCandidates() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : BEDROCK_TO_ACTION.entrySet()) {
            map.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(entry.getKey());
        }
        return map;
    }

    /**
     * 把 Bedrock 动画文件合并重命名为动作动画。
     * 未映射的动画（矛/控制器调度类）不导入；多个源动画映射到同一动作时合并骨骼通道，
     * 同骨骼后到覆盖先到。
     */
    public static Map<String, RawYsmModel.RawAnimationFile> remapToActions(
            Map<String, RawYsmModel.RawAnimationFile> animationFiles) {
        if (animationFiles == null || animationFiles.isEmpty()) {
            return animationFiles;
        }
        Map<String, RawYsmModel.RawAnimation> merged = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile file : animationFiles.values()) {
            if (file == null || file.animations == null) continue;
            for (RawYsmModel.RawAnimation animation : file.animations.values()) {
                if (animation == null || animation.name == null) continue;
                String action = BEDROCK_TO_ACTION.get(animation.name);
                if (action == null) continue;
                RawYsmModel.RawAnimation target = merged.get(action);
                if (target == null) {
                    target = new RawYsmModel.RawAnimation();
                    target.name = action;
                    target.length = animation.length;
                    target.loopMode = animation.loopMode;
                    merged.put(action, target);
                } else {
                    target.length = Math.max(target.length, animation.length);
                    if (animation.loopMode == 1) {
                        target.loopMode = 1;
                    }
                }
                if (animation.boneAnimations == null) continue;
                for (RawYsmModel.RawBoneAnimation boneAnimation : animation.boneAnimations) {
                    if (boneAnimation == null || boneAnimation.boneName == null) continue;
                    target.boneAnimations.removeIf(existing -> existing.boneName != null
                            && existing.boneName.equals(boneAnimation.boneName));
                    target.boneAnimations.add(boneAnimation);
                }
            }
        }
        if (merged.isEmpty()) {
            return animationFiles;
        }
        Map<String, RawYsmModel.RawAnimationFile> result = new LinkedHashMap<>();
        RawYsmModel.RawAnimationFile actionFile = new RawYsmModel.RawAnimationFile();
        actionFile.animType = 1;
        actionFile.animations.putAll(merged);
        result.put("bedrock-anim-actions", actionFile);
        return result;
    }

    /** 运行时兜底：动作名查不到时，按候选 Bedrock 动画名反查（bbmodel 内嵌基岩名动画）。 */
    public static Animation findByAction(Object2ReferenceMap<String, Animation> animations, String action) {
        if (animations == null || action == null) {
            return null;
        }
        List<String> candidates = ACTION_CANDIDATES.get(action);
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            Animation found = animations.get(candidate);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
