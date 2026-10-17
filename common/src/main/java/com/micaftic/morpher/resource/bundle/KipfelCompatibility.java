package com.micaftic.morpher.resource.bundle;

import com.google.gson.*;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Exact content allowlist. No model-name, bone-name or expression substring heuristics. */
public final class KipfelCompatibility {
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private static final Set<String> VERIFIED = Set.of("607594749a823ada654606df3fb1d163ef895678ae3fb558e63270e3541218c5");
    public static final String HAIR = "头发物理实现";
    public static final Set<String> OUTPUTS = Set.of("刘海噪声摆动实现", HAIR, "尾巴摆动");
    private static final Set<String> PROGRAMS = Set.of("刘海噪声摆动", "头发物理集合", "尾巴噪声摆动");
    private KipfelCompatibility() { }

    public static String fingerprint(RawYsmModel raw) {
        try {
            JsonObject content = GSON.toJsonTree(raw).getAsJsonObject();
            content.remove("modelId");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(content).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            for(String key : new TreeSet<>(value.getAsJsonObject().keySet())) sorted.add(key,canonical(value.getAsJsonObject().get(key)));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            value.getAsJsonArray().forEach(v->array.add(canonical(v)));
            return array;
        }
        return value;
    }
    public static boolean matches(RawYsmModel raw) {
        boolean candidate = raw.mainEntity.animationFiles.values().stream().anyMatch(f -> f.animations.containsKey(HAIR));
        return candidate && VERIFIED.contains(fingerprint(raw));
    }

    public static void bind(RawYsmModel raw, Map<String, AnimationFile> files, String modelId) {
        if (!matches(raw)) return;
        int tagged = 0;
        for(AnimationFile file : files.values()) {
            for(Animation animation : file.getAnimations().values()) {
                if (PROGRAMS.contains(animation.animationName)) {
                    // Only the three verified state-updater timelines; never external events.
                    animation.customInstructionKeyframes.clear();
                }
                if (OUTPUTS.contains(animation.animationName)) { animation.stableKipfelAttachments = true; tagged++; }
            }
        }
        YesSteveModel.LOGGER.info("[SM-PHYSICS] model={} fingerprint={} profile=kipfel-stable-v1 outputs={}",modelId,fingerprint(raw),tagged);
    }
}
