package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.projectile.ProjectileDisplayRuntime;
import com.micaftic.morpher.client.projectile.ProjectileDisplaySnapshot;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.micaftic.morpher.core.network.NetworkAccess.allowsCloud;

/**
 * Cloud projectile adapter. Local appearance lives in {@link ProjectileDisplayRuntime};
 * this class only publishes, renews, polls and applies remote snapshots.
 */
public final class CloudProjectileModelSync {
    public record Ticket(EntityDisplayContext context, ProjectileFiringCandidates.Firing firing, String identityId) {}
    private static final ProjectileDisplayRuntime DISPLAY = new ProjectileDisplayRuntime();
    private static final ProjectileSnapshotLedger LEDGER = new ProjectileSnapshotLedger(4096);
    private static final Map<UUID, CloudProjectileSnapshotClient.Lease> OWN = new HashMap<>();
    private static final Map<UUID, Long> RENEW_AT = new HashMap<>();
    private static final Set<UUID> RENEWING = new HashSet<>();
    private static final Map<CloudPlayerSelection, CompletableFuture<String>> IMPORTS = new HashMap<>();
    private static final Map<CloudPlayerSelection, Long> RETRIES = new HashMap<>();
    private static CloudClientRuntime.RuntimeState runtime;
    private static Object level, connection;
    private static EntityDisplayContext sharedContext;
    private static long scopeGeneration, networkGeneration, nextPoll;
    private static boolean polling;

    private CloudProjectileModelSync() {}

    private static boolean shared() {
        return sharedContext != null && allowsCloud() && !PrivacyMode.isActive();
    }

    private static boolean current(EntityDisplayContext expected, CloudClientRuntime.RuntimeState session) {
        var client = Minecraft.getInstance();
        return expected != null && expected.equals(sharedContext) && runtime == session
                && client.level == level && client.getConnection() == connection
                && allowsCloud() && !PrivacyMode.isActive();
    }

    private static void ensureContext() {
        var client = Minecraft.getInstance();
        var currentLevel = client.level;
        var currentConnection = client.getConnection();
        if (currentLevel != level || currentConnection != connection) {
            resetNetwork();
            level = currentLevel;
            connection = currentConnection;
            if (currentLevel == null) {
                DISPLAY.leaveWorld();
                sharedContext = null;
                LEDGER.enter(null);
                return;
            }
            DISPLAY.enterWorld(currentLevel, currentConnection, currentLevel.dimension().identifier().toString());
            LEDGER.enter(null);
            nextPoll = 0;
        }
        var next = CloudClientRuntime.state();
        long nextScope = next == null ? 0 : next.scopeLifecycle().contextGeneration();
        if (next != runtime || nextScope != scopeGeneration) {
            resetNetwork();
            runtime = next;
            scopeGeneration = nextScope;
        }
        if (currentLevel == null) return;
        String scope = next == null ? null : next.scopeLifecycle().activeScopeId();
        String epoch = next == null ? null : next.scopeLifecycle().activeWorldEpoch();
        boolean capable = next != null && next.instanceInfo() != null
                && next.instanceInfo().supports("projectile_snapshot_v1")
                && allowsCloud() && !PrivacyMode.isActive() && scope != null && epoch != null;
        if (!capable) {
            if (sharedContext != null) {
                resetNetwork();
                sharedContext = null;
                LEDGER.enter(null);
            }
            return;
        }
        String dimension = currentLevel.dimension().identifier().toString();
        var candidate = new EntityDisplayContext(next.instance().instanceId(),
                CloudAssetCache.sha256(next.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),
                scope, epoch, dimension, networkGeneration);
        if (!candidate.equals(sharedContext)) {
            resetNetwork();
            sharedContext = new EntityDisplayContext(candidate.instanceId(), candidate.originSha256(),
                    candidate.scopeId(), candidate.worldEpoch(), candidate.dimensionId(), networkGeneration);
            LEDGER.enter(null);
            LEDGER.enter(sharedContext);
        }
    }

    private static void resetNetwork() {
        withdrawOwn();
        DISPLAY.releaseAllRemote();
        OWN.clear();
        RENEW_AT.clear();
        RENEWING.clear();
        IMPORTS.clear();
        RETRIES.clear();
        polling = false;
        nextPoll = 0;
        networkGeneration++;
        LEDGER.enter(null);
        if (sharedContext != null) LEDGER.enter(sharedContext);
    }

