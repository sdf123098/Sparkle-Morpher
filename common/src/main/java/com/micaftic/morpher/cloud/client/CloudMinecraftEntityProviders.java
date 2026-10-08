package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

/**
 * Registers the built-in client adapters for the three explicitly bindable
 * Cloud entity kinds. The adapter never infers a kind or creates a binding;
 * it only resolves UUIDs already present in the Cloud binding snapshot.
 */
public final class CloudMinecraftEntityProviders {
    private static CloudClientRuntime.RuntimeState registeredRuntime;
    private static final Map<LivingAnimatable<?>, AppearanceRequest> MATERIALIZING = new ConcurrentHashMap<>();
    private static final Map<LivingAnimatable<?>, AppearanceRequest> REQUESTS = new IdentityHashMap<>();
    private static boolean privacySuspended;
    private static int permissionChecks;
    private static long nextPermissionCheck;

    private static final class AppearanceRequest {
        final LivingAnimatable<?> capability;
        final CloudScopeClient.CloudAppearance appearance;
        final CloudClientRuntime.RuntimeState runtime;
        final CloudAssetIdentity identity;
        java.util.function.BooleanSupplier current;
        String ownedModel;
        boolean applied;
        long retryAt;
        final CloudDisplayPermissionLease permission = new CloudDisplayPermissionLease();

        AppearanceRequest(LivingAnimatable<?> capability, CloudScopeClient.CloudAppearance appearance,
                          CloudClientRuntime.RuntimeState runtime, CloudAssetIdentity identity) {
            this.capability = capability;
            this.appearance = appearance;
            this.runtime = runtime;
            this.identity = identity;
            ownedModel = capability.getModelId();
        }
    }

    private CloudMinecraftEntityProviders() {
    }

