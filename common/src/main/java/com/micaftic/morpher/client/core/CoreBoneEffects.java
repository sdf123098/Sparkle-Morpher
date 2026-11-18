package com.micaftic.morpher.client.core;

import dev.spm.core.api.BonePose;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.MolangValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-generation Host render styles. Core logical color is 0xAABBGGRR, never an old GPU layout. */
public final class CoreBoneEffects {
    private record Style(Integer rgb, Integer alpha, Integer glow) { }
    private Map<Integer, Style> styles = Map.of();

    public static boolean handles(EffectCommand effect) {
        return switch (effect.resource()) {
            case "ysm.bone_color", "ysm.bone_transparency", "ysm.bone_glow" -> true;
            default -> false;
        };
    }

    /** Invalid batches publish neither channel changes nor persistent overrides. */
    public synchronized BonePose apply(Skeleton skeleton, BonePose pose, List<EffectCommand> effects) {
        if (skeleton.boneCount() != pose.boneCount()) throw new IllegalArgumentException("Core style skeleton/pose mismatch");
        var candidate = new HashMap<>(styles);
        for (var effect : effects) {
            if (!handles(effect)) continue;
            var args = effect.arguments();
            int required = effect.resource().equals("ysm.bone_color") ? 4 : 2;
            if (args.size() != required || !(args.get(0) instanceof MolangValue.TextValue text))
                throw new IllegalArgumentException("Invalid Core bone style arguments: " + effect.resource());
            int bone = skeleton.names().indexOf(text.value());
            if (bone < 0 || text.value().isEmpty()) continue; // Certified source does not mutate missing bones.
            Style before = candidate.getOrDefault(bone, new Style(null, null, null));
            Style after = switch (effect.resource()) {
                case "ysm.bone_color" -> new Style(rounded(args.get(1), 0, 255)
                        | rounded(args.get(2), 0, 255) << 8 | rounded(args.get(3), 0, 255) << 16, before.alpha(), before.glow());
                case "ysm.bone_transparency" -> new Style(before.rgb(), rounded(args.get(1), 0, 255), before.glow());
                case "ysm.bone_glow" -> {
                    int glow = rounded(args.get(1), -1, 15);
                    yield new Style(before.rgb(), before.alpha(), glow == -1 ? 255 : glow);
                }
                default -> throw new AssertionError();
            };
            candidate.put(bone, after);
        }
        if (candidate.isEmpty()) return pose;
        int[] colors = pose.colorsRgba(), glow = pose.glow();
        candidate.forEach((bone, style) -> {
            if (style.rgb() != null) colors[bone] = (colors[bone] & 0xFF000000) | style.rgb();
            if (style.alpha() != null) colors[bone] = (colors[bone] & 0x00FFFFFF) | style.alpha() << 24;
            if (style.glow() != null) glow[bone] = style.glow();
        });
        var result = new BonePose(pose.rotations(), pose.translations(), pose.scales(), pose.ownHidden(), pose.childrenHidden(), glow, colors);
        styles = Map.copyOf(candidate);
        return result;
    }
    private static int rounded(MolangValue value, int min, int max) {
        return Math.clamp(Math.round((float) value.number()), min, max);
    }
}