    private static void withdrawOwn() {
        if (sharedContext == null || runtime == null) return;
        try {
            var api = new CloudProjectileSnapshotClient(runtime.http(), runtime.instanceInfo());
            OWN.values().forEach(lease -> api.withdraw(sharedContext, lease).exceptionally(error -> null));
        } catch (RuntimeException error) {
            YesSteveModel.LOGGER.debug("[SPM projectile] failed to withdraw leases", error);
        }
    }

    /** Capture before native item use changes inventory. Commit only if the native use consumes the action. */
    public static Ticket captureUse(Player player, InteractionHand hand) {
        if (player != Minecraft.getInstance().player) return null;
        ItemStack item = player.getItemInHand(hand);
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        Set<ProjectileFiringCandidates.Family> families = new HashSet<>();
        int count = 1;
        if (id.equals("minecraft:crossbow")) {
            var charged = item.get(DataComponents.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) return null;
            var ammunition = charged.itemCopies().toList();
            count = ammunition.size();
            for (var ammo : ammunition) {
                String ammoId = BuiltInRegistries.ITEM.getKey(ammo.getItem()).toString();
                if (ammoId.equals("minecraft:firework_rocket")) families.add(ProjectileFiringCandidates.Family.FIREWORK);
                else if (ammoId.endsWith("arrow")) families.add(ProjectileFiringCandidates.Family.ARROW);
                else return null;
            }
        } else if (id.equals("minecraft:snowball")) families.add(ProjectileFiringCandidates.Family.SNOWBALL);
        else if (id.equals("minecraft:egg")) families.add(ProjectileFiringCandidates.Family.EGG);
        else return null;
        return capture(player, item, families, count);
    }

    public static void captureRelease(Player player) {
        if (player != Minecraft.getInstance().player || player.getTicksUsingItem() < 3) return;
        ItemStack item = player.getUseItem();
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        ProjectileFiringCandidates.Family family = id.equals("minecraft:bow")
                ? ProjectileFiringCandidates.Family.ARROW
                : id.equals("minecraft:trident") ? ProjectileFiringCandidates.Family.TRIDENT : null;
        if (family != null) commit(capture(player, item, Set.of(family), 1), true);
    }

    private static Ticket capture(Player player, ItemStack item, Set<ProjectileFiringCandidates.Family> families, int count) {
        ensureContext();
        var cap = PlayerCapability.get(player).orElse(null);
        var displayContext = DISPLAY.context();
        if (displayContext == null || cap == null || !cap.isModelReady() || cap.isForceDisabled()) return null;
        cap.awaitAsyncResult();
        var position = player.getEyePosition();
        var direction = player.getLookAngle();
        var struct = cap.getServerVarContainer();
        try {
            Map<String, Float> variables = struct instanceof Int2FloatOpenHashMapStruct numbers
                    ? numbers.snapshotNumbers(32)
                    : CloudPlayerMotionSync.snapshot(cap.getModelId()).roaming();
            return new Ticket(displayContext, new ProjectileFiringCandidates.Firing(player.getUUID(),
                    UUID.randomUUID().toString(), families, count, cap.getModelId(),
                    CloudPlayerModelSync.ownCloudSelection(), variables,
                    BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                    position.x, position.y, position.z, direction.x, direction.y, direction.z),
                    CloudPlayerModelSync.verifiedOwnIdentity());
        } catch (IllegalArgumentException error) {
            YesSteveModel.LOGGER.debug("[SPM projectile] firing input exceeds limits", error);
            return null;
        }
    }

    public static void commit(Ticket ticket, boolean used) {
        if (ticket == null || !used) return;
        ensureContext();
        DISPLAY.offer(ticket.context(), ticket.firing(), ticket.identityId(), System.nanoTime());
    }

    /** Invoked after the native add-entity packet creates the real entity and restores its owner. */
    public static void nativeSpawn(Projectile projectile) {
        ensureContext();
        var expected = DISPLAY.context();
        if (expected == null) return;
        DISPLAY.nativeSpawn(expected, projectile, System.nanoTime());
    }