    public static void tick() {
        CloudClientRuntime.RuntimeState runtime = CloudClientRuntime.state();
        CapabilityProvider.reconcile();
        if (runtime == null) return;
        if (registeredRuntime != runtime) {
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.PLAYER));
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.FAKE_PLAYER));
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.MAID));
            registeredRuntime = runtime;
        }
        if (com.micaftic.morpher.client.PrivacyMode.isActive()) {
            if (!privacySuspended) runtime.entityCoordinator().deactivate();
            privacySuspended = true;
            return;
        }
        if (privacySuspended) {
            String scope = runtime.scopeLifecycle().activeScopeId();
            String epoch = runtime.scopeLifecycle().activeWorldEpoch();
            if (scope != null && epoch != null)
                runtime.entityCoordinator().activate(scope, epoch, CloudClientRuntime.currentWorldGeneration());
            privacySuspended = false;
        }
        CloudSelectedModelRecovery.tick(runtime);
        CloudClientRuntime.tickEntityObservations();
    }

    private static final class CapabilityProvider implements CloudEntityProvider {
        private final Kind kind;

        private CapabilityProvider(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public boolean isAvailable(UUID entityUuid) {
            Entity entity = entity(entityUuid);
            if (entity == null) return false;
            return switch (kind) {
                case PLAYER, FAKE_PLAYER -> PlayerCapability.get(entity).isPresent();
                case MAID -> MaidCapability.get(entity).isPresent();
            };
        }

        @Override
        public void applyAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
            Entity entity = entity(entityUuid);
            if (entity == null || entity == Minecraft.getInstance().player) return;
            if (kind == Kind.MAID) {
                MaidCapability.get(entity).ifPresent(capability -> applyModel(capability, appearance, bindingRevision));
            } else {
                PlayerCapability.get(entity).ifPresent(capability -> applyModel(capability, appearance, bindingRevision));
            }
        }

        private static void applyModel(LivingAnimatable<?> capability,
                                       CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
            CloudClientRuntime.RuntimeState runtime = CloudClientRuntime.state();
            CloudAssetIdentity identity = appearance.assetId() == null || appearance.assetId().isBlank() || runtime == null
                    ? null : cloudIdentity(runtime, appearance);
            if (runtime == null || REQUESTS.size() >= 4096 && !REQUESTS.containsKey(capability)) return;
            Minecraft client = Minecraft.getInstance();
            Object expectedLevel = client.level, expectedConnection = client.getConnection();
            long scopeGeneration = runtime.scopeLifecycle().contextGeneration();
            String scope = runtime.scopeLifecycle().activeScopeId(), epoch = runtime.scopeLifecycle().activeWorldEpoch();
            var binding = runtime.bindingResolver().snapshot(scope, epoch).stream().filter(entry ->
                entry.entityUuid().equals(capability.getEntity().getUUID().toString())
                && entry.targetId().equals(appearance.targetId()) && entry.revision() == bindingRevision).findFirst().orElse(null);
            AppearanceRequest request = new AppearanceRequest(capability, appearance, runtime, identity);
            request.current = () -> REQUESTS.get(capability) == request && CloudClientRuntime.state() == runtime
                && !com.micaftic.morpher.client.PrivacyMode.isActive()
                && client.level == expectedLevel && client.getConnection() == expectedConnection
                && runtime.scopeLifecycle().contextGeneration() == scopeGeneration
                && Objects.equals(runtime.scopeLifecycle().activeScopeId(), scope)
                && Objects.equals(runtime.scopeLifecycle().activeWorldEpoch(), epoch)
                && binding != null && runtime.bindingResolver().isCurrent(scope, epoch, binding)
                && !capability.getEntity().isRemoved()
                && client.level.getEntity(capability.getEntity().getId()) == capability.getEntity()
                && Objects.equals(appearance, runtime.appearances().get(scope, appearance.targetId()));
            REQUESTS.put(capability, request);
            capability.awaitAsyncResult();
            capability.setForceDisabled(true);
            if (request.current.getAsBoolean()) tryApply(request);
        }

        private static void reconcile() {
            var iterator = REQUESTS.entrySet().iterator();
            while (iterator.hasNext()) {
                AppearanceRequest request = iterator.next().getValue();
                if (!request.current.getAsBoolean()) {
                    LivingAnimatable<?> cap = request.capability;
                    if (Objects.equals(cap.getModelId(), request.ownedModel)) {
                        cap.awaitAsyncResult();
                        cap.resetModel();
                        cap.setForceDisabled(true);
                    }
                    iterator.remove();
                } else {
                    long now = System.nanoTime();
                    if (permissionChecks < 8 && now - nextPermissionCheck >= 0 && request.permission.start(now)) {
                        nextPermissionCheck = now + 250_000_000L;
                        permissionChecks++;
                        CloudAppearanceAuthorization.check(request.runtime.scopes(), request.runtime.assets(), request.appearance)
                            .whenComplete((allowed, failure) -> Minecraft.getInstance().execute(() -> {
                                permissionChecks--;
                                if (!request.current.getAsBoolean()) return;
                                request.permission.complete(failure == null && Boolean.TRUE.equals(allowed), System.nanoTime());
                            }));
                    }
                    if (!request.permission.valid(now)) {
                        if (request.applied && Objects.equals(request.capability.getModelId(), request.ownedModel)) {
                            request.capability.awaitAsyncResult();
                            request.capability.resetModel();
                            request.capability.setForceDisabled(true);
                            request.ownedModel = request.capability.getModelId();
                            request.applied = false;
                        }
                    } else if (!request.applied && now - request.retryAt >= 0) {
                        tryApply(request);
                    }
                }
            }
        }

        private static void tryApply(AppearanceRequest request) {
            if (!request.current.getAsBoolean() || !request.permission.valid(System.nanoTime())) return;
            LivingAnimatable<?> capability = request.capability;
            var appearance = request.appearance;
            capability.awaitAsyncResult();
            if (capability instanceof PlayerCapability player) player.isDisabled = appearance.disabled();
            if (appearance.disabled()) {
                capability.setForceDisabled(true);
                request.applied = true;
                return;
            }
            if (appearance.assetId() == null || appearance.assetId().isBlank()) {
                capability.resetModel();
                request.ownedModel = capability.getModelId();
                request.applied = true;
                return;
            }
            String modelId = request.identity == null ? null : request.identity.runtimeModelId();
            if (modelId != null && ClientModelManager.getAvailableModelIds().contains(modelId)) {
                capability.initModelWithTexture(modelId, appearance.textureId() == null ? "default" : appearance.textureId());
                capability.setForceDisabled(false);
                request.ownedModel = modelId;
                request.applied = true;
                return;
            }
            capability.setForceDisabled(true);
            if (request.identity == null || MATERIALIZING.containsKey(capability)) return;
            request.retryAt = System.nanoTime() + 5_000_000_000L;
            if (MATERIALIZING.size() >= 128) return;
            MATERIALIZING.put(capability, request);
            var runtime = request.runtime;
            var ref = new CloudAssetRef(appearance.assetId(), appearance.assetRevision(), appearance.rawSha256());
            var catalog = runtime.assetCatalog().get(ref.assetId());
            String fallbackFormat = catalog != null && catalog.ref().equals(ref) ? catalog.format() : null;
            runtime.assetCache().downloadForDisplay(runtime.assets(), ref, runtime.cacheRoot(), fallbackFormat)
                .thenApplyAsync(asset -> {
                    try {
                        byte[] bytes = Files.readAllBytes(asset.path());
                        return new ImportData(CloudAssetImportName.fileName(
                            new CloudAssetSummary(ref, ref.assetId(), asset.format(), bytes.length)), bytes);
                    } catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
                }).whenComplete((data, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null || !request.current.getAsBoolean() || !request.permission.valid(System.nanoTime())) {
                        MATERIALIZING.remove(capability, request);
                        request.retryAt = System.nanoTime() + 5_000_000_000L;
                        return;
                    }
                    try {
                        ClientModelManager.importLocalModel(modelId, data.fileName(), data.bytes(), failure ->
                            Minecraft.getInstance().execute(() -> {
                                MATERIALIZING.remove(capability, request);
                                request.retryAt = System.nanoTime() + 5_000_000_000L;
                                if (failure == null && request.current.getAsBoolean()) tryApply(request);
                            }));
                    } catch (RuntimeException failure) {
                        MATERIALIZING.remove(capability, request);
                        request.retryAt = System.nanoTime() + 5_000_000_000L;
                    }
                }));
        }

        private record ImportData(String fileName, byte[] bytes) {}

        private static CloudAssetIdentity cloudIdentity(CloudClientRuntime.RuntimeState runtime,
                                                        CloudScopeClient.CloudAppearance appearance) {
            if (appearance.assetRevision() == null || appearance.rawSha256() == null || appearance.rawSha256().isBlank()) return null;
            return new CloudAssetIdentity(runtime.instance().instanceId(), "catalog", appearance.assetId(),
                    Long.toString(appearance.assetRevision()), appearance.rawSha256());
        }


        private static Entity entity(UUID entityUuid) {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return null;
            try {
                Method byUuid = client.level.getClass().getMethod("getEntity", UUID.class);
                Object resolved = byUuid.invoke(client.level, entityUuid);
                if (resolved instanceof Entity entity) return entity;
            } catch (ReflectiveOperationException ignored) {
                // 1.21.1 exposes the integer-id overload instead; use the
                // reflective iterable fallbacks below so this common source
                // remains binary-safe across the supported client versions.
            }
            for (String methodName : new String[]{"entitiesForRendering", "getAllEntities"}) {
                try {
                    Method all = client.level.getClass().getMethod(methodName);
                    Object value = all.invoke(client.level);
                    if (value instanceof Iterable<?> iterable) {
                        for (Object candidate : iterable) {
                            if (candidate instanceof Entity entity && entityUuid.equals(entity.getUUID())) return entity;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Optional loader/version-specific lookup surface.
                }
            }
            for (var player : client.level.players()) {
                if (entityUuid.equals(player.getUUID())) return player;
            }
            return null;
        }
    }
}
