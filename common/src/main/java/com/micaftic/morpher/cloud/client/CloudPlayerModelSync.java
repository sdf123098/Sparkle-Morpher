package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.google.gson.JsonObject;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Publishes and discovers appearances directly through Cloud on any Minecraft server. */
public final class CloudPlayerModelSync {
    private static final CloudPlayerAppearanceState STATE = new CloudPlayerAppearanceState();
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final Map<String, CompletableFuture<String>> IMPORTS = new HashMap<>();
    private static final Set<String> AUTHORIZED = new HashSet<>();
    private static CloudClientRuntime.RuntimeState authorizationRuntime;
    private static volatile CloudClientRuntime.RuntimeState sessionRuntime;
    private static volatile Object connection, level;
    private static volatile long generation;
    private static long nextPoll, nextPublish, lastPollSuccess;
    private static boolean polling, publishing, identifying, hasPublished, hasAttempted;
    private static String identityId;
    private static long ownRevision = -1;
    private static CloudPlayerSelection lastPublished;
    private static CloudPlayerSelection lastAttempted;
    private static UUID publicationUuid;
    private static JsonObject ownNameProof;
    private static long nextIdentityRefresh;
    private record Identification(CloudIdentityClient.CloudIdentity identity, JsonObject nameProof) {}
    private static String status = "Cloud 联机：等待进入世界";
    private record Attempt(CloudPlayerAppearanceState.Snapshot token, Player entity,
            CloudClientRuntime.RuntimeState runtime, long retryAt, boolean pending, boolean applied) {}
    private CloudPlayerModelSync() {}

    public static String status() { return status; }
    public static boolean ownsAppearance(UUID playerId) { return !PrivacyMode.isActive() && STATE.ownsAppearance(playerId); }

    private static void clearRemote(Player player) {
        STATE.remove(player.getUUID()); ATTEMPTS.remove(player.getUUID());
        if (!CloudEntityModelSync.ownsFakeAppearance(player.getUUID()))
            PlayerCapability.get(player).ifPresent(cap -> { cap.clearModel(); cap.setForceDisabled(true); });
    }

    private static void reset(Minecraft client, CloudClientRuntime.RuntimeState runtime) {
        // Retire an old publication using its acknowledged revision. CAS prevents it clearing a newer session.
        if (sessionRuntime != null && identityId != null && ownRevision >= 0 && publicationUuid != null)
            new CloudPlayerPresenceClient(sessionRuntime.http()).publish(identityId, publicationUuid, ownRevision, null, ownNameProof).exceptionally(error -> null);
        if (client.level != null) for (Player player : client.level.players()) if (player != client.player && STATE.ownsAppearance(player.getUUID())) clearRemote(player);
        generation++; STATE.clear(); ATTEMPTS.clear(); IMPORTS.clear(); AUTHORIZED.clear(); authorizationRuntime = null;
        sessionRuntime = runtime; connection = client.getConnection(); level = client.level;
        polling = publishing = identifying = hasPublished = hasAttempted = false; identityId = null; ownRevision = -1;
        nextPoll = nextPublish = lastPollSuccess = 0; lastPublished = lastAttempted = null; publicationUuid = null;
        ownNameProof = null; nextIdentityRefresh = 0;
    }

    public static void disconnect() {
        Minecraft client = Minecraft.getInstance(); reset(client, null); connection = level = null;
    }

