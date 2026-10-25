package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread local appearance choices, independent of Cloud login and privacy mode. */
public final class LocalMaidModelSync {
    private record Applied(Entity entity, LocalMaidModelStore.Selection selection) {}
    private static final Map<UUID, Applied> APPLIED = new HashMap<>();
    private static LocalMaidModelStore store;
    private static String world;
    private static Object level;
    private static boolean reportedLoadFailure;
    private LocalMaidModelSync() {}

    private static LocalMaidModelStore store() {
        if (store == null) store = new LocalMaidModelStore(ModelStoragePaths.folder().resolve("local_maid_models.json"));
        return store;
    }

    public static void select(String worldKey, UUID id, String model, String texture) throws IOException {
        var client = Minecraft.getInstance();
        if (worldKey == null || client.level == null || client.player == null)
            throw new IllegalStateException("No active world");
        Entity entity = find(id);
        if (entity == null || !TouhouLittleMaidCompat.isMaidOwnedBy(entity, client.player))
            throw new IllegalStateException("Only your own loaded maid can be changed");
        var cap = MaidCapability.get(entity).orElseThrow(() -> new IllegalStateException("Maid unavailable"));
        var selection = new LocalMaidModelStore.Selection(model, texture);
        store().save(worldKey, id, selection);
        context(worldKey);
        cap.applyLocalState(selection.modelId(), selection.textureId());
        APPLIED.put(id, new Applied(entity, selection));
    }

    public static void forget(String worldKey, UUID id) throws IOException {
        store().remove(worldKey, id);
        APPLIED.remove(id);
        Entity entity = find(id);
        if (entity != null) MaidCapability.get(entity).ifPresent(MaidCapability::clearLocalState);
    }

    private static void context(String worldKey) {
        var client = Minecraft.getInstance();
        if (!Objects.equals(world, worldKey) || level != client.level) {
            APPLIED.clear(); world = worldKey; level = client.level; reportedLoadFailure = false;
        }
    }

    public static void tick(String worldKey) {
        context(worldKey);
        var client = Minecraft.getInstance();
        if (worldKey == null || client.level == null) return;
        var visible = new HashSet<UUID>();
        try {
            for (Entity entity : client.level.entitiesForRendering()) {
                var capability = MaidCapability.get(entity);
                if (capability.isEmpty()) continue;
                UUID id = entity.getUUID(); visible.add(id);
                var selection = store().load(worldKey, id);
                if (selection == null) continue;
                var previous = APPLIED.get(id);
                if (previous == null || previous.entity() != entity || !previous.selection().equals(selection)) {
                    capability.get().applyLocalState(selection.modelId(), selection.textureId());
                    APPLIED.put(id, new Applied(entity, selection));
                }
            }
            APPLIED.keySet().retainAll(visible);
        } catch (IOException error) {
            if (!reportedLoadFailure) YesSteveModel.LOGGER.warn("Could not restore local maid models", error);
            reportedLoadFailure = true;
        }
    }

    public static Map<UUID,LocalMaidModelStore.Selection> visibleSelections() {
        var result=new HashMap<UUID,LocalMaidModelStore.Selection>();APPLIED.forEach((id,value)->result.put(id,value.selection()));return Map.copyOf(result);
    }

    private static Entity find(UUID id) {
        var client = Minecraft.getInstance();
        if (client.level != null) for (Entity entity : client.level.entitiesForRendering())
            if (entity.getUUID().equals(id)) return entity;
        return null;
    }
}
