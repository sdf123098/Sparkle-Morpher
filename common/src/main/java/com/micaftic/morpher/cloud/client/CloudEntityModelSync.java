package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.util.*;
import java.util.concurrent.*;

/** World-scoped entity appearances on arbitrary Minecraft servers; all transport is Cloud HTTPS. */
public final class CloudEntityModelSync {
    private static final Map<UUID, CloudEntityPresenceClient.Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final CloudEntityApplyGuard APPLY_GUARD = new CloudEntityApplyGuard();
    private static final CloudEntityModelMaterializer IMPORTS = new CloudEntityModelMaterializer();
    private static final Map<UUID, CompletableFuture<CloudEntityPresenceClient.Entry>> PUBLICATIONS = new HashMap<>();
    private static final Map<UUID, Long> CHOICES = new HashMap<>();
    private static final Map<UUID, Feedback> FEEDBACK = new HashMap<>();
    private static CloudClientRuntime.RuntimeState runtime;
    private static Object connection, level;
    private static String worldKey;
    private static long generation, nextPoll, mutation, nextChoice, lastVerified;
    private static boolean polling;
    private record Attempt(CloudEntityPresenceClient.Entry entry, Entity entity, boolean pending, boolean applied, long retryAt) {}
    public record Feedback(Component message, boolean failed, boolean pending) {}
    private CloudEntityModelSync() {}

