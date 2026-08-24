package com.micaftic.morpher.client.animation.custom;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义轮盘布局的持久化存储。
 * <p>
 * 每个模型 ID 有独立的配置文件，路径为：
 * config/sparkle_morpher/custom_roulette/<modelId>.json
 * <p>
 * 原子写入（先写临时文件再替换），与 LocalModelSelectionStore 同模式。
 */
public final class CustomRouletteStore {

    private static final Path BASE_DIR = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("custom_roulette");
    private static final String MODEL_ID = "model_id";
    private static final String ROOT_ENTRIES = "root_entries";
    private static final String GROUPS = "groups";
    private static final String NAME = "name";
    private static final String ANIMATIONS = "animations";
    private static final String KEY = "key";
    private static final String CATEGORY = "category";
    private static final String ORIGINAL_INDEX = "original_index";
    private static final String DISPLAY_LABEL = "display_label";

    // --- UI 数据构建方法 ---
    // 供 UnifiedRouletteScreen 调用

    /**
     * 从自定义布局构建根级 OrderedStringMap。
     * 包含根级动画条目 + 子菜单导航链接（#groupName 格式）。
     */
    public static OrderedStringMap<String, String> buildRootMap(CustomRouletteLayout layout) {
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (CustomRouletteEntry entry : layout.rootEntries()) {
            keys.add(entry.key());
            values.add(entry.displayLabel());
        }
        for (CustomRouletteGroup group : layout.groups()) {
            keys.add("#" + group.name());
            values.add("#" + group.name());
        }
        if (keys.isEmpty()) {
            keys.add("");
            values.add("");
        }
        return new OrderedStringMap<>(keys.toArray(new String[0]), values.toArray(new String[0]));
    }

    /**
     * 从自定义布局构建子菜单分类 Map。
     * 每个分组名映射到该分组的 OrderedStringMap。
     */
    public static Map<String, OrderedStringMap<String, String>> buildClassifyMap(CustomRouletteLayout layout) {
        Map<String, OrderedStringMap<String, String>> map = new LinkedHashMap<>();
        for (CustomRouletteGroup group : layout.groups()) {
            List<String> keys = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (CustomRouletteEntry entry : group.animations()) {
                keys.add(entry.key());
                values.add(entry.displayLabel());
            }
            if (!keys.isEmpty()) {
                map.put(group.name(), new OrderedStringMap<>(keys.toArray(new String[0]), values.toArray(new String[0])));
            }
        }
        return map;
    }

    /**
     * 构建动画名→原始索引的映射（用于在线模式 C2SPlayAnimationPacket）。
     */
    public static Map<String, Integer> buildIndexMap(CustomRouletteLayout layout) {
        Map<String, Integer> map = new HashMap<>();
        for (CustomRouletteEntry entry : layout.rootEntries()) {
            map.put(entry.key(), entry.originalIndex());
        }
        for (CustomRouletteGroup group : layout.groups()) {
            for (CustomRouletteEntry entry : group.animations()) {
                map.put(entry.key(), entry.originalIndex());
            }
        }
        return map;
    }

    /**
     * 构建动画名→原始类别的映射（用于在线模式 C2SPlayAnimationPacket）。
     */
    public static Map<String, String> buildCategoryMap(CustomRouletteLayout layout) {
        Map<String, String> map = new HashMap<>();
        for (CustomRouletteEntry entry : layout.rootEntries()) {
            map.put(entry.key(), entry.category());
        }
        for (CustomRouletteGroup group : layout.groups()) {
            for (CustomRouletteEntry entry : group.animations()) {
                map.put(entry.key(), entry.category());
            }
        }
        return map;
    }

    private CustomRouletteStore() {}

