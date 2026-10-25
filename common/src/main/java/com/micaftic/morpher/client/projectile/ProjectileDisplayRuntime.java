package com.micaftic.morpher.client.projectile;

import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.RuntimeModelLease;
import com.micaftic.morpher.cloud.client.CloudPlayerSelection;
import com.micaftic.morpher.cloud.client.EntityDisplayContext;
import com.micaftic.morpher.cloud.client.ProjectileFiringCandidates;
import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Local projectile appearance authority. World lifetime alone clears frozen snapshots;
 * Cloud login, scope changes and network failures never touch this state.
 */
public final class ProjectileDisplayRuntime {
    public enum Source { LOCAL, REMOTE }

    private record Input(ProjectileFiringCandidates.Firing firing, String identityId, long deadlineNanos) {}
    private record Applied(Projectile entity, ProjectileCapability capability, ProjectileModelRef model,
                           Map<String, Float> variables, String firingItemId, CloudPlayerSelection selection,
                           Source source) {}

    private static final long GROUND_CHECK_DELAY_NANOS = 100_000_000L;
    private static final long INPUT_LIFETIME_NANOS = 1_500_000_000L;
    private static final int MAX_LOCAL = 4096;
    private static final int MAX_INPUTS = 64;

    private final ProjectileFiringCandidates candidates = new ProjectileFiringCandidates();
    private final Map<UUID, ProjectileDisplaySnapshot> local = new HashMap<>();
    private final Map<String, Input> inputs = new HashMap<>();
    private final Set<UUID> checked = new HashSet<>();
    private final Map<UUID, Applied> applied = new HashMap<>();
    private final Map<UUID, RuntimeModelLease> localLeases = new HashMap<>();
    private final Map<UUID, RuntimeModelLease> remoteLeases = new HashMap<>();
    private Object level, connection;
    private EntityDisplayContext context;
    private long worldGeneration;
    private boolean inWorld;

    public EntityDisplayContext context() { return context; }
    public long worldGeneration() { return worldGeneration; }
    public ProjectileDisplaySnapshot local(UUID entityUuid) { return local.get(entityUuid); }
    public boolean isLocal(UUID entityUuid) { return local.containsKey(entityUuid); }

    public void enterWorld(Object level, Object connection, String dimensionId) {
        if (inWorld && this.level == level && this.connection == connection && context != null
                && context.dimensionId().equals(dimensionId)) {
            return;
        }
        clearLocalState();
        this.inWorld = level != null;
        this.level = level;
        this.connection = connection;
        if (!inWorld) {
            context = null;
            candidates.enter(null);
            return;
        }
        worldGeneration++;
        context = new EntityDisplayContext("local", "0".repeat(64), "local",
                "world-" + worldGeneration, dimensionId, worldGeneration);
        candidates.enter(context);
    }

    public void leaveWorld() {
        if (!inWorld && context == null) return;
        clearLocalState();
        inWorld = false;
        level = null;
        connection = null;
        context = null;
        candidates.enter(null);
    }

    public boolean offer(EntityDisplayContext expected, ProjectileFiringCandidates.Firing firing,
            String identityId, long nowNanos) {
        if (context == null || !context.equals(expected)) return false;
        inputs.entrySet().removeIf(entry -> nowNanos - entry.getValue().deadlineNanos >= 0);
        if (inputs.size() >= MAX_INPUTS || !candidates.offer(expected, firing, nowNanos)) return false;
        inputs.put(firing.eventId(), new Input(firing, identityId, nowNanos + INPUT_LIFETIME_NANOS));
        return true;
    }

