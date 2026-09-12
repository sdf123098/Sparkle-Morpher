package com.micaftic.morpher.resource;

import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 1.2.7 §24.2：从 {@code YSMFolderDeserializer} 外提的动画解析职责（行为等价，纯搬运）。
 *
 * 负责 Bedrock 动画 JSON → RawAnimationFile 的解析（关键帧 / Molang 数组），
 * 以及动画类型 key ↔ id 的映射。不读任何 zip/目录资源。
 */
public final class YsmAnimationParsing {

    private YsmAnimationParsing() {
    }

    public static RawYsmModel.RawAnimationFile parseAnimationFile(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        RawYsmModel.RawAnimationFile raf = new RawYsmModel.RawAnimationFile();

        if (root.has("animations")) {
            JsonObject anims = root.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject aObj = entry.getValue().getAsJsonObject();
                RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
                anim.name = entry.getKey();
                anim.length = (float) YsmJsonSupport.getDouble(aObj, "animation_length", Float.POSITIVE_INFINITY);

                if (aObj.has("loop")) {
                    String loopStr = YsmJsonSupport.getJsonString(aObj.get("loop"));
                    if ("true".equals(loopStr)) anim.loopMode = 1;
                    else if ("hold_on_last_frame".equals(loopStr)) anim.loopMode = 3;
                    else anim.loopMode = 0;
                } else {
                    anim.loopMode = 2;
                }

                if (aObj.has("blend_weight")) {
                    JsonElement bw = aObj.get("blend_weight");
                    if (bw.isJsonPrimitive() && bw.getAsJsonPrimitive().isNumber()) {
                        anim.blendWeight = bw.getAsFloat();
                    } else {
                        anim.blendWeight = YsmJsonSupport.getJsonString(bw);
                    }
                }

                if (aObj.has("bones") && aObj.get("bones").isJsonObject()) {
                    JsonObject bonesObj = aObj.getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> bEntry : bonesObj.entrySet()) {
                        if (!bEntry.getValue().isJsonObject()) continue;
                        JsonObject bObj = bEntry.getValue().getAsJsonObject();
                        RawYsmModel.RawBoneAnimation ba = new RawYsmModel.RawBoneAnimation();
                        ba.boneName = bEntry.getKey();

                        parseChannelToKeyframes(bObj, "rotation", ba.rotation);
                        parseChannelToKeyframes(bObj, "position", ba.position);
                        parseChannelToKeyframes(bObj, "scale", ba.scale);

                        anim.boneAnimations.add(ba);
                    }
                }

                if (aObj.has("timeline") && aObj.get("timeline").isJsonObject()) {
                    JsonObject tlObj = aObj.getAsJsonObject("timeline");
                    for (Map.Entry<String, JsonElement> tlEntry : tlObj.entrySet()) {
                        RawYsmModel.RawTimelineEvent tle = new RawYsmModel.RawTimelineEvent();
                        tle.timestamp = Float.parseFloat(tlEntry.getKey());
                        JsonElement val = tlEntry.getValue();
                        Iterable<JsonElement> arr = val.isJsonArray() ? val.getAsJsonArray() : Collections.singletonList(val);
                        for (JsonElement e : arr) tle.events.add(YsmJsonSupport.getJsonString(e));
                        anim.timelineEvents.add(tle);
                    }
                }

                if (aObj.has("sound_effects") && aObj.get("sound_effects").isJsonObject()) {
                    JsonObject sfxObj = aObj.getAsJsonObject("sound_effects");
                    for (Map.Entry<String, JsonElement> sfxEntry : sfxObj.entrySet()) {
                        RawYsmModel.RawSoundEffect sfx = new RawYsmModel.RawSoundEffect();
                        sfx.timestamp = Float.parseFloat(sfxEntry.getKey());
                        sfx.effectName = YsmJsonSupport.getStr(sfxEntry.getValue().getAsJsonObject(), "effect", "");
                        anim.soundEffects.add(sfx);
                    }
                }

                raf.animations.put(anim.name, anim);
            }
        }
        return raf;
    }

    private static void parseChannelToKeyframes(JsonObject bObj, String channel, List<RawYsmModel.RawKeyframe> targetList) {
        if (!bObj.has(channel)) return;
        JsonElement cElem = bObj.get(channel);

        if (!cElem.isJsonObject()) {
            RawYsmModel.RawKeyframe kf = new RawYsmModel.RawKeyframe();
            kf.timestamp = 0.0f;
            kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
            kf.hasPreData = false;
            kf.postData = jsonElementToMolangArray(cElem);
            targetList.add(kf);
            return;
        }

        JsonObject kfsObj = cElem.getAsJsonObject();
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(kfsObj.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> Double.parseDouble(e.getKey())));

        for (Map.Entry<String, JsonElement> entry : sorted) {
            RawYsmModel.RawKeyframe kf = new RawYsmModel.RawKeyframe();
            kf.timestamp = Float.parseFloat(entry.getKey());
            kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;

            JsonElement valElem = entry.getValue();
            if (valElem.isJsonObject()) {
                JsonObject obj = valElem.getAsJsonObject();
                if (obj.has("lerp_mode")) {
                    String lm = YsmJsonSupport.getJsonString(obj.get("lerp_mode"));
                    if ("catmullrom".equals(lm)) kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM;
                    else if ("step".equals(lm)) kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_STEP;
                } else
                    kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_STEP;

                if (obj.has("pre") && obj.has("post")) {
                    kf.hasPreData = true;
                    kf.preData = jsonElementToMolangArray(obj.get("pre"));
                    kf.postData = jsonElementToMolangArray(obj.get("post"));
                } else {
                    kf.hasPreData = false;
                    kf.postData = jsonElementToMolangArray(obj.has("post") ? obj.get("post") : obj.has("pre") ? obj.get("pre") : obj);
                }
            } else {
                kf.hasPreData = false;
                kf.postData = jsonElementToMolangArray(valElem);
            }
            targetList.add(kf);
        }
    }

    private static Object[] jsonElementToMolangArray(JsonElement elem) {
        Object[] arr = new Object[]{0f, 0f, 0f};
        if (elem == null || elem.isJsonNull()) return arr;

        if (elem.isJsonArray()) {
            JsonArray jArr = elem.getAsJsonArray();
            for (int i = 0; i < Math.min(3, jArr.size()); i++) {
                JsonElement e = jArr.get(i);
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) arr[i] = e.getAsFloat();
                else arr[i] = YsmJsonSupport.getJsonString(e);
            }
        } else {
            Object val;
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) val = elem.getAsFloat();
            else val = YsmJsonSupport.getJsonString(elem);
            arr[0] = val; arr[1] = val; arr[2] = val;
        }
        return arr;
    }

    public static int getAnimTypeFromKey(String key) {
        if (key == null) return 0;
        return switch (key) {
            case "main" -> 1;
            case "arm" -> 2;
            case "extra" -> 3;
            case "tac" -> 4;
            case "arrow" -> 5;
            case "carryon" -> 6;
            case "parcool" -> 7;
            case "swem" -> 8;
            case "slashblade" -> 9;
            case "tlm" -> 10;
            case "fp.arm", "fp_arm" -> 11;
            case "immersive_melodies" -> 12;
            case "iss", "irons_spell_books" -> 13;
            default -> parseUnknownAnimType(key);
        };
    }

    private static int parseUnknownAnimType(String key) {
        if (!key.startsWith("unk_")) return 0;
        try {
            int type = Integer.parseInt(key.substring(4));
            return type >= 0 ? type : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String getAnimKeyFromType(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unk_" + type;
        };
    }
}
