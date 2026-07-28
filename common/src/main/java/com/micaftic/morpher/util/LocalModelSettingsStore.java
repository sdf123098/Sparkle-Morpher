package com.micaftic.morpher.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import dev.architectury.platform.Platform;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Persists local-player roaming model settings independently from a server's player data.
 * This lets the same settings follow the player when a proxy moves them between subservers.
 */
public final class LocalModelSettingsStore {

    private static final Path FILE = Platform.getConfigFolder()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("local_model_settings.json");
    private static final String MODELS = "models";
    private static final Object LOCK = new Object();

    private LocalModelSettingsStore() {
    }

    public static void restore(String modelId, RoamingStruct target) {
        if (!isValidModelId(modelId) || target == null) {
            return;
        }
        synchronized (LOCK) {
            JsonObject models = readModels();
            JsonElement modelElement = models.get(modelId);
            if (modelElement == null || !modelElement.isJsonObject()) {
                return;
            }
            int restored = 0;
            for (Map.Entry<String, JsonElement> entry : modelElement.getAsJsonObject().entrySet()) {
                if (restored >= RoamingStruct.MAX_VARS) {
                    break;
                }
                String variableName = entry.getKey();
                if (!isValidVariableName(variableName)) {
                    continue;
                }
                try {
                    float value = entry.getValue().getAsFloat();
                    if (Float.isFinite(value)) {
                        target.putProperty(StringPool.computeIfAbsent(variableName), value);
                        restored++;
                    }
                } catch (RuntimeException ignored) {
                    // Ignore a single malformed value without discarding other saved settings.
                }
            }
        }
    }

    public static void save(String modelId, Int2FloatMap changedVariables) {
        if (!isValidModelId(modelId) || changedVariables == null || changedVariables.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            JsonObject root = readRoot();
            JsonObject models = getOrCreateObject(root, MODELS);
            JsonObject model = getOrCreateObject(models, modelId);
            for (Int2FloatMap.Entry entry : changedVariables.int2FloatEntrySet()) {
                String variableName = StringPool.getString(entry.getIntKey());
                float value = entry.getFloatValue();
                if (isValidVariableName(variableName) && Float.isFinite(value)) {
                    model.addProperty(variableName, value);
                }
            }
            writeRoot(root);
        }
    }

    private static JsonObject readModels() {
        return getOrCreateObject(readRoot(), MODELS);
    }

    private static JsonObject readRoot() {
        if (!Files.exists(FILE)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
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

    private static void writeRoot(JsonObject root) {
        try {
            Path parent = FILE.getParent();
            Files.createDirectories(parent);
            Path temp = parent.resolve("local_model_settings.json.tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
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