    public boolean nativeSpawn(EntityDisplayContext expected, Projectile projectile, long nowNanos) {
        if (context == null || !context.equals(expected)) return false;
        ProjectileFiringCandidates.Family family = family(projectile);
        if (family == null) {
            candidates.remember(projectile.getUUID());
            return false;
        }
        Entity owner = projectile.getOwner();
        var position = projectile.position();
        var velocity = projectile.getDeltaMovement();
        ProjectileFiringCandidates.Firing firing = candidates.match(expected, projectile.getUUID(),
                owner == null ? null : owner.getUUID(), family, position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z, nowNanos);
        if (firing == null || local.size() >= MAX_LOCAL) return false;
        var assembly = ClientModelManager.getModelContext(firing.runtimeModelId()).orElse(null);
        if (assembly == null) return false;
        String bundle = kind(projectile);
        if (!assembly.getProjectileModels().containsKey(ResourceApi.parseNative(bundle))) {
            if (projectile instanceof AbstractArrow
                    && assembly.getProjectileModels().containsKey(ResourceApi.parseNative("minecraft:arrow"))) {
                bundle = "minecraft:arrow";
            } else {
                return false;
            }
        }
        Input input = inputs.get(firing.eventId());
        if (input == null || nowNanos - input.deadlineNanos >= 0) return false;
        local.put(projectile.getUUID(), new ProjectileDisplaySnapshot(projectile.getUUID(), firing.ownerUuid(),
                kind(projectile), new ProjectileModelRef(firing.runtimeModelId(), bundle), firing.variables(),
                firing.firingItemId(), firing.eventId(), firing.selection(), input.identityId(),
                worldGeneration, nowNanos));
        retainLocalLease(projectile.getUUID(), firing.runtimeModelId());
        return true;
    }

    public void tick(Map<UUID, Projectile> loaded, long nowNanos) {
        for (UUID id : List.copyOf(applied.keySet())) {
            if (!loaded.containsKey(id)) clear(id);
        }
        for (UUID id : List.copyOf(local.keySet())) {
            if (!loaded.containsKey(id)) clear(id);
        }
        checked.retainAll(local.keySet());
        inputs.entrySet().removeIf(entry -> nowNanos - entry.getValue().deadlineNanos >= 0);
        for (Map.Entry<UUID, Projectile> entry : loaded.entrySet()) {
            ProjectileDisplaySnapshot snapshot = local.get(entry.getKey());
            if (snapshot == null) continue;
            if (nowNanos - snapshot.observedAtNanos() < GROUND_CHECK_DELAY_NANOS) continue;
            Projectile entity = entry.getValue();
            Entity owner = entity.getOwner();
            boolean grounded = !checked.contains(entry.getKey())
                    && entity instanceof ProjectileStateAccessor state && state.isInGround();
            if (owner == null || !owner.getUUID().equals(snapshot.ownerUuid()) || grounded) {
                local.remove(entry.getKey());
                checked.remove(entry.getKey());
                releaseLocal(entry.getKey());
                closeLocalLease(entry.getKey());
                continue;
            }
            checked.add(entry.getKey());
            applyLocal(entity, snapshot);
        }
    }

    public void applyRemote(UUID entityUuid, Projectile entity, String runtimeModelId, String bundleKey,
            Map<String, Float> variables, String firingItemId, CloudPlayerSelection selection) {
        if (local.containsKey(entityUuid)) return;
        Applied old = applied.get(entityUuid);
        if (old != null && old.source == Source.LOCAL) return;
        ProjectileCapability capability = ProjectileCapability.get(entity).orElse(null);
        if (capability == null) return;
        ProjectileModelRef model = new ProjectileModelRef(runtimeModelId, bundleKey);
        Map<String, Float> frozen = Map.copyOf(variables);
        if (old != null && old.entity == entity && old.capability == capability && old.model.equals(model)
                && old.variables.equals(frozen) && Objects.equals(old.firingItemId, firingItemId)) {
            return;
        }
        closeRemoteLease(entityUuid);
        capability.awaitAsyncResult();
        capability.setDisplayBundleKey(bundleKey);
        capability.updateModelId(runtimeModelId);
        var values = new Int2FloatOpenHashMap();
        frozen.forEach((name, value) -> values.put(StringPool.computeIfAbsent(name), value.floatValue()));
        capability.setFloatProperties(values);
        if (entity instanceof ProjectileStateAccessor state) state.ysm$setDisplayFiringItem(firingItemId);
        remoteLeases.put(entityUuid, ClientModelManager.acquireRuntimeModelLease(runtimeModelId));
        applied.put(entityUuid, new Applied(entity, capability, model, frozen, firingItemId, selection, Source.REMOTE));
    }

    public void releaseRemote(UUID entityUuid) {
        Applied old = applied.get(entityUuid);
        if (old != null && old.source == Source.REMOTE) clearApplied(entityUuid);
    }

    public void releaseAllRemote() {
        for (UUID id : List.copyOf(applied.keySet())) releaseRemote(id);
    }

    public void clear(UUID entityUuid) {
        clearApplied(entityUuid);
        local.remove(entityUuid);
        checked.remove(entityUuid);
        closeLocalLease(entityUuid);
    }

