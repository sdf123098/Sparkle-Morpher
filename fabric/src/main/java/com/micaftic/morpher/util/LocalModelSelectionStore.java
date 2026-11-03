package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端本地模型选择持久化存储。
 * <p>
 * 在无 YSM 模组的服务器（如 Hypixel）上，保存玩家选择的模型到本地文件，
 * 以便在切换服务器或重新加入时自动恢复，而不是被重置为 default。
 * <p>
 * 存储路径：config/sparkle_morpher/local_model_selection.json
 */
public final class LocalModelSelectionStore {

    private static final String MODEL_ID = "model_id";
    private static final String TEXTURE_ID = "texture_id";

    private LocalModelSelectionStore() {}

    /**
     * 保存本地玩家的模型选择到文件。
     * modelId 为 "default" 时清除存储（表示玩家选择了默认模型，无需恢复）。
     */
    public static void save(@Nullable String modelId, @Nullable String textureId) {
        try {
            save(file(), modelId, textureId);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save local model selection; existing data was preserved: {}", e.getMessage());
        }
    }

    public static void save(Path file, @Nullable String modelId, @Nullable String textureId) throws IOException {
        if (modelId == null || modelId.equals("default") || modelId.isBlank()) {
            Files.deleteIfExists(file); // An explicit default/clear selection removes the persisted choice.
            return;
        }
        if (!validId(modelId, 512)) throw new IOException("Invalid local model selection ID");
        String savedTexture = textureId == null || textureId.isBlank() ? "default" : textureId;
        if (!validId(savedTexture, 256)) throw new IOException("Invalid local model selection texture ID");
        JsonObject json = new JsonObject();
        json.addProperty(MODEL_ID, modelId);
        json.addProperty(TEXTURE_ID, savedTexture);
        com.micaftic.morpher.core.storage.LocalJsonDocumentStore.saveObject(file, json);
    }

    /**
     * 从文件加载本地玩家的模型选择。
     * 如果文件不存在或内容无效，返回 null。
     */
    @Nullable
    public static Pair<String, String> load() {
        try {
            return load(file());
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load local model selection; original file retained: {}", e.getMessage());
            return null;
        }
    }

    public static Pair<String, String> load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        JsonObject json = com.micaftic.morpher.core.storage.LocalJsonDocumentStore.readObject(file);
        String modelId = json.has(MODEL_ID) && json.get(MODEL_ID).isJsonPrimitive() && json.get(MODEL_ID).getAsJsonPrimitive().isString()
                ? json.get(MODEL_ID).getAsString() : null;
        String textureId = json.has(TEXTURE_ID) && json.get(TEXTURE_ID).isJsonPrimitive() && json.get(TEXTURE_ID).getAsJsonPrimitive().isString()
                ? json.get(TEXTURE_ID).getAsString() : "default";
        if (!validId(modelId, 512) || modelId.equals("default") || !validId(textureId, 256))
            throw new IOException("Invalid local model selection; original file retained");
        return Pair.of(modelId, textureId);
    }

    /**
     * 清除存储的模型选择。
     */
    public static void clear() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to clear local model selection: {}", e.getMessage());
        }
    }

    private static boolean validId(@Nullable String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("local_model_selection.json");
    }
}
