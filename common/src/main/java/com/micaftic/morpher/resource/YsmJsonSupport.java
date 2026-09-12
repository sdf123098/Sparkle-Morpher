package com.micaftic.morpher.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 1.2.7 §24.2：从 {@code YSMFolderDeserializer} 外提的 JSON 取值辅助（行为等价，纯搬运）。
 *
 * 供 manifest / metadata / texture / geometry / animation 各解析类共用的纯函数。
 */
public final class YsmJsonSupport {

    private YsmJsonSupport() {
    }

    public static String getStr(JsonObject obj, String key, String def) {
        return obj.has(key) ? getJsonString(obj.get(key)) : def;
    }
    public static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }
    public static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }
    public static float[] getFloatArray(JsonObject obj, String key, int size) {
        float[] result = new float[size];
        if (obj.has(key)) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (int i = 0; i < Math.min(arr.size(), size); i++) result[i] = arr.get(i).getAsFloat();
        }
        return result;
    }
    public static String getJsonString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }
    public static String cleanJsonString(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.length() >= 2 && cleaned.charAt(0) == '"' && cleaned.charAt(cleaned.length() - 1) == '"') {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
    public static String extractFileName(String fullPath) {
        String name = cleanJsonString(fullPath);
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) name = name.substring(0, dotIdx);
        return name;
    }
}