    public boolean hasAppliedSelection(CloudPlayerSelection selection) {
        for (Applied value : applied.values()) {
            if (selection.equals(value.selection)) return true;
        }
        for (ProjectileDisplaySnapshot snapshot : local.values()) {
            if (selection.equals(snapshot.selection())) return true;
        }
        return false;
    }

    public static String kind(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public static ProjectileFiringCandidates.Family family(Projectile entity) {
        return switch (kind(entity)) {
            case "minecraft:trident" -> ProjectileFiringCandidates.Family.TRIDENT;
            case "minecraft:snowball" -> ProjectileFiringCandidates.Family.SNOWBALL;
            case "minecraft:egg" -> ProjectileFiringCandidates.Family.EGG;
            case "minecraft:firework_rocket" -> ProjectileFiringCandidates.Family.FIREWORK;
            default -> entity instanceof AbstractArrow ? ProjectileFiringCandidates.Family.ARROW : null;
        };
    }

    private void applyLocal(Projectile entity, ProjectileDisplaySnapshot snapshot) {
        ProjectileCapability capability = ProjectileCapability.get(entity).orElse(null);
        if (capability == null) return;
        Applied old = applied.get(snapshot.entityUuid());
        if (old != null && old.source == Source.LOCAL && old.entity == entity && old.capability == capability
                && old.model.equals(snapshot.model()) && old.variables.equals(snapshot.variables())
                && Objects.equals(old.firingItemId, snapshot.firingItemId())) {
            return;
        }
        if (old != null && old.source == Source.REMOTE) closeRemoteLease(snapshot.entityUuid());
        capability.awaitAsyncResult();
        capability.setDisplayBundleKey(snapshot.model().bundleKey());
        capability.updateModelId(snapshot.model().runtimeModelId());
        var values = new Int2FloatOpenHashMap();
        snapshot.variables().forEach((name, value) -> values.put(StringPool.computeIfAbsent(name), value.floatValue()));
        capability.setFloatProperties(values);
        if (entity instanceof ProjectileStateAccessor state) state.ysm$setDisplayFiringItem(snapshot.firingItemId());
        retainLocalLease(snapshot.entityUuid(), snapshot.model().runtimeModelId());
        applied.put(snapshot.entityUuid(), new Applied(entity, capability, snapshot.model(), snapshot.variables(),
                snapshot.firingItemId(), snapshot.selection(), Source.LOCAL));
    }

    private void releaseLocal(UUID entityUuid) {
        Applied old = applied.get(entityUuid);
        if (old != null && old.source == Source.LOCAL) clearApplied(entityUuid);
    }

    private void clearApplied(UUID entityUuid) {
        Applied old = applied.remove(entityUuid);
        if (old == null) return;
        old.capability.awaitAsyncResult();
        old.capability.resetModel();
        old.capability.setFloatProperties(null);
        old.capability.setDisplayBundleKey(null);
        if (old.entity instanceof ProjectileStateAccessor state) state.ysm$setDisplayFiringItem(null);
        if (old.source == Source.LOCAL) closeLocalLease(entityUuid);
        else closeRemoteLease(entityUuid);
    }

    private void retainLocalLease(UUID entityUuid, String runtimeModelId) {
        String key = LocalModelCatalog.canonicalKey(runtimeModelId);
        RuntimeModelLease lease = localLeases.get(entityUuid);
        if (lease != null && lease.isValid() && lease.runtimeModelId().equals(key)) return;
        if (lease != null) lease.close();
        localLeases.put(entityUuid, ClientModelManager.acquireRuntimeModelLease(runtimeModelId));
    }

    private void closeLocalLease(UUID entityUuid) {
        RuntimeModelLease lease = localLeases.remove(entityUuid);
        if (lease != null) lease.close();
    }

    private void closeRemoteLease(UUID entityUuid) {
        RuntimeModelLease lease = remoteLeases.remove(entityUuid);
        if (lease != null) lease.close();
    }

    private void clearLocalState() {
        for (UUID id : List.copyOf(applied.keySet())) clear(id);
        for (UUID id : List.copyOf(localLeases.keySet())) closeLocalLease(id);
        for (UUID id : List.copyOf(remoteLeases.keySet())) closeRemoteLease(id);
        local.clear();
        inputs.clear();
        checked.clear();
    }
}