    /**
     * 保存自定义轮盘布局到文件。
     */
    public static void save(CustomRouletteLayout layout) {
        try {
            Path file = getFile(layout.modelId());
            Path parent = file.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String json = serialize(layout).toString();
            Path temp = parent.resolve(layout.modelId() + ".json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // 文件系统不支持原子移动，回退到普通替换
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save custom roulette layout for {}: {}", layout.modelId(), e.getMessage());
        }
    }

    /**
     * 加载指定模型的自定义轮盘布局。
     * 如果文件不存在或内容无效，返回 null（此时应 fallback 到原始布局）。
     */
    @Nullable
    public static CustomRouletteLayout load(String modelId) {
        Path file = getFile(modelId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return deserialize(json);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load custom roulette layout for {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除指定模型的自定义轮盘配置。
     */
    public static void delete(String modelId) {
        try {
            Files.deleteIfExists(getFile(modelId));
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to delete custom roulette layout for {}: {}", modelId, e.getMessage());
        }
    }

    /**
     * 列出所有已有自定义配置的模型 ID。
     */
    public static List<String> listModelIds() {
        List<String> ids = new ArrayList<>();
        if (!Files.exists(BASE_DIR)) {
            return ids;
        }
        try {
            Files.list(BASE_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        ids.add(name.substring(0, name.length() - 5));
                    });
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to list custom roulette layouts: {}", e.getMessage());
        }
        return ids;
    }

    private static Path getFile(String modelId) {
        return BASE_DIR.resolve(modelId + ".json");
    }

    // --- 序列化 ---
    private static JsonObject serialize(CustomRouletteLayout layout) {
        JsonObject json = new JsonObject();
        json.addProperty(MODEL_ID, layout.modelId());
        json.add(ROOT_ENTRIES, serializeEntries(layout.rootEntries()));
        JsonArray groupsArr = new JsonArray();
        for (CustomRouletteGroup group : layout.groups()) {
            JsonObject groupObj = new JsonObject();
            groupObj.addProperty(NAME, group.name());
            groupObj.add(ANIMATIONS, serializeEntries(group.animations()));
            groupsArr.add(groupObj);
        }
        json.add(GROUPS, groupsArr);
        return json;
    }

    private static JsonArray serializeEntries(List<CustomRouletteEntry> entries) {
        JsonArray arr = new JsonArray();
        for (CustomRouletteEntry entry : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty(KEY, entry.key());
            obj.addProperty(CATEGORY, entry.category());
            obj.addProperty(ORIGINAL_INDEX, entry.originalIndex());
            obj.addProperty(DISPLAY_LABEL, entry.displayLabel());
            arr.add(obj);
        }
        return arr;
    }

    // --- 反序列化 ---
    private static CustomRouletteLayout deserialize(JsonObject json) {
        String modelId = json.has(MODEL_ID) ? json.get(MODEL_ID).getAsString() : "";
        List<CustomRouletteEntry> rootEntries = deserializeEntries(json.getAsJsonArray(ROOT_ENTRIES));
        List<CustomRouletteGroup> groups = new ArrayList<>();
        if (json.has(GROUPS)) {
            for (JsonElement elem : json.getAsJsonArray(GROUPS)) {
                JsonObject groupObj = elem.getAsJsonObject();
                String name = groupObj.has(NAME) ? groupObj.get(NAME).getAsString() : "";
                List<CustomRouletteEntry> animations = deserializeEntries(groupObj.getAsJsonArray(ANIMATIONS));
                groups.add(new CustomRouletteGroup(name, animations));
            }
        }
        return new CustomRouletteLayout(modelId, rootEntries, groups);
    }

    private static List<CustomRouletteEntry> deserializeEntries(JsonArray arr) {
        List<CustomRouletteEntry> entries = new ArrayList<>();
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String key = obj.has(KEY) ? obj.get(KEY).getAsString() : "";
            String category = obj.has(CATEGORY) ? obj.get(CATEGORY).getAsString() : "";
            int originalIndex = obj.has(ORIGINAL_INDEX) ? obj.get(ORIGINAL_INDEX).getAsInt() : 0;
            String displayLabel = obj.has(DISPLAY_LABEL) ? obj.get(DISPLAY_LABEL).getAsString() : key;
            entries.add(new CustomRouletteEntry(key, category, originalIndex, displayLabel));
        }
        return entries;
    }
}
