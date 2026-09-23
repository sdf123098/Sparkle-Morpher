package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.client.upload.CloudUploadTransport;
import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Process-local, cross-loader Cloud client runtime.
 *
 * <p>The runtime owns one authenticated HTTP graph and one realtime session.
 * Tokens remain in memory and are discarded when the client disconnects or
 * stops. Minecraft loader adapters only provide lifecycle hooks; they do not
 * own Cloud protocol state.</p>
 */
public final class CloudClientRuntime {
    private static volatile RuntimeState current;
    private static final CloudWorldSession WORLD_SESSION = new CloudWorldSession();
    private static final ConcurrentLinkedQueue<Runnable> CLIENT_TASKS = new ConcurrentLinkedQueue<>();

    private CloudClientRuntime() {
    }

    public static Path defaultCacheRoot() {
        return Path.of("config", "sparkle-morpher", "cloud-cache");
    }

    public static synchronized void configure(
            CloudInstanceConfig instance,
            CloudSession session,
            Path cacheRoot,
            String clientVersion
    ) {
        configure(instance, session, cacheRoot, clientVersion, ignored -> { }, ignored -> { });
    }

    public static synchronized void configure(
            CloudInstanceConfig instance,
            CloudSession session,
            Path cacheRoot,
            String clientVersion,
            Consumer<CloudRealtimeClient.CloudRealtimeMessage> messageConsumer,
            Consumer<CloudRealtimeClient.CloudRealtimeEvent> eventConsumer
    ) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(clientVersion, "clientVersion");
        Objects.requireNonNull(messageConsumer, "messageConsumer");
        Objects.requireNonNull(eventConsumer, "eventConsumer");

        clearCurrent();

        CloudHttpClient http = new CloudAuthClient(new CloudHttpClient(instance)).authenticated(session);
        CloudAssetClient assets = new CloudAssetClient(http);
        CloudAppearanceStore appearances = new CloudAppearanceStore();
        CloudAnimationStore animations = new CloudAnimationStore();
        CloudEntityBindingResolver bindingResolver = new CloudEntityBindingResolver();
        CloudEntityClientCoordinator entityCoordinator = new CloudEntityClientCoordinator(
                bindingResolver, appearances, WORLD_SESSION::currentGeneration, CloudClientRuntime::enqueueClientTask);
        CloudRealtimeClient realtime = new CloudRealtimeClient(
                instance,
                session.accessToken(),
                clientVersion,
                messageConsumer,
                event -> {
                    boolean accepted = appearances.apply(event);
                    if (event.animation() != null) accepted = animations.apply(event.scopeId(), event.animation()) || accepted;
                    RuntimeState active = current;
                    if (accepted && event.appearance() != null && active != null && active.appearances() == appearances) {
                        String scopeId = active.scopeLifecycle().activeScopeId();
                        String worldEpoch = active.scopeLifecycle().activeWorldEpoch();
                        if (scopeId != null && worldEpoch != null) {
                            entityCoordinator.applyAppearance(scopeId, worldEpoch,
                                    WORLD_SESSION.currentGeneration(), event.appearance().targetId());
                        }
                    }
                    eventConsumer.accept(event);
                });
        RuntimeState next = new RuntimeState(
                instance,
                session,
                http,
                assets,
                new CloudAssetCache(),
                new CloudScopeClient(http),
                new CloudIdentityClient(http),
                new CloudIdentityBindingClient(http),
                appearances,
                animations,
                bindingResolver,
                entityCoordinator,
                realtime,
                cacheRoot.toAbsolutePath().normalize());
        current = next;

