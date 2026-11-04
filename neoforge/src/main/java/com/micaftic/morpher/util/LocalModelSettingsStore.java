package com.micaftic.morpher.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Persists local-player roaming model settings independently from a server's player data.
 * This lets the same settings follow the player when a proxy moves them between subservers.
 */
public final class LocalModelSettingsStore {

    private static final String MODELS = "models";
    private static final String TEXTURES = "textures";
    private static final Object LOCK = new Object();

    private LocalModelSettingsStore() {
    }

    private static Path file() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(YesSteveModel.MOD_ID).resolve("local_model_settings.json");
    }

    /** Queue after PLAYER_INIT; absent settings need no expression or animation-cache invalidation. */
    @Nullable
    public static IValue restoreExpression(String modelId, RoamingStruct target) {
        return restoreExpression(file(), modelId, target);
    }

    @Nullable
    static IValue restoreExpression(Path file, String modelId, RoamingStruct target) {
        if (target == null) return null;
        Map<String, Float> saved = readValues(file, modelId);
        if (saved.isEmpty()) return null;
        return evaluator -> {
            applyValues(saved, target);
            return null;
        };
    }

    public static void restore(String modelId, RoamingStruct target) {
        restore(file(), modelId, target);
    }

    static void restore(Path file, String modelId, RoamingStruct target) {
        if (target != null) applyValues(readValues(file, modelId), target);
    }

    private static void applyValues(Map<String, Float> saved, RoamingStruct target) {
        saved.forEach((name, value) -> target.putProperty(StringPool.computeIfAbsent(name), value));
    }

    private static Map<String, Float> readValues(Path file, String modelId) {
        if (!isValidModelId(modelId)) return Map.of();
        synchronized (LOCK) {
            JsonObject models = getOrCreateObject(readRoot(file), MODELS);
            JsonElement modelElement = models.get(modelId);
            if (modelElement == null || !modelElement.isJsonObject()) {
                return Map.of();
            }
            Map<String, Float> saved = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : modelElement.getAsJsonObject().entrySet()) {
                if (saved.size() >= RoamingStruct.MAX_VARS) {
                    break;
                }
                String variableName = entry.getKey();
                if (!isValidVariableName(variableName)) {
                    continue;
                }
                try {
                    float value = entry.getValue().getAsFloat();
                    if (Float.isFinite(value)) {
                        saved.put(variableName, value);
                    }
                } catch (RuntimeException ignored) {
                    // Ignore a single malformed value without discarding other saved settings.
                }
            }
            return Map.copyOf(saved);
        }
    }

    public static void save(String modelId, Int2FloatMap changedVariables) {
        save(file(), modelId, changedVariables);
    }

    public static void savePending(String modelId, RoamingStruct source) {
        savePending(file(), modelId, source);
    }

    static void savePending(Path file, String modelId, RoamingStruct source) {
        if (isValidModelId(modelId) && source != null && source.hasPendingChanges()) {
            save(file, modelId, source.consumePendingBoneData().changedVariables());
        }
    }

    static void save(Path file, String modelId, Int2FloatMap changedVariables) {
        if (!isValidModelId(modelId) || changedVariables == null || changedVariables.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            JsonObject root = readRoot(file);
            JsonObject models = getOrCreateObject(root, MODELS);
            JsonObject model = getOrCreateObject(models, modelId);
            for (Int2FloatMap.Entry entry : changedVariables.int2FloatEntrySet()) {
                String variableName = StringPool.getString(entry.getIntKey());
                float value = entry.getFloatValue();
                if (isValidVariableName(variableName) && Float.isFinite(value)) {
                    model.addProperty(variableName, value);
                }
            }
            writeRoot(file, root);
        }
    }

    public static String texture(String modelId) {
        return texture(file(), modelId);
    }

    static String texture(Path file, String modelId) {
        if (!isValidModelId(modelId)) return "";
        synchronized (LOCK) {
            JsonElement value = getOrCreateObject(readRoot(file), TEXTURES).get(modelId);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? value.getAsString() : "";
        }
    }

    public static void saveTexture(String modelId, String textureId) {
        saveTexture(file(), modelId, textureId);
    }

    static void saveTexture(Path file, String modelId, String textureId) {
        if (!isValidModelId(modelId) || textureId == null || textureId.isBlank() || textureId.length() > 512) return;
        synchronized (LOCK) {
            JsonObject root = readRoot(file);
            JsonObject textures = getOrCreateObject(root, TEXTURES);
            JsonElement previous = textures.get(modelId);
            if (previous != null && previous.isJsonPrimitive() && previous.getAsJsonPrimitive().isString()
                    && textureId.equals(previous.getAsString())) return;
            textures.addProperty(modelId, textureId);
            writeRoot(file, root);
        }
    }

    private static JsonObject readRoot(Path file) {
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try {
            return com.micaftic.morpher.core.storage.LocalJsonDocumentStore.readObject(file);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load local model settings: {}", e.getMessage());
            return new JsonObject();
        }
    }

    private static JsonObject getOrCreateObject(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value != null && value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        JsonObject object = new JsonObject();
        parent.add(key, object);
        return object;
    }

    private static void writeRoot(Path file, JsonObject root) {
        try {
            com.micaftic.morpher.core.storage.LocalJsonDocumentStore.saveObject(file, root);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save local model settings: {}", e.getMessage());
        }
    }

    private static boolean isValidModelId(String modelId) {
        return modelId != null && !modelId.isBlank() && !"default".equals(modelId) && modelId.length() <= 512;
    }

    private static boolean isValidVariableName(String name) {
        return name != null && !name.isBlank() && name.length() <= RoamingStruct.MAX_VAR_NAME_LENGTH;
    }
}