    public static void tick() {
        ensureContext();
        var client = Minecraft.getInstance();
        if (DISPLAY.context() == null || client.level == null) return;
        Map<UUID, Projectile> loaded = new HashMap<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Projectile projectile && loaded.size() < 4096
                    && ProjectileDisplayRuntime.family(projectile) != null) {
                loaded.put(entity.getUUID(), projectile);
            }
        }
        long now = System.nanoTime();
        DISPLAY.tick(loaded, now);
        LEDGER.retain(loaded.keySet());
        Set<CloudPlayerSelection> selections = new HashSet<>();
        loaded.keySet().forEach(id -> {
            var lease = LEDGER.get(id, now);
            if (lease != null) selections.add(lease.snapshot().selection());
        });
        RETRIES.keySet().retainAll(selections);
        OWN.keySet().retainAll(loaded.keySet());
        RENEW_AT.keySet().retainAll(loaded.keySet());
        for (var entry : loaded.entrySet()) {
            Entity owner = entry.getValue().getOwner();
            LEDGER.observe(entry.getKey(), new ProjectileSnapshotLedger.Observation(
                    ProjectileDisplayRuntime.kind(entry.getValue()), owner == null ? null : owner.getUUID()));
        }
        if (shared() && !polling && now - nextPoll >= 0 && !loaded.isEmpty()) poll(loaded);
        for (var entry : loaded.entrySet()) {
            UUID id = entry.getKey();
            Projectile entity = entry.getValue();
            ProjectileDisplaySnapshot local = DISPLAY.local(id);
            if (local != null) {
                if (shared() && local.identityId() != null && local.selection() != null
                        && !OWN.containsKey(id) && !RENEWING.contains(id)) {
                    publish(local);
                }
            } else {
                var lease = LEDGER.get(id, now);
                if (lease == null) {
                    DISPLAY.releaseRemote(id);
                    continue;
                }
                var snapshot = lease.snapshot();
                if (!ClientModelManager.getAvailableModelIds().contains(snapshot.selection().runtimeModelId())) {
                    materialize(snapshot.selection());
                    DISPLAY.releaseRemote(id);
                    continue;
                }
                if (!snapshot.projectileBundleKey().equals(ProjectileDisplayRuntime.kind(entity))
                        && !(entity instanceof AbstractArrow && snapshot.projectileBundleKey().equals("minecraft:arrow"))) {
                    DISPLAY.releaseRemote(id);
                    continue;
                }
                DISPLAY.applyRemote(id, entity, snapshot.selection().runtimeModelId(),
                        snapshot.projectileBundleKey(), snapshot.variables(), snapshot.firingItemId(),
                        snapshot.selection());
            }
            var lease = OWN.get(id);
            if (lease != null && shared() && !RENEWING.contains(id)
                    && now - RENEW_AT.getOrDefault(id, Long.MAX_VALUE) >= 0) {
                renew(id, lease);
            }
        }
    }

    private static void publish(ProjectileDisplaySnapshot local) {
        var expected = sharedContext;
        var session = runtime;
        if (expected == null || session == null) return;
        UUID id = local.entityUuid();
        RENEWING.add(id);
        var snapshot = new ProjectileAppearanceSnapshot(expected.worldEpoch(), expected.dimensionId(), id,
                local.entityKind(), local.identityId(), local.ownerUuid(),
                CloudAssetCache.sha256((local.eventId() + ":" + id).getBytes(StandardCharsets.UTF_8)),
                local.selection(), local.model().bundleKey(), local.variables(), local.firingItemId());
        var api = new CloudProjectileSnapshotClient(session.http(), session.instanceInfo());
        CompletableFuture<CloudProjectileSnapshotClient.Lease> request;
        try {
            request = api.publish(expected, snapshot);
        } catch (IllegalArgumentException error) {
            request = CompletableFuture.failedFuture(error);
        }
        request.whenComplete((lease, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(expected, session)) {
                if (error == null) api.withdraw(expected, lease).exceptionally(failure -> null);
                return;
            }
            RENEWING.remove(id);
            if (error == null) {
                if (DISPLAY.local(id) == local) {
                    OWN.put(id, lease);
                    RENEW_AT.put(id, System.nanoTime() + renewDelay(lease));
                }
            } else {
                RENEW_AT.put(id, Long.MAX_VALUE);
                YesSteveModel.LOGGER.debug("[SPM projectile] publication rejected", error);
            }
        }));
    }

    private static long renewDelay(CloudProjectileSnapshotClient.Lease lease) {
        return Math.max(1_000_000_000L, (lease.expiresAtMs() - lease.serverTimeMs()) * 500_000L);
    }

    private static void renew(UUID id, CloudProjectileSnapshotClient.Lease lease) {
        var expected = sharedContext;
        var session = runtime;
        var api = new CloudProjectileSnapshotClient(session.http(), session.instanceInfo());
        RENEWING.add(id);
        api.renew(expected, lease).whenComplete((next, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(expected, session)) {
                if (error == null) api.withdraw(expected, next).exceptionally(failure -> null);
                return;
            }
            RENEWING.remove(id);
            if (error == null && OWN.get(id) == lease) {
                OWN.put(id, next);
                RENEW_AT.put(id, System.nanoTime() + renewDelay(next));
            } else {
                OWN.remove(id);
                LEDGER.unload(id);
                DISPLAY.releaseRemote(id);
                if (error == null) api.withdraw(expected, next).exceptionally(failure -> null);
            }
        }));
    }

    private static void poll(Map<UUID, Projectile> loaded) {
        var expected = sharedContext;
        var session = runtime;
        var api = new CloudProjectileSnapshotClient(session.http(), session.instanceInfo());
        polling = true;
        nextPoll = System.nanoTime() + 2_000_000_000L;
        var ids = List.copyOf(loaded.keySet());
        int batch = CloudVisualQueryBudget.batchLimit(expected, session.instanceInfo());
        var requests = new ArrayList<CompletableFuture<Map<UUID, CloudProjectileSnapshotClient.Lease>>>();
        for (int i = 0; i < ids.size(); i += batch) {
            requests.add(api.query(expected, ids.subList(i, Math.min(i + batch, ids.size()))));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (!current(expected, session)) return;
                    polling = false;
                    if (error != null) {
                        nextPoll = System.nanoTime() + 5_000_000_000L;
                        Throwable cause = error;
                        while (cause instanceof CompletionException && cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        if (cause instanceof CloudHttpException denied
                                && (denied.statusCode() == 401 || denied.statusCode() == 403)) {
                            LEDGER.enter(null);
                            LEDGER.enter(sharedContext);
                            DISPLAY.releaseAllRemote();
                        }
                        return;
                    }
                    Map<UUID, CloudProjectileSnapshotClient.Lease> found = new HashMap<>();
                    requests.forEach(request -> found.putAll(request.join()));
                    long now = System.nanoTime();
                    for (UUID id : ids) {
                        Projectile entity = loaded.get(id);
                        if (Minecraft.getInstance().level.getEntity(entity.getId()) != entity) continue;
                        var lease = found.get(id);
                        if (lease == null) {
                            LEDGER.unload(id);
                            continue;
                        }
                        Entity owner = entity.getOwner();
                        LEDGER.observe(id, new ProjectileSnapshotLedger.Observation(
                                ProjectileDisplayRuntime.kind(entity), owner == null ? null : owner.getUUID()));
                        LEDGER.accept(expected, lease.snapshot(), lease.revision(), lease.deadlineNanos(now), now);
                    }
                }));
    }

    private static void materialize(CloudPlayerSelection selection) {
        if (IMPORTS.containsKey(selection) || IMPORTS.size() >= 128
                || System.nanoTime() - RETRIES.getOrDefault(selection, 0L) < 0) return;
        var expected = sharedContext;
        var session = runtime;
        var future = new CompletableFuture<String>();
        IMPORTS.put(selection, future);
        session.downloadDisplayAsset(selection.ref(), selection.format())
                .thenApply(CloudAssetClient.MemoryAsset::bytes)
                .whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
                    if (!current(expected, session) || !wanted(selection)) {
                        IMPORTS.remove(selection, future);
                        future.completeExceptionally(new CancellationException("Projectile display permission changed"));
                        return;
                    }
                    if (error != null) {
                        IMPORTS.remove(selection, future);
                        RETRIES.put(selection, System.nanoTime() + 5_000_000_000L);
                        future.completeExceptionally(error);
                        return;
                    }
                    ClientModelManager.importCloudModelInMemory(selection.runtimeModelId(), selection.importFileName(), bytes, failure -> {
                        if (!current(expected, session) || !wanted(selection)) {
                            IMPORTS.remove(selection, future);
                            future.completeExceptionally(new CancellationException("Projectile display permission changed"));
                            return;
                        }
                        IMPORTS.remove(selection, future);
                        if (failure == null) {
                            RETRIES.remove(selection);
                            future.complete(selection.runtimeModelId());
                        } else {
                            RETRIES.put(selection, System.nanoTime() + 5_000_000_000L);
                            future.completeExceptionally(new IllegalStateException(failure.getString()));
                        }
                    });
                }));
    }

    private static boolean wanted(CloudPlayerSelection selection) {
        if (DISPLAY.hasAppliedSelection(selection)) return true;
        long now = System.nanoTime();
        var client = Minecraft.getInstance();
        if (client.level == null) return false;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Projectile) {
                var lease = LEDGER.get(entity.getUUID(), now);
                if (lease != null && lease.snapshot().selection().equals(selection)) return true;
            }
        }
        return false;
    }
}