        http.discoverInstance().whenComplete((info, failure) -> {
            if (failure == null) {
                next.instanceInfo = info;
            }
        });
        realtime.connect();
    }

    public static boolean isConfigured() {
        return current != null;
    }

    public static RuntimeState state() {
        return current;
    }

    public static ModelUploadTransport uploadTransport() {
        RuntimeState state = requireState();
        return state.uploadTransport();
    }

    public static CompletableFuture<CloudRealtimeClient> connectRealtime() {
        return requireState().realtime().connect();
    }

    public static synchronized long onWorldJoined(Object connectionToken) {
        long previousGeneration = WORLD_SESSION.isActive() ? WORLD_SESSION.currentGeneration() : -1L;
        long nextGeneration = WORLD_SESSION.enter(connectionToken);
        if (previousGeneration >= 0L && nextGeneration != previousGeneration) leaveScope();
        return nextGeneration;
    }

    public static synchronized void onWorldLeft(Object connectionToken) {
        if (WORLD_SESSION.leave(connectionToken)) leaveScope();
    }

    public static synchronized void onWorldDisconnected() {
        if (WORLD_SESSION.leaveCurrent()) leaveScope();
    }

    public static long currentWorldGeneration() {
        return WORLD_SESSION.currentGeneration();
    }

    public static boolean isCurrentWorldGeneration(long expectedGeneration) {
        return WORLD_SESSION.isCurrent(expectedGeneration);
    }

    public static synchronized CompletableFuture<Void> joinScope(String scopeId, String worldEpoch) {
        WORLD_SESSION.currentGeneration();
        RuntimeState state = requireState();
        state.bindingResolver().clear();
        state.entityCoordinator().activate(scopeId, worldEpoch, WORLD_SESSION.currentGeneration());
        state.observations().enter(scopeId, worldEpoch);
        return state.scopeLifecycle().enter(scopeId, worldEpoch);
    }

    public static void leaveScope() {
        RuntimeState state = current;
        if (state != null) {
            String scopeId = state.scopeLifecycle().activeScopeId();
            state.observations().leave();
            state.bindingResolver().clear();
            state.entityCoordinator().deactivate();
            state.animations().clearScope(scopeId);
            state.scopeLifecycle().leave();
        }
    }

    public static CompletableFuture<CloudEntityObservationCoordinator.ObservationResult> reportObservation(
            java.util.UUID entityUuid,
            String entityKind,
            CloudEntityObservationCoordinator.ObservationState state
    ) {
        return requireState().observations().report(entityUuid, entityKind, state);
    }

    public static CompletableFuture<CloudEntityObservationCoordinator.ObservationResult> reportEntityObservation(
            java.util.UUID entityUuid,
            CloudEntityProvider.Kind kind,
            CloudEntityObservationCoordinator.ObservationState state
    ) {
        Objects.requireNonNull(kind, "kind");
        return reportObservation(entityUuid, kind.wireValue(), state);
    }

    /** Runs edge-triggered observations for explicitly bound client entities. */
    public static int tickEntityObservations() {
        RuntimeState state = current;
        if (state == null) return 0;
        int submitted = 0;
        for (CloudEntityObservationTracker.Observation observation : state.entityCoordinator().collectObservations()) {
            state.observations().report(observation.entityUuid(), observation.entityKind(), observation.state());
            submitted++;
        }
        return submitted;
    }

    public static void registerEntityProvider(CloudEntityProvider provider) {
        requireState().entityCoordinator().register(provider);
    }

    public static void unregisterEntityProvider(CloudEntityProvider provider) {
        RuntimeState state = current;
        if (state != null) state.entityCoordinator().unregister(provider);
    }

    public static CompletableFuture<CloudScopeClient.CloudEventRecovery> recoverScope(String scopeId, long after, int limit) {
        RuntimeState state = requireState();
        long expectedGeneration = WORLD_SESSION.currentGeneration();
        return WORLD_SESSION.guard(expectedGeneration, state.scopes().recoverEvents(scopeId, after, limit), recovery -> {
            if (!Objects.equals(scopeId, state.scopeLifecycle().activeScopeId())) {
                throw new java.util.concurrent.CancellationException("Cloud scope changed before recovery completed");
            }
            state.appearances().applyRecovery(scopeId, recovery);
        });
    }

    public static CompletableFuture<List<CloudScopeClient.CloudEntityBinding>> refreshBindings(String scopeId, String worldEpoch) {
        RuntimeState state = requireState();
        long expectedGeneration = WORLD_SESSION.currentGeneration();
        return WORLD_SESSION.guard(expectedGeneration, state.scopes().listBindings(scopeId), bindings -> {
            if (!Objects.equals(scopeId, state.scopeLifecycle().activeScopeId())
                    || !Objects.equals(worldEpoch, state.scopeLifecycle().activeWorldEpoch())) {
                throw new java.util.concurrent.CancellationException("Cloud scope changed before bindings completed");
            }
            state.bindingResolver().replace(scopeId, worldEpoch, bindings);
        });
    }

    public static CompletableFuture<List<CloudAssetSummary>> refreshAssets() {
        RuntimeState state = requireState();
        return state.assets().list().thenApply(entries -> {
            state.assetCatalog().replace(entries);
            return entries;
        });
    }

    public static CompletableFuture<Path> downloadAsset(CloudAssetRef ref) {
        RuntimeState state = requireState();
        CloudAssetSummary summary = state.assetCatalog().get(ref.assetId());
        if (summary == null || !summary.ref().equals(ref)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Cloud asset revision is not in the current catalog"));
        }
        return state.assetCache().downloadAndStore(state.assets(), ref, state.cacheRoot());
    }

    /** Ensures a catalog-approved asset revision is downloaded exactly once per runtime. */
    public static CompletableFuture<Path> materializeAsset(CloudAssetRef ref) {
        return requireState().assetMaterialization().ensure(ref);
    }

    /** Runs queued network-to-client work from the client tick. */
    public static int drainClientTasks() {
        int drained = 0;
        Runnable task;
        while ((task = CLIENT_TASKS.poll()) != null) {
            task.run();
            drained++;
        }
        return drained;
    }

    private static void enqueueClientTask(Runnable task) {
        CLIENT_TASKS.add(Objects.requireNonNull(task, "task"));
    }

    public static synchronized void clear() {
        clearCurrent();
        CloudState.reset();
    }

    private static RuntimeState requireState() {
        RuntimeState state = current;
        if (state == null) {
            throw new IllegalStateException("Cloud client runtime is not configured");
        }
        return state;
    }

    private static void clearCurrent() {
        RuntimeState previous = current;
        current = null;
        if (previous != null) {
            previous.assetCatalog().clear();
            previous.animations().clear();
            previous.entityCoordinator().deactivate();
            previous.observations().close();
            previous.scopeLifecycle().close();
            previous.realtime().close();
        }
    }

    public static final class RuntimeState {
        private final CloudInstanceConfig instance;
        private final CloudSession session;
        private final CloudHttpClient http;
        private final CloudAssetClient assets;
        private final CloudAssetCache assetCache;
        private final CloudAssetMaterializationCoordinator assetMaterialization;
        private final CloudAssetCatalogStore assetCatalog;
        private final CloudScopeClient scopes;
        private final CloudIdentityClient identities;
        private final CloudIdentityBindingClient identityBindings;
        private final CloudAppearanceStore appearances;
        private final CloudAnimationStore animations;
        private final CloudEntityBindingResolver bindingResolver;
        private final CloudEntityClientCoordinator entityCoordinator;
        private final CloudRealtimeClient realtime;
        private final CloudScopeLifecycle scopeLifecycle;
        private final CloudEntityObservationCoordinator observations;
        private final Path cacheRoot;
        private volatile CloudInstanceInfo instanceInfo;

        private RuntimeState(
                CloudInstanceConfig instance,
                CloudSession session,
                CloudHttpClient http,
                CloudAssetClient assets,
                CloudAssetCache assetCache,
                CloudScopeClient scopes,
                CloudIdentityClient identities,
                CloudIdentityBindingClient identityBindings,
                CloudAppearanceStore appearances,
                CloudAnimationStore animations,
                CloudEntityBindingResolver bindingResolver,
                CloudEntityClientCoordinator entityCoordinator,
                CloudRealtimeClient realtime,
                Path cacheRoot
        ) {
            this.instance = instance;
            this.session = session;
            this.http = http;
            this.assets = assets;
            this.assetCache = assetCache;
            this.assetCatalog = new CloudAssetCatalogStore();
            this.assetMaterialization = new CloudAssetMaterializationCoordinator(
                    assetCatalog, ref -> assetCache.downloadAndStore(assets, ref, cacheRoot));
            this.scopes = scopes;
            this.identities = identities;
            this.identityBindings = identityBindings;
            this.appearances = appearances;
            this.animations = animations;
            this.bindingResolver = bindingResolver;
            this.entityCoordinator = entityCoordinator;
            this.realtime = realtime;
            this.scopeLifecycle = new CloudScopeLifecycle(scopes, realtime, appearances);
            this.observations = new CloudEntityObservationCoordinator(scopes, bindingResolver);
            this.cacheRoot = cacheRoot;
        }

        public CloudInstanceConfig instance() { return instance; }
        public CloudSession session() { return session; }
        public CloudHttpClient http() { return http; }
        public CloudAssetClient assets() { return assets; }
        public CloudAssetCache assetCache() { return assetCache; }
        public CloudAssetMaterializationCoordinator assetMaterialization() { return assetMaterialization; }
        public CloudAssetCatalogStore assetCatalog() { return assetCatalog; }
        public CloudScopeClient scopes() { return scopes; }
        public CloudIdentityClient identities() { return identities; }
        public CloudIdentityBindingClient identityBindings() { return identityBindings; }
        public CloudAppearanceStore appearances() { return appearances; }
        public CloudAnimationStore animations() { return animations; }
        public CloudEntityBindingResolver bindingResolver() { return bindingResolver; }
        public CloudEntityClientCoordinator entityCoordinator() { return entityCoordinator; }
        public CloudRealtimeClient realtime() { return realtime; }
        public CloudScopeLifecycle scopeLifecycle() { return scopeLifecycle; }
        public CloudEntityObservationCoordinator observations() { return observations; }
        public Path cacheRoot() { return cacheRoot; }
        public CloudInstanceInfo instanceInfo() { return instanceInfo; }

        private ModelUploadTransport uploadTransport() {
            return new CloudUploadTransport(assets);
        }
    }
}
