package com.micaftic.morpher.client.peer;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.RuntimeModelLease;
import com.micaftic.morpher.client.compat.touhoulittlemaid.LocalMaidModelSync;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.network.NetworkAccess;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.peer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** P2P maid appearances use the existing verified resource transport, never Cloud or custom imports. */
public final class PeerMaidModelSync {
    private record Applied(Entity entity, PeerEntityAppearance appearance, String model,
            PeerAssetStore.Lease asset, RuntimeModelLease runtime) {}
    private static final Map<UUID, Applied> APPLIED = new HashMap<>();
    private static final Map<UUID, PeerEntityAppearance> PENDING = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, String> SENT = new HashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>(), SEND_RETRY = new HashMap<>();
    private static volatile Map<UUID, List<PeerEntityAppearance>> catalog = Map.of();
    private static volatile Map<UUID, Entity> visible = Map.of();
    private static volatile Map<UUID, UUID> owners = Map.of();
    private static volatile Set<UUID> eligible = Set.of();
    private static volatile PeerRoomRuntime runtime;
    private static volatile Object level;
    private PeerMaidModelSync() {}

    public static void receive(List<PeerEntityAppearance> entries) {
        var next = new HashMap<UUID, List<PeerEntityAppearance>>();
        for (var entry : entries) if (!entry.removed()) next.computeIfAbsent(entry.entityId(), ignored -> new ArrayList<>()).add(entry);
        next.replaceAll((id, values) -> List.copyOf(values)); catalog = Map.copyOf(next);
    }

    public static void tick(PeerRoomRuntime active, boolean sharing) {
        var client = Minecraft.getInstance();
        if (active != runtime || client.level != level) { reset(); runtime = active; level = client.level; }
        if (client.level == null || client.player == null) return;
        var observed = new HashMap<UUID, Entity>(); var ownership = new HashMap<UUID, UUID>(); var eligibleIds = new HashSet<UUID>();
        for (Entity entity : client.level.entitiesForRendering()) MaidCapability.get(entity).ifPresent(cap -> {
            UUID id = entity.getUUID(); observed.put(id, entity); UUID owner = ownerUuid(entity);
            if (owner != null) ownership.put(id, owner);
            if (!cap.hasLocalState() && !(NetworkAccess.state().preset().prefersCloud() && cap.hasCloudState())) eligibleIds.add(id);
        });
        visible = Map.copyOf(observed); owners = Map.copyOf(ownership); eligible = Set.copyOf(eligibleIds);
        if (sharing) publish(active); else if (!SENT.isEmpty()) { active.clearEntities(); SENT.clear(); SEND_RETRY.clear(); }
        for (UUID id : List.copyOf(APPLIED.keySet())) if (!current(active, APPLIED.get(id).appearance(), APPLIED.get(id).entity())) release(id);
        for (var applied : APPLIED.values()) MaidCapability.get(applied.entity()).ifPresent(cap -> {
            if (!cap.isModelActive() || !Objects.equals(cap.getModelId(), applied.model())
                    || !Objects.equals(cap.getCurrentTextureName(), applied.appearance().texture()))
                cap.applyPeerState(applied.model(), applied.appearance().texture());
        });
        for (var row : catalog.entrySet()) {
            UUID id = row.getKey(); Entity entity = find(id);
            if (entity == null) continue;
            var choice = row.getValue().stream().filter(e -> Objects.equals(e.owner().minecraftUuid(), owners.get(id))).findFirst().orElse(null);
            if (choice == null || !current(active, choice, entity) || APPLIED.containsKey(id) || PENDING.containsKey(id)
                    || System.nanoTime() < RETRY.getOrDefault(id, 0L)) continue;
            if (choice.model() == null) { apply(choice, entity, "default", null); continue; }
            PENDING.put(id, choice);
            java.util.function.BooleanSupplier allowed = () -> PENDING.get(id) == choice && current(active, choice, entity);
            active.download(choice.assetOwner(), allowed, (asset, lease) -> {
                if (!allowed.getAsBoolean()) { lease.close(); PENDING.remove(id, choice); return; }
                ClientModelManager.loadPeerModel(choice.model(), asset, allowed, failure -> {
                    boolean accepted = failure == null && allowed.getAsBoolean(); PENDING.remove(id, choice);
                    if (accepted) apply(choice, entity, choice.model().runtimeModelId(), lease);
                    else { lease.close(); RETRY.put(id, System.nanoTime() + 3_000_000_000L); }
                });
            }, failure -> { if (PENDING.remove(id, choice)) RETRY.put(id, System.nanoTime() + 3_000_000_000L); });
        }
        // A superseded or departed publisher must not leave an old download blocking a new choice.
        PENDING.entrySet().removeIf(e -> { Entity entity = find(e.getKey()); return entity == null || !current(active, e.getValue(), entity); });
    }

