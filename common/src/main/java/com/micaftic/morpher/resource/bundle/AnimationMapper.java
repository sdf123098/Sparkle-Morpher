package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.core.HostAnimationAssets.BlendEntry;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerStateSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.TransitionEntry;
import com.micaftic.morpher.client.core.HostMolangRegistry;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.2.7 §24.1：将 raw YSM 动画/控制器 JSON 结构映射为 SPM 宿主规范（{@link ClipSpec} /
 * {@link ControllerSpec}）以及 {@link HostMolangScript} 列表；不含几何或装配策略。
 */
public final class AnimationMapper {

    private AnimationMapper() {
    }

    public static Map<String, ClipSpec> buildAnimations(RawYsmModel.RawAnimationFile animFile, boolean mergeMultilineExpr) {
        Map<String, ClipSpec> result = new LinkedHashMap<>();
        if (animFile == null || animFile.animations == null) {
            return result;
        }
        for (RawYsmModel.RawAnimation ra : animFile.animations.values()) {
            List<HostMolangScript> parsed = new ArrayList<>();
            if (ra.boneAnimations != null) {
                for (RawYsmModel.RawBoneAnimation rba : ra.boneAnimations) {
                    collectKeyframeScripts(rba.rotation, parsed);
                    collectKeyframeScripts(rba.position, parsed);
                    collectKeyframeScripts(rba.scale, parsed);
                    warnBezierIfDebug(rba.rotation);
                    warnBezierIfDebug(rba.position);
                    warnBezierIfDebug(rba.scale);
                }
            }
            if (ra.timelineEvents != null) {
                for (RawYsmModel.RawTimelineEvent rte : ra.timelineEvents) {
                    parsed.addAll(parse(rte.events, mergeMultilineExpr));
                }
            }
            if (ra.blendWeight instanceof String weightExpr && !weightExpr.isBlank()) {
                parsed.add(parse(weightExpr));
            }
            result.put(ra.name, new ClipSpec(ra.name, ra, parsed, null, false, false));
        }
        return result;
    }

    private static void collectKeyframeScripts(List<RawYsmModel.RawKeyframe> frames, List<HostMolangScript> out) {
        if (frames == null) {
            return;
        }
        for (RawYsmModel.RawKeyframe rk : frames) {
            if (rk.hasPreData) {
                collectVectorScripts(rk.preData, out);
                collectVectorScripts(rk.postData, out);
            } else {
                collectVectorScripts(rk.postData, out);
            }
        }
    }

    private static void collectVectorScripts(Object[] data, List<HostMolangScript> out) {
        if (data == null) {
            return;
        }
        for (int i = 0; i < Math.min(3, data.length); i++) {
            if (data[i] instanceof String s && !s.isBlank()) {
                out.add(parse(s));
            }
        }
    }

    private static void warnBezierIfDebug(List<RawYsmModel.RawKeyframe> frames) {
        if (frames == null || !ConfigPolicies.diagnostics().animationDebugLog()) {
            return;
        }
        for (RawYsmModel.RawKeyframe rk : frames) {
            if (rk.interpolationMode == RawYsmModel.RawKeyframe.INTERPOLATION_BEZIER) {
                YesSteveModel.LOGGER.warn("[SM-ANIM] Bezier keyframe at {}s was downgraded to linear because runtime Bezier evaluation is not available yet", rk.timestamp);
            }
        }
    }

    public static Map<String, ControllerSpec> buildControllers(Map<String, RawYsmModel.RawAnimationController> rawControllers, boolean mergeMultilineExpr) {
        Map<String, ControllerSpec> result = new LinkedHashMap<>();
        if (rawControllers == null) {
            return result;
        }
        for (RawYsmModel.RawAnimationController rac : rawControllers.values()) {
            Map<String, ControllerStateSpec> states = new LinkedHashMap<>();
            for (RawYsmModel.RawControllerState rs : rac.states) {
                List<BlendEntry> animations = new ArrayList<>(rs.animations.size());
                for (Map.Entry<String, String> e : rs.animations) {
                    HostMolangScript blend = (e.getValue() != null && !e.getValue().isEmpty()) ? parse(e.getValue()) : null;
                    animations.add(new BlendEntry(e.getKey(), blend));
                }

                List<TransitionEntry> transitions = new ArrayList<>(rs.transitions.size());
                for (Map.Entry<String, String> e : rs.transitions) {
                    transitions.add(new TransitionEntry(e.getKey(), parse(e.getValue())));
                }

                List<HostMolangScript> onEntry = parse(rs.onEntry, mergeMultilineExpr);
                List<HostMolangScript> onExit = parse(rs.onExit, mergeMultilineExpr);
                states.put(rs.name, new ControllerStateSpec(rs.name, rs, animations, transitions, onEntry, onExit));
            }
            String initialState = (rac.initialState == null || rac.initialState.isEmpty()) ? "default" : rac.initialState;
            result.put(rac.animationName, new ControllerSpec(rac.animationName, initialState, states, rac));
        }
        return result;
    }

    public static List<HostMolangScript> parse(List<String> array, boolean mergeMultilineExpr) {
        return HostMolangRegistry.parseList(array, mergeMultilineExpr);
    }

    public static HostMolangScript parse(String str) {
        return HostMolangRegistry.parseExpression(str);
    }
}
