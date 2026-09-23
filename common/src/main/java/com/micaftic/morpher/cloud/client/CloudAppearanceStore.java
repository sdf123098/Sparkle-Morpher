package com.micaftic.morpher.cloud.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Latest Cloud appearance snapshot for the currently observed scopes.
 *
 * <p>This is a protocol-side store only. Minecraft entity lookup, world
 * generation checks and render-thread application stay in the loader client
 * adapter. Revisions are applied monotonically so a delayed event cannot
 * roll a target back.</p>
 */
public final class CloudAppearanceStore {
    private final Map<String, Map<String, CloudScopeClient.CloudAppearance>> byScope = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BiConsumer<String, CloudScopeClient.CloudAppearance>> listeners =
            new CopyOnWriteArrayList<>();

    public boolean apply(CloudRealtimeClient.CloudRealtimeEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"AppearanceState".equals(event.kind()) || event.appearance() == null || event.scopeId().isBlank()) {
            return false;
        }
        return apply(event.scopeId(), event.appearance());
    }

    public boolean apply(String scopeId, CloudScopeClient.CloudAppearance appearance) {
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(appearance, "appearance");
        if (scopeId.isBlank()) throw new IllegalArgumentException("scopeId must not be blank");
        Map<String, CloudScopeClient.CloudAppearance> scope = byScope.computeIfAbsent(scopeId, ignored -> new ConcurrentHashMap<>());
        final boolean[] accepted = {false};
        scope.compute(appearance.targetId(), (ignored, previous) -> {
            if (previous != null && appearance.revision() <= previous.revision()) return previous;
            accepted[0] = true;
            return appearance;
        });
        if (accepted[0]) {
            for (BiConsumer<String, CloudScopeClient.CloudAppearance> listener : listeners) {
                listener.accept(scopeId, appearance);
            }
        }
        return accepted[0];
    }

    public void addListener(BiConsumer<String, CloudScopeClient.CloudAppearance> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(BiConsumer<String, CloudScopeClient.CloudAppearance> listener) {
        if (listener != null) listeners.remove(listener);
    }

    public void applyRecovery(String scopeId, CloudScopeClient.CloudEventRecovery recovery) {
        Objects.requireNonNull(recovery, "recovery");
        for (CloudScopeClient.CloudRecoveredEvent event : recovery.events()) {
            if (event.appearance() != null) apply(scopeId, event.appearance());
        }
    }

    public CloudScopeClient.CloudAppearance get(String scopeId, String targetId) {
        Map<String, CloudScopeClient.CloudAppearance> scope = byScope.get(scopeId);
        return scope == null ? null : scope.get(targetId);
    }

    public Map<String, CloudScopeClient.CloudAppearance> snapshot(String scopeId) {
        Map<String, CloudScopeClient.CloudAppearance> scope = byScope.get(scopeId);
        return scope == null ? Map.of() : Map.copyOf(scope);
    }

    public void clearScope(String scopeId) {
        if (scopeId != null) byScope.remove(scopeId);
    }

    public void clear() {
        byScope.clear();
        listeners.clear();
    }
}