    private static boolean current(long token, CloudClientRuntime.RuntimeState runtime) {
        return token == generation && runtime == CloudClientRuntime.state() && runtime == sessionRuntime
                && Minecraft.getInstance().level == level && Minecraft.getInstance().getConnection() == connection;
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance(); var runtime = CloudClientRuntime.state();
        if (runtime != sessionRuntime || client.getConnection() != connection) reset(client, runtime);
        // Dimension/respawn changes invalidate imports but keep the session publication revision.
        if (level != client.level) {
            generation++; level = client.level; STATE.clear(); ATTEMPTS.clear(); IMPORTS.clear();
            polling = publishing = identifying = false; nextPoll = nextPublish = 0;
        }
        if (client.player == null || client.level == null) return;
        if (runtime == null) {
            for (Player player : client.level.players()) if (player != client.player) clearRemote(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (PrivacyMode.isActive()) {
            for (Player player : client.level.players()) if (player != client.player && STATE.ownsAppearance(player.getUUID())) clearRemote(player);
            publish(runtime, null, now); return;
        }
        if (!identifying && (identityId == null && now >= nextPublish || ownNameProof != null && now >= nextIdentityRefresh)) identify(runtime, now);
        CloudPlayerSelection selection = localSelection(runtime);
        if (identityId != null && (!hasPublished || !Objects.equals(lastPublished, selection) || now >= nextPublish)) publish(runtime, selection, now);
        List<Player> peers = client.level.players().stream().filter(player -> player != client.player).map(player -> (Player) player).toList();
        if (!polling && now >= nextPoll && !peers.isEmpty()) poll(runtime, peers, now);
        // Transient Cloud outages keep a verified appearance briefly; then revert to vanilla.
        if (lastPollSuccess > 0 && now - lastPollSuccess > 60000) for (Player player : peers) if (STATE.ownsAppearance(player.getUUID())) clearRemote(player);
        for (Player player : peers) {
            var token = STATE.get(player.getUUID());
            if (token != null && token.selection() != null) apply(player, token);
            else if (!CloudEntityModelSync.ownsFakeAppearance(player.getUUID()))
                PlayerCapability.get(player).ifPresent(cap -> cap.setForceDisabled(true));
        }
    }

    private static void identify(CloudClientRuntime.RuntimeState runtime, long now) {
        identifying = true; nextPublish = now + 10000; long token = generation;
        var profile = MinecraftSessionServiceJoiner.currentProfile();
        UUID entityUuid = Minecraft.getInstance().player.getUUID();
        runtime.identities().listIdentities().thenCompose(identities -> {
            var matches = identities.stream().filter(identity -> identity.verificationStatus().equals("VERIFIED")
                    && identity.identityRef().profileUuid().equals(profile.profileId())).toList();
            if (matches.size() != 1) return CompletableFuture.<Identification>failedFuture(
                    new IllegalStateException("请在 Cloud 账号管理绑定当前游戏身份"));
            var identity = matches.get(0);
            if (entityUuid.equals(profile.profileId()))
                return CompletableFuture.completedFuture(new Identification(identity, null));
            // Offline-mode entity UUID is accepted only after the provider proves its canonical profile name.
            String provider = identity.identityRef().providerId() == null ? "official" : identity.identityRef().providerId();
            if (provider.equals("official")) {
                return CloudOfficialProfileNameProof.fetch(profile.profileId()).thenApply(proof -> new Identification(identity, proof));
            }
            return runtime.identities().createChallenge(provider, profile.name(), profile.profileId().toString())
                    .thenCompose(challenge -> runtime.identities().joinAndComplete(challenge,
                            (CloudIdentityClient.SessionJoiner) value -> new MinecraftSessionServiceJoiner().join(value)))
                    .thenApply(verified -> new Identification(verified, null));
        }).whenComplete((identity, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, runtime)) return; identifying = false;
            if (error == null) { identityId = identity.identity().identityId(); ownNameProof = identity.nameProof(); nextIdentityRefresh = System.currentTimeMillis() + 120000; nextPublish = 0; status = "Cloud 联机：游戏身份已验证"; }
            else { nextIdentityRefresh = System.currentTimeMillis() + 10000; status = "Cloud 联机身份验证失败：" + message(error); YesSteveModel.LOGGER.warn("[SM][CloudPlayer] identity: {}", message(error)); }
        }));
    }

    /** Local selection callbacks request immediate publication; tick also notices texture changes. */
    public static void publishCurrentSelection() { nextPublish = 0; }

    private static CloudPlayerSelection localSelection(CloudClientRuntime.RuntimeState runtime) {
        Player player = Minecraft.getInstance().player;
        var capability = PlayerCapability.get(player);
        if (capability.isEmpty()) return null;
        var cap = capability.get(); String modelId = cap.getModelId();
        if (!CloudAssetIdentity.isRuntimeModelId(modelId) || cap.isForceDisabled()) return null;
        String instance = runtime.instance().instanceId();
        CloudAssetSummary asset = CloudSelectedModelRecovery.findAsset(instance, modelId, List.copyOf(runtime.assetCatalog().snapshot().values()));
        if (asset == null) asset = CloudSelectedModelRecovery.findAsset(instance, modelId, CloudModelSelectionStore.recent(instance));
        return asset == null ? null : new CloudPlayerSelection(instance, originFingerprint(runtime), asset.ref(), asset.format(), Objects.requireNonNullElse(cap.currentTextureName, "default"));
    }

    private static void publish(CloudClientRuntime.RuntimeState runtime, CloudPlayerSelection selection, long now) {
        if (publishing || identityId == null || now < nextPublish && hasAttempted && Objects.equals(selection, lastAttempted)) return;
        lastAttempted = selection; hasAttempted = true;
        publishing = true; nextPublish = now + 15000; long token = generation;
        var api = new CloudPlayerPresenceClient(runtime.http()); UUID uuid = Minecraft.getInstance().player.getUUID();
        JsonObject nameProof = ownNameProof;
        publicationUuid = uuid;
        CompletableFuture<Long> revision = ownRevision < 0 ? api.revision(identityId) : CompletableFuture.completedFuture(ownRevision);
        revision.thenCompose(expected -> current(token, runtime) ? api.publish(identityId, uuid, expected, selection, nameProof)
                : CompletableFuture.failedFuture(new CancellationException())).whenComplete((value, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, runtime)) return; publishing = false;
            if (error == null) { ownRevision = value; lastPublished = selection; hasPublished = true; status = "Cloud 联机：已同步模型与贴图"; }
            else { ownRevision = -1; nextPublish = System.currentTimeMillis() + 3000; hasPublished = false;
                status = "Cloud 联机发布失败：" + message(error); YesSteveModel.LOGGER.warn("[SM][CloudPlayer] publish: {}", message(error)); }
        }));
    }

    private static void poll(CloudClientRuntime.RuntimeState runtime, List<Player> peers, long now) {
        polling = true; nextPoll = now + 1000; long token = generation;
        var api = new CloudPlayerPresenceClient(runtime.http()); List<CompletableFuture<Map<UUID, CloudPlayerSelection>>> requests = new ArrayList<>();
        List<UUID> ids = peers.stream().map(Player::getUUID).distinct().toList();
        for (int i = 0; i < ids.size(); i += 64) requests.add(api.query(ids.subList(i, Math.min(i + 64, ids.size()))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            if (!current(token, runtime)) return; polling = false;
            if (error != null) { nextPoll = System.currentTimeMillis() + 3000; status = "Cloud 联机查询失败：" + message(error); return; }
            if (PrivacyMode.isActive()) return;
            Map<UUID, CloudPlayerSelection> entries = new HashMap<>(); requests.forEach(request -> entries.putAll(request.join()));
            lastPollSuccess = System.currentTimeMillis();
            for (Player player : Minecraft.getInstance().level.players()) if (player != Minecraft.getInstance().player && ids.contains(player.getUUID())) {
                CloudPlayerSelection selection = entries.get(player.getUUID());
                if (selection == null) clearRemote(player);
                else { var old = STATE.get(player.getUUID()); if (old == null || !selection.equals(old.selection())) { STATE.publish(player.getUUID(), selection); ATTEMPTS.remove(player.getUUID()); } }
            }
        }));
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }

    private static String originFingerprint(CloudClientRuntime.RuntimeState runtime) {
        return CloudAssetCache.sha256(runtime.instance().origin().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void apply(Player player, CloudPlayerAppearanceState.Snapshot token) {
        if (PrivacyMode.isActive()) return;
        var runtime = CloudClientRuntime.state();
        Attempt previous = ATTEMPTS.get(player.getUUID());
        if (previous != null && previous.token().equals(token) && previous.entity() == player
                && previous.runtime() == runtime && (previous.pending() || previous.applied() || System.currentTimeMillis() < previous.retryAt())) return;
        PlayerCapability.get(player).ifPresent(cap -> {
            // A missing/denied Cloud model must not draw the observer's local fallback.
            cap.setForceDisabled(true);
            CloudPlayerSelection selection = token.selection();
            if (runtime == null || !selection.instanceId().equals(runtime.instance().instanceId())
                    || !selection.originSha256().equals(originFingerprint(runtime))) {
                ATTEMPTS.put(player.getUUID(), new Attempt(token, player, runtime, System.currentTimeMillis() + 5000, false, false));
                return;
            }
            Object world = level;
            Attempt attempt = new Attempt(token, player, runtime, 0, true, false);
            ATTEMPTS.put(player.getUUID(), attempt);
            importModel(runtime, selection).whenComplete((modelId, failure) -> Minecraft.getInstance().execute(() -> {
                if (level != world || Minecraft.getInstance().level != world || CloudClientRuntime.state() != runtime
                        || !STATE.isCurrent(token) || PrivacyMode.isActive()
                        || !Minecraft.getInstance().level.players().contains(player)) {
                    ATTEMPTS.remove(player.getUUID(), attempt);
                    return;
                }
                ATTEMPTS.put(player.getUUID(), new Attempt(token, player, runtime, System.currentTimeMillis() + 5000, false, failure == null));
                if (failure == null) {
                    cap.initModelWithTexture(modelId, selection.textureId());
                    cap.setForceDisabled(false);
                    YesSteveModel.LOGGER.info("[SM][CloudPlayer] applied player={} asset={} revision={} texture={}", player.getUUID(), selection.ref().assetId(), selection.ref().revision(), selection.textureId());
                } else {
                    YesSteveModel.LOGGER.warn("[SM][CloudPlayer] failed player={} asset={} revision={}: {}", player.getUUID(), selection.ref().assetId(), selection.ref().revision(), failure.toString());
                }
            }));
        });
    }

    private static CompletableFuture<String> importModel(CloudClientRuntime.RuntimeState runtime, CloudPlayerSelection selection) {
        String id = selection.runtimeModelId();
        if (authorizationRuntime != runtime) {
            authorizationRuntime = runtime; AUTHORIZED.clear(); IMPORTS.clear();
        }
        if (AUTHORIZED.contains(id) && ClientModelManager.getAvailableModelIds().contains(id)) return CompletableFuture.completedFuture(id);
        CompletableFuture<String> existing = IMPORTS.get(id);
        if (existing != null) return existing;
        CompletableFuture<String> result = new CompletableFuture<>();
        IMPORTS.put(id, result);
        // Only the authenticated Cloud response supplies asset references; never trust a game-server packet.
        // The configured Cloud authenticates every exact revision GET; the cache verifies SHA.
        runtime.assetCache().downloadAndStore(runtime.assets(), selection.ref(), runtime.cacheRoot())
                .thenApplyAsync(path -> {
                    try { return Files.readAllBytes(path); }
                    catch (java.io.IOException failure) { throw new CompletionException(failure); }
                }).whenComplete((bytes, failure) -> Minecraft.getInstance().execute(() -> {
                    if (CloudClientRuntime.state() != runtime) result.completeExceptionally(new IllegalStateException("Cloud session changed"));
                    else if (failure != null) result.completeExceptionally(failure);
                    else {
                        AUTHORIZED.add(id);
                        if (ClientModelManager.getAvailableModelIds().contains(id)) result.complete(id);
                        else ClientModelManager.importLocalModel(id, selection.importFileName(), bytes, error -> {
                            if (error == null) result.complete(id); else result.completeExceptionally(new IllegalStateException(error.getString()));
                        });
                    }
                    result.whenComplete((ignored, error) -> IMPORTS.remove(id, result));
                }));
        return result;
    }
}
