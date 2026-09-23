package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.client.upload.CloudUploadTransport;
import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
        CloudRealtimeClient realtime = new CloudRealtimeClient(
                instance,
                session.accessToken(),
                clientVersion,
                messageConsumer,
                eventConsumer);
        RuntimeState next = new RuntimeState(
                instance,
                session,
                http,
                assets,
                new CloudAssetCache(),
                new CloudScopeClient(http),
                new CloudIdentityClient(http),
                new CloudIdentityBindingClient(http),
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

    public static CompletableFuture<Void> joinScope(String scopeId, String worldEpoch) {
        return requireState().realtime().joinScope(scopeId, worldEpoch);
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
            previous.realtime().close();
        }
    }

    public static final class RuntimeState {
        private final CloudInstanceConfig instance;
        private final CloudSession session;
        private final CloudHttpClient http;
        private final CloudAssetClient assets;
        private final CloudAssetCache assetCache;
        private final CloudScopeClient scopes;
        private final CloudIdentityClient identities;
        private final CloudIdentityBindingClient identityBindings;
        private final CloudRealtimeClient realtime;
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
                CloudRealtimeClient realtime,
                Path cacheRoot
        ) {
            this.instance = instance;
            this.session = session;
            this.http = http;
            this.assets = assets;
            this.assetCache = assetCache;
            this.scopes = scopes;
            this.identities = identities;
            this.identityBindings = identityBindings;
            this.realtime = realtime;
            this.cacheRoot = cacheRoot;
        }

        public CloudInstanceConfig instance() { return instance; }
        public CloudSession session() { return session; }
        public CloudHttpClient http() { return http; }
        public CloudAssetClient assets() { return assets; }
        public CloudAssetCache assetCache() { return assetCache; }
        public CloudScopeClient scopes() { return scopes; }
        public CloudIdentityClient identities() { return identities; }
        public CloudIdentityBindingClient identityBindings() { return identityBindings; }
        public CloudRealtimeClient realtime() { return realtime; }
        public Path cacheRoot() { return cacheRoot; }
        public CloudInstanceInfo instanceInfo() { return instanceInfo; }

        private ModelUploadTransport uploadTransport() {
            return new CloudUploadTransport(assets);
        }
    }
}