    private static void publish(PeerRoomRuntime active) {
        var client = Minecraft.getInstance(); var desired = new HashSet<UUID>();
        for (var row : LocalMaidModelSync.visibleSelections().entrySet()) {
            UUID id = row.getKey(); Entity entity = find(id);
            if (entity == null || !client.player.getUUID().equals(owners.get(id))) continue;
            var selection = row.getValue(); desired.add(id);
            String signature = selection.modelId() + "\n" + selection.textureId() + "\n" + ClientModelManager.localSourceRevision(selection.modelId());
            if (signature.equals(SENT.get(id)) || System.nanoTime() < SEND_RETRY.getOrDefault(id, 0L)) continue;
            SENT.put(id, signature);
            java.util.function.Supplier<LocalShareSourceResolver.Selection> resolver = "default".equals(selection.modelId()) ? null : () -> {
                try { return LocalShareSourceResolver.resolve(ClientModelManager.MODEL_LIBRARY, selection.modelId(), ModelStoragePaths.custom(), true); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            };
            active.shareEntity(id, resolver, selection.textureId(), success -> {
                if (active != runtime || !signature.equals(SENT.get(id))) return;
                if (!success) { SENT.remove(id); SEND_RETRY.put(id, System.nanoTime() + 5_000_000_000L); }
            });
        }
        // Keep a known owned maid published while its owner walks out of tracking range.
        for (UUID id : List.copyOf(SENT.keySet())) if (!desired.contains(id) && visible.containsKey(id)) {
            active.clearEntity(id); SENT.remove(id); SEND_RETRY.remove(id);
        }
    }

    private static boolean current(PeerRoomRuntime active, PeerEntityAppearance entry, Entity entity) {
        if (runtime != active || active.isClosed() || PrivacyMode.isActive() || !NetworkAccess.allowsPeer()
                || visible.get(entry.entityId()) != entity || !Objects.equals(owners.get(entry.entityId()), entry.owner().minecraftUuid())
                || !catalog.getOrDefault(entry.entityId(), List.of()).contains(entry)) return false;
        return eligible.contains(entry.entityId());
    }

    private static void apply(PeerEntityAppearance entry, Entity entity, String model, PeerAssetStore.Lease asset) {
        var cap = MaidCapability.get(entity).orElse(null);
        if (level != Minecraft.getInstance().level || entity.isRemoved() || cap == null || cap.hasLocalState()
                || (NetworkAccess.state().preset().prefersCloud() && cap.hasCloudState())) { if (asset != null) asset.close(); return; }
        cap.applyPeerState(model, entry.texture());
        APPLIED.put(entry.entityId(), new Applied(entity, entry, model, asset,
                entry.model() == null ? null : ClientModelManager.acquireRuntimeModelLease(model)));
        YesSteveModel.LOGGER.info("[SPM Peer maid] Applied entity={} model={} texture={}", entry.entityId(), model, entry.texture());
    }

    private static void release(UUID id) {
        var previous = APPLIED.remove(id); if (previous == null) return;
        MaidCapability.get(previous.entity()).ifPresent(cap -> { if (Objects.equals(cap.getModelId(), previous.model())) cap.clearPeerState(); });
        if (previous.runtime() != null) previous.runtime().close(); if (previous.asset() != null) previous.asset().close();
        if (previous.appearance().model() != null && !ClientPeerRuntime.retainsPeerModel(previous.model())) ClientModelManager.releasePeerModel(previous.model());
    }
    public static boolean retainsModel(String model) {
        return APPLIED.values().stream().anyMatch(a -> a.model().equals(model))
                || PENDING.values().stream().anyMatch(a -> a.model() != null && a.model().runtimeModelId().equals(model));
    }
    public static void reset() {
        PENDING.clear(); for (UUID id : List.copyOf(APPLIED.keySet())) release(id);
        SENT.clear(); RETRY.clear(); SEND_RETRY.clear(); catalog = Map.of(); visible = Map.of(); owners = Map.of(); eligible = Set.of(); runtime = null; level = null;
    }

    private static Entity find(UUID id) {
        return visible.get(id);
    }
    static UUID ownerUuid(Object entity) {
        try { Object value = entity.getClass().getMethod("getOwnerUUID").invoke(entity); if (value instanceof UUID uuid) return uuid; }
        catch (ReflectiveOperationException | RuntimeException ignored) {}
        try {
            Object reference = entity.getClass().getMethod("getOwnerReference").invoke(entity);
            Object value = reference == null ? null : reference.getClass().getMethod("getUUID").invoke(reference);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
}
