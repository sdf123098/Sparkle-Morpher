package com.micaftic.morpher.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Lowers particle events to the timeline already preserved by the YSM binary format. */
public final class AnimationParticleEvents {
    private AnimationParticleEvents() {}

    public static List<RawYsmModel.RawTimelineEvent> read(JsonObject animation) {
        var result = new ArrayList<RawYsmModel.RawTimelineEvent>();
        JsonElement effects = animation.get("particle_effects");
        if (effects == null || !effects.isJsonObject()) return result;
        for (var entry : effects.getAsJsonObject().entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            if (!Float.isFinite(time) || time < 0) continue;
            JsonElement value = entry.getValue();
            Iterable<JsonElement> events = value.isJsonArray() ? value.getAsJsonArray() : List.of(value);
            for (JsonElement event : events) {
                if (!event.isJsonObject()) continue;
                var object = event.getAsJsonObject();
                String effect = text(object, "effect");
                if (effect.isBlank()) continue;
                String script = text(object, "pre_effect_script").strip();
                if (!script.isEmpty() && !script.endsWith(";")) script += ";";
                var frame = new RawYsmModel.RawTimelineEvent();
                frame.timestamp = time;
                // Base64 keeps particle options and arbitrary bone names out of Molang source syntax.
                frame.events.add("ysm.animation_particle('" + encode(effect) + "','"
                        + encode(text(object, "locator")) + "',{" + script + "});");
                result.add(frame);
            }
        }
        result.sort(Comparator.comparingDouble(frame -> frame.timestamp));
        return result;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
