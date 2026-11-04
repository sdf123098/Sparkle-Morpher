package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.storage.LocalStarModelsDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

public final class LocalStarModelsStore {
    private static final Path FILE = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("local_star_models.json");
    private static final Path LEGACY_FILE = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(YesSteveModel.MOD_ID)
            .resolve("local_starred_models.json");

    private LocalStarModelsStore() {}

    public static Set<String> load() {
        String scope = currentScopeKey();
        if (scope == null) return Set.of();
        try {
            return LocalStarModelsDocument.load(FILE, LEGACY_FILE, scope);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to load local star models: {}", e.getMessage());
            return Set.of();
        }
    }

    public static boolean save(Set<String> models) {
        String scope = currentScopeKey();
        if (scope == null) return false;
        try {
            LocalStarModelsDocument.save(FILE, LEGACY_FILE, scope, models);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save local star models: {}", e.getMessage());
            return false;
        }
    }

    public static boolean add(String modelId) {
        if (!isValidModelId(modelId)) {
            return false;
        }
        Set<String> models = load();
        if (!models.add(modelId)) return true;
        return save(models);
    }

    public static boolean remove(String modelId) {
        if (!isValidModelId(modelId)) {
            return false;
        }
        Set<String> models = load();
        if (!models.remove(modelId)) return true;
        return save(models);
    }

    public static boolean toggle(String modelId) {
        if (!isValidModelId(modelId) || currentScopeKey() == null) return false;
        Set<String> models = load();
        if (!models.remove(modelId)) models.add(modelId);
        return save(models);
    }

    private static boolean isValidModelId(@Nullable String modelId) {
        return modelId != null && !modelId.isBlank();
    }

    @Nullable
    private static String currentScopeKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return null;
        String serverKey;
        ServerData serverData = minecraft.getConnection() == null ? null : minecraft.getConnection().getServerData();
        if (serverData == null) {
            serverData = minecraft.getCurrentServer();
        }
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            serverKey = serverData.ip.trim().toLowerCase(java.util.Locale.ROOT);
        } else if (minecraft.isLocalServer()) {
            serverKey = "singleplayer";
        } else {
            return null;
        }
        UUID playerId = minecraft.player.getUUID();
        return sanitize(serverKey) + "|" + playerId;
    }

    private static String sanitize(String value) {
        return value.replace('\\', '_').replace('/', '_');
    }
}