    public static Feedback feedback(String stateKey) {
        if (stateKey == null || !stateKey.contains(":")) return null;
        try { return FEEDBACK.get(UUID.fromString(stateKey.substring(stateKey.indexOf(':') + 1))); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static boolean ownsFakeAppearance(UUID id) {
        var entry = ENTRIES.get(id);
        return !PrivacyMode.isActive() && entry != null && entry.kind() == CloudEntityProvider.Kind.FAKE_PLAYER;
    }
    public static String selectedModelId(UUID id) {
        var entry = ENTRIES.get(id);
        return entry == null || entry.selection() == null ? "" : entry.selection().runtimeModelId();
    }
    /** Explicit candidate list; a client cannot authoritatively identify a Carpet server entity. */
    public static void refreshFakeTargets() {
        context();
        var client = Minecraft.getInstance();
        List<com.micaftic.morpher.fakeplayer.FakePlayerListEntry> rows = new ArrayList<>();
        if (client.level != null) for (Player player : client.level.players()) if (player != client.player) {
            String name = player.getName().getString();
            rows.add(new com.micaftic.morpher.fakeplayer.FakePlayerListEntry(player.getUUID(), name,
                    player.getDisplayName().getString(), ownsFakeAppearance(player.getUUID()) ? "Cloud" : "client",
                    selectedModelId(player.getUUID()), client.level.dimension().toString(), true));
        }
        com.micaftic.morpher.fakeplayer.FakePlayerListCache.replace(rows);
    }
    private static String currentWorldKey(Minecraft client) {
        var listener = client.getConnection();
        var server = listener == null ? null : listener.getServerData();
        if (server != null && server.ip != null && !server.ip.isBlank()) return CloudEntityWorldKey.remote(server.ip);
        var integrated = client.getSingleplayerServer();
        return integrated == null ? null : CloudEntityWorldKey.local(integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
    }
    private static void context() {
        Minecraft client = Minecraft.getInstance();
        var nextRuntime = CloudClientRuntime.state();
        String nextKey = client.level == null ? null : currentWorldKey(client);
        if (nextRuntime != runtime || client.getConnection() != connection || !Objects.equals(nextKey, worldKey)) {
            if (client.level == level && client.level != null) for (Entity entity : client.level.entitiesForRendering()) clear(entity, ENTRIES.get(entity.getUUID()));
            generation++; mutation++;
            runtime = nextRuntime; connection = client.getConnection(); level = client.level; worldKey = nextKey;
            ENTRIES.clear(); ATTEMPTS.clear(); IMPORTS.clear(); PUBLICATIONS.clear(); CHOICES.clear(); FEEDBACK.clear();
            APPLY_GUARD.clear();
            polling = false; nextPoll = lastVerified = 0;
        } else if (level != client.level) {
            generation++; mutation++; level = client.level;
            ENTRIES.clear(); ATTEMPTS.clear(); polling = false; nextPoll = lastVerified = 0;
            APPLY_GUARD.clear();
        }
    }
    private static boolean current(long token, CloudClientRuntime.RuntimeState expected, String expectedWorld) {
        Minecraft client = Minecraft.getInstance();
        return token == generation && expected == runtime && expected == CloudClientRuntime.state()
                && Objects.equals(worldKey, expectedWorld) && client.level == level && client.getConnection() == connection;
    }
    public static void applySelection(CloudEntityProvider.Kind kind, UUID entityId, String name, String modelId, String textureId) {
        context();
        if (PrivacyMode.isActive()) { notify(entityId, "privacy"); return; }
        if (kind == CloudEntityProvider.Kind.PLAYER || runtime == null || worldKey == null) {
            notify(entityId, "unavailable"); return;
        }
        CloudPlayerSelection selection = null;
        if (!"default".equals(modelId) && modelId != null && !modelId.isBlank()) {
            if (!CloudAssetIdentity.isRuntimeModelId(modelId)) { notify(entityId, "cloud_required"); return; }
            var assets = new ArrayList<>(runtime.assetCatalog().snapshot().values());
            assets.addAll(CloudModelSelectionStore.recent(runtime.instance().instanceId()));
            var asset = CloudSelectedModelRecovery.findAsset(runtime.instance().instanceId(), modelId, assets);
            if (asset == null) { notify(entityId, "cloud_required"); return; }
            selection = new CloudPlayerSelection(runtime.instance().instanceId(),
                    CloudAssetCache.sha256(runtime.instance().origin().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    asset.ref(), asset.format(), textureId == null || textureId.isBlank() ? "default" : textureId);
        }
        final var selected = selection;
        final var expected = runtime; final String key = worldKey;
        final long token = generation, choice = ++nextChoice;
        CHOICES.put(entityId, choice);
        notify(entityId, "pending", name);
        var api = new CloudEntityPresenceClient(expected.http());
        var previous = PUBLICATIONS.get(entityId);
        var ready = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((ignored, error) -> null);
        java.util.function.BooleanSupplier allowed = () -> current(token, expected, key)
                && !PrivacyMode.isActive() && Objects.equals(CHOICES.get(entityId), choice);
        var request = ready.thenCompose(ignored -> CloudEntityPublicationGuard.run(Minecraft.getInstance()::execute, allowed, () -> api.ensureWorld(key)))
                .thenCompose(ignored -> CloudEntityPublicationGuard.run(Minecraft.getInstance()::execute, allowed, () -> api.revision(key, entityId)))
                .thenCompose(revision -> CloudEntityPublicationGuard.run(Minecraft.getInstance()::execute, allowed, () -> api.publish(key, entityId, kind, name, revision, selected)));
        PUBLICATIONS.put(entityId, request);
        request.whenComplete((entry, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, expected, key)) return;
            PUBLICATIONS.remove(entityId, request);
            if (!Objects.equals(CHOICES.get(entityId), choice)) return;
            if (error != null) { notifyFailure(entityId, error); return; }
            mutation++; ENTRIES.put(entityId, entry); ATTEMPTS.remove(entityId); nextPoll = 0;
            lastVerified = System.currentTimeMillis();
            tick();
            notify(entityId, "synced", name);
        }));
    }
    public static void tick() {
        context();
        Minecraft client = Minecraft.getInstance();
        if (runtime == null || worldKey == null || client.level == null) return;
        // Explicit scope bindings are owned by the permission-checked provider.
        // The compatibility path must not overwrite its fallback after revocation.
        String scope = runtime.scopeLifecycle().activeScopeId();
        String epoch = runtime.scopeLifecycle().activeWorldEpoch();
        Set<UUID> explicitlyBound = new HashSet<>();
        if (scope != null && epoch != null) for (var binding : runtime.bindingResolver().snapshot(scope, epoch)) {
            try { explicitlyBound.add(UUID.fromString(binding.entityUuid())); }
            catch (IllegalArgumentException ignored) { }
        }
        for (UUID id : explicitlyBound) {
            ENTRIES.remove(id);
            ATTEMPTS.remove(id);
            APPLY_GUARD.remove(id);
        }
        List<Entity> visible = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && !explicitlyBound.contains(entity.getUUID())
                    && (entity instanceof Player || MaidCapability.get(entity).isPresent())) visible.add(entity);
        }
        Set<UUID> visibleIds = new HashSet<>();
        visible.forEach(entity -> visibleIds.add(entity.getUUID()));
        for (UUID id : List.copyOf(ENTRIES.keySet())) if (!visibleIds.contains(id)) {
            ENTRIES.remove(id); ATTEMPTS.remove(id); APPLY_GUARD.remove(id);
        }
        long now = System.currentTimeMillis();
        if (lastVerified > 0 && now - lastVerified > 60000) clearAll();
        if (!PrivacyMode.isActive() && !polling && now >= nextPoll && !visible.isEmpty()) poll(visible, now);
        for (Entity entity : visible) {
            var entry = ENTRIES.get(entity.getUUID());
            if (entry == null) continue;
            if (PrivacyMode.isActive()) { clear(entity, entry); ATTEMPTS.remove(entity.getUUID()); continue; }
            if (entry.kind() == CloudEntityProvider.Kind.FAKE_PLAYER && (!(entity instanceof Player)
                    || CloudPlayerModelSync.ownsAppearance(entity.getUUID()))) {
                ATTEMPTS.remove(entity.getUUID()); continue;
            }
            if (entry.kind() == CloudEntityProvider.Kind.MAID && MaidCapability.get(entity).isEmpty()) continue;
            apply(entity, entry, now);
        }
    }
    private static void poll(List<Entity> entities, long now) {
        polling = true; nextPoll = now + 1000;
        final var expected = runtime; final String key = worldKey; final long token = generation, change = mutation;
        var ids = entities.stream().map(Entity::getUUID).distinct().toList();
        var api = new CloudEntityPresenceClient(expected.http());
        List<CompletableFuture<Map<UUID, CloudEntityPresenceClient.Entry>>> requests = new ArrayList<>();
        int batch = expected.instanceInfo() == null ? 64 : (int) expected.instanceInfo().maxEntityQueryCount();
        for (int i = 0; i < ids.size(); i += batch) requests.add(api.query(key, ids.subList(i, Math.min(i + batch, ids.size()))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, expected, key)) return;
            polling = false;
            if (error != null) {
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof CloudHttpException http && (http.statusCode() == 401 || http.statusCode() == 403)) clearAll();
                nextPoll = System.currentTimeMillis() + 5000; return;
            }
            if (change != mutation || PrivacyMode.isActive()) { nextPoll = 0; return; }
            Map<UUID, CloudEntityPresenceClient.Entry> found = new HashMap<>();
            requests.forEach(request -> found.putAll(request.join()));
            lastVerified = System.currentTimeMillis();
            for (Entity entity : entities) {
                UUID id = entity.getUUID(); var entry = found.get(id); var old = ENTRIES.get(id);
                if (entry == null) {
                    if (old != null && !PUBLICATIONS.containsKey(id)) {
                        Entity currentEntity = loadedEntity(id);
                        if (currentEntity != null) clear(currentEntity, old);
                        ENTRIES.remove(id); ATTEMPTS.remove(id);
                        APPLY_GUARD.remove(id);
                    }
                } else if (old == null || entry.revision() >= old.revision()) ENTRIES.put(id, entry);
            }
        }));
    }
    private static void apply(Entity entity, CloudEntityPresenceClient.Entry entry, long now) {
        var previous = ATTEMPTS.get(entity.getUUID());
        if (previous != null && previous.entity() == entity && previous.entry().equals(entry)
                && (previous.pending() || previous.applied() || now < previous.retryAt())) return;
        if (entry.selection() == null) {
            clear(entity, entry);
            APPLY_GUARD.remove(entity.getUUID());
            ATTEMPTS.put(entity.getUUID(), new Attempt(entry, entity, false, true, 0));
            return;
        }
        var attempt = new Attempt(entry, entity, true, false, 0);
        ATTEMPTS.put(entity.getUUID(), attempt);
        final var expected = runtime; final String key = worldKey; final long token = generation;
        var application = APPLY_GUARD.begin(entry, entity, token);
        importModel(expected, key, entry).whenComplete((modelId, failure) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, expected, key) || ATTEMPTS.get(entity.getUUID()) != attempt
                    || !APPLY_GUARD.accept(application, ENTRIES.get(entity.getUUID()), loadedEntity(entity.getUUID()), generation,
                            PrivacyMode.isActive(), entry.kind() == CloudEntityProvider.Kind.FAKE_PLAYER && CloudPlayerModelSync.ownsAppearance(entity.getUUID()))) {
                ATTEMPTS.remove(entity.getUUID(), attempt); return;
            }
            ATTEMPTS.put(entity.getUUID(), new Attempt(entry, entity, false, failure == null, System.currentTimeMillis() + 5000));
            if (failure != null) { YesSteveModel.LOGGER.warn("[SPM Cloud entity] model download failed: {}", message(failure)); return; }
            if (entry.kind() == CloudEntityProvider.Kind.MAID) MaidCapability.get(entity).ifPresent(cap -> cap.applyCloudState(modelId, entry.selection().textureId()));
            else if (entity instanceof Player player) PlayerCapability.get(player).ifPresent(cap -> {
                cap.initModelWithTexture(modelId, entry.selection().textureId()); cap.setForceDisabled(false);
            });
        }));
    }
    private static Entity loadedEntity(UUID id) {
        var world = Minecraft.getInstance().level;
        if (world != null) for (Entity entity : world.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity;
        return null;
    }
    private static void clearAll() {
        var world = Minecraft.getInstance().level;
        if (world != null) for (Entity entity : world.entitiesForRendering()) clear(entity, ENTRIES.get(entity.getUUID()));
        ENTRIES.clear(); ATTEMPTS.clear(); IMPORTS.clear(); lastVerified = 0;
        APPLY_GUARD.clear();
    }
    private static CompletableFuture<String> importModel(CloudClientRuntime.RuntimeState expected, String key, CloudEntityPresenceClient.Entry entry) {
        return IMPORTS.load(entry, id -> ClientModelManager.getAvailableModelIds().contains(id),
                value -> downloadModel(expected, key, value));
    }
    private static CompletableFuture<String> downloadModel(CloudClientRuntime.RuntimeState expected, String key, CloudEntityPresenceClient.Entry entry) {
        String id = entry.selection().runtimeModelId();
        var future = new CompletableFuture<String>();
        new CloudEntityPresenceClient(expected.http()).download(key, entry).thenApplyAsync(response -> {
            if (response.statusCode() != 200) throw CloudHttpClient.httpFailure(response);
            try {
                byte[] verifiedBytes = response.body();
                CloudAssetCache.writeVerified(expected.cacheRoot(), entry.selection().ref(), verifiedBytes);
                return verifiedBytes;
            }
            catch (java.io.IOException error) { throw new CompletionException(error); }
        }).whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
            if (expected != runtime || expected != CloudClientRuntime.state()) future.completeExceptionally(new CancellationException("Cloud session changed"));
            else if (error != null) future.completeExceptionally(error);
            else if (ClientModelManager.getAvailableModelIds().contains(id)) future.complete(id);
            else ClientModelManager.importLocalModel(id, entry.selection().importFileName(), bytes, failure -> {
                if (failure == null) future.complete(id); else future.completeExceptionally(new IllegalStateException(failure.getString()));
            });
        }));
        return future;
    }
    private static void clear(Entity entity, CloudEntityPresenceClient.Entry entry) {
        if (entry == null) return;
        if (entry.kind() == CloudEntityProvider.Kind.MAID) MaidCapability.get(entity).ifPresent(cap -> cap.clearCloudState());
        else if (entity instanceof Player player && !CloudPlayerModelSync.ownsAppearance(player.getUUID()))
            PlayerCapability.get(player).ifPresent(cap -> { cap.clearModel(); cap.setForceDisabled(true); });
    }
    private static void notify(UUID entityId, String key, Object... args) {
        Component text = Component.translatable("gui.sparkle_morpher.cloud.entity." + key, args);
        FEEDBACK.put(entityId, new Feedback(text, !Set.of("pending", "synced").contains(key), "pending".equals(key)));
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(text);
    }
    private static void notifyFailure(UUID entityId, Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof CloudHttpException http && http.statusCode() == 403) notify(entityId, "permission_denied");
        else notify(entityId, "failed", message(cause));
    }
    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }
}
