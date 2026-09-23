package com.micaftic.morpher.cloud.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Connects Cloud binding/appearance state to loader-specific client adapters.
 *
 * <p>Network callbacks may invoke this class from any thread. The supplied
 * executor is responsible for switching to the client thread before the
 * provider touches Minecraft state. World generation and binding revision are
 * checked again inside that executor.</p>
 */
public final class CloudEntityClientCoordinator {
    private final CloudEntityBindingResolver bindings;
    private final CloudAppearanceStore appearances;
    private final LongSupplier currentWorldGeneration;
    private final Consumer<Runnable> clientExecutor;
    private final Map<CloudEntityProvider.Kind, CopyOnWriteArrayList<CloudEntityProvider>> providers =
            new EnumMap<>(CloudEntityProvider.Kind.class);

    public CloudEntityClientCoordinator(
            CloudEntityBindingResolver bindings,
            CloudAppearanceStore appearances,
            LongSupplier currentWorldGeneration,
            Consumer<Runnable> clientExecutor
    ) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.appearances = Objects.requireNonNull(appearances, "appearances");
        this.currentWorldGeneration = Objects.requireNonNull(currentWorldGeneration, "currentWorldGeneration");
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        for (CloudEntityProvider.Kind kind : CloudEntityProvider.Kind.values()) {
            providers.put(kind, new CopyOnWriteArrayList<>());
        }
        appearances.addListener(this::onAppearanceChanged);
    }

    private volatile Context activeContext;

    public synchronized void activate(String scopeId, String worldEpoch, long worldGeneration) {
        requireText(scopeId, "scopeId");
        requireText(worldEpoch, "worldEpoch");
        activeContext = new Context(scopeId, worldEpoch, worldGeneration);
    }

    public synchronized void deactivate() {
        activeContext = null;
    }

    private void onAppearanceChanged(String scopeId, CloudScopeClient.CloudAppearance appearance) {
        Context context = activeContext;
        if (context != null && context.scopeId().equals(scopeId)) {
            applyAppearance(context.scopeId(), context.worldEpoch(), context.worldGeneration(), appearance.targetId());
        }
    }

    public void register(CloudEntityProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.get(provider.kind()).addIfAbsent(provider);
    }

    public void unregister(CloudEntityProvider provider) {
        if (provider != null) providers.get(provider.kind()).remove(provider);
    }

    /**
     * Schedules the current appearance for every explicitly bound entity of
     * the target. Returns false when the request is stale or has no applicable
     * binding/provider.
     */
    public boolean applyAppearance(String scopeId, String worldEpoch, long worldGeneration, String targetId) {
        requireText(scopeId, "scopeId");
        requireText(worldEpoch, "worldEpoch");
        requireText(targetId, "targetId");
        if (currentWorldGeneration.getAsLong() != worldGeneration) return false;
        CloudScopeClient.CloudAppearance appearance = appearances.get(scopeId, targetId);
        if (appearance == null) return false;

        AtomicBoolean scheduled = new AtomicBoolean();
        for (CloudScopeClient.CloudEntityBinding binding : bindings.snapshot(scopeId, worldEpoch)) {
            if (!targetId.equals(binding.targetId())) continue;
            CloudEntityProvider.Kind kind;
            try {
                kind = CloudEntityProvider.Kind.fromWireValue(binding.entityKind());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            UUID entityUuid;
            try {
                entityUuid = UUID.fromString(binding.entityUuid());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            for (CloudEntityProvider provider : providers.get(kind)) {
                scheduled.set(true);
                scheduleApply(provider, entityUuid, scopeId, worldEpoch, worldGeneration, binding, appearance);
            }
        }
        return scheduled.get();
    }

    private void scheduleApply(
            CloudEntityProvider provider,
            UUID entityUuid,
            String scopeId,
            String worldEpoch,
            long worldGeneration,
            CloudScopeClient.CloudEntityBinding binding,
            CloudScopeClient.CloudAppearance expectedAppearance
    ) {
        clientExecutor.accept(() -> {
            if (currentWorldGeneration.getAsLong() != worldGeneration
                    || !bindings.isCurrent(scopeId, worldEpoch, binding)) return;
            CloudScopeClient.CloudAppearance current = appearances.get(scopeId, expectedAppearance.targetId());
            if (current == null || current.revision() != expectedAppearance.revision()) return;
            provider.applyAppearance(entityUuid, current, binding.revision());
        });
    }

    public List<CloudEntityProvider> providers(CloudEntityProvider.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return List.copyOf(new ArrayList<>(providers.get(kind)));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private record Context(String scopeId, String worldEpoch, long worldGeneration) {
    }
}
