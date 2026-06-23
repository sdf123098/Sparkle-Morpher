package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 客户端本地模型选择持久化存储。
 * <p>
 * 在无 YSM 模组的服务器（如 Hypixel）上，保存玩家选择的模型到本地文件，
 * 以便在切换服务器或重新加入时自动恢复，而不是被重置为 default。
 * <p>
 * 存储路径：config/sparkle_morpher/local_model_selection.json
 */
public final class LocalModelSelectionStore {

    private static final Path FILE = Platform.getConfigFolder()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("local_model_selection.json");
    private static final String MODEL_ID = "model_id";
    private static final String TEXTURE_ID = "texture_id";

    private LocalModelSelectionStore() {}

    /**
     * 保存本地玩家的模型选择到文件。
     * modelId 为 "default" 时清除存储（表示玩家选择了默认模型，无需恢复）。
     */
    public static void save(@Nullable String modelId, @Nullable String textureId) {
        try {
            if (modelId == null || modelId.equals("default") || modelId.isBlank()) {
                // 玩家选择了 default 或清空了选择，删除文件
                if (Files.exists(FILE)) {
                    Files.deleteIfExists(FILE);
                }
                return;
            }
            JsonObject json = new JsonObject();
            json.addProperty(MODEL_ID, modelId);
            json.addProperty(TEXTURE_ID, textureId != null ? textureId : "default");
            Path parent = FILE.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            // 先写入临时文件再原子替换，避免写入中断导致文件损坏
            Path temp = parent.resolve("local_model_selection.json.tmp");
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            Files.move(temp, FILE, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save local model selection: {}", e.getMessage());
        }
    }

    /**
     * 从文件加载本地玩家的模型选择。
     * 如果文件不存在或内容无效，返回 null。
     */
    @Nullable
    public static Pair<String, String> load() {
        if (!Files.exists(FILE)) {
            return null;
        }
        try {
            String content = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            String modelId = json.has(MODEL_ID) ? json.get(MODEL_ID).getAsString() : null;
            String textureId = json.has(TEXTURE_ID) ? json.get(TEXTURE_ID).getAsString() : "default";
            if (modelId == null || modelId.isBlank() || modelId.equals("default")) {
                return null;
            }
            return Pair.of(modelId, textureId);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load local model selection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清除存储的模型选择。
     */
    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to clear local model selection: {}", e.getMessage());
        }
    }
}
