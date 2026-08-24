package com.micaftic.morpher.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class LocalStarModelsStore {
    private static final Path FILE = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("local_star_models.json");
    private static final String GLOBAL_KEY = "global";
    private static final String MODELS = "models";

    private LocalStarModelsStore() {}

    public static Set<String> load() {
        JsonObject root = readRoot();
        JsonObject entry = getEntry(root, currentScopeKey());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (entry != null && entry.has(MODELS) && entry.get(MODELS).isJsonArray()) {
            for (var element : entry.getAsJsonArray(MODELS)) {
                if (element.isJsonPrimitive()) {
                    String modelId = element.getAsString();
                    if (isValidModelId(modelId)) {
                        result.add(modelId);
                    }
                }
            }
        }
        return result;
    }

    public static void save(Set<String> models) {
        JsonObject root = readRoot();
        String key = currentScopeKey();
        if (models == null || models.isEmpty()) {
            root.remove(key);
        } else {
            JsonObject entry = new JsonObject();
            JsonArray array = new JsonArray();
            models.stream()
                    .filter(LocalStarModelsStore::isValidModelId)
                    .sorted()
                    .forEach(array::add);
            entry.add(MODELS, array);
            root.add(key, entry);
        }
        writeRoot(root);
    }

    public static void add(String modelId) {
        if (!isValidModelId(modelId)) {
            return;
        }
        Set<String> models = load();
        if (models.add(modelId)) {
            save(models);
        }
    }

    public static void remove(String modelId) {
        if (!isValidModelId(modelId)) {
            return;
        }
        Set<String> models = load();
        if (models.remove(modelId)) {
            save(models);
        }
    }

    private static boolean isValidModelId(@Nullable String modelId) {
        return modelId != null && !modelId.isBlank();
    }

    private static JsonObject readRoot() {
        if (!Files.exists(FILE)) {
            return new JsonObject();
        }
        try {
            String content = Files.readString(FILE, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load local star models: {}", e.getMessage());
            return new JsonObject();
        }
    }

    @Nullable
    private static JsonObject getEntry(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject(key);
    }

    private static void writeRoot(JsonObject root) {
        try {
            Path parent = FILE.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path temp = parent.resolve("local_star_models.json.tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save local star models: {}", e.getMessage());
        }
    }

    private static String currentScopeKey() {
        Minecraft minecraft = Minecraft.getInstance();
        String serverKey = GLOBAL_KEY;
        ServerData serverData = minecraft.getConnection() == null ? null : minecraft.getConnection().getServerData();
        if (serverData == null) {
            serverData = minecraft.getCurrentServer();
        }
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            serverKey = serverData.ip.trim().toLowerCase(java.util.Locale.ROOT);
        } else if (minecraft.isLocalServer()) {
            serverKey = "singleplayer";
        }
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        String playerKey = playerId == null ? "unknown" : playerId.toString();
        return sanitize(serverKey) + "|" + playerKey;
    }

    private static String sanitize(String value) {
        return value.replace('\\', '_').replace('/', '_');
    }
}
