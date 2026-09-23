package com.micaftic.morpher.cloud.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Scope-local, monotonic animation state with client-side TTL enforcement. */
public final class CloudAnimationStore {
    private final Map<String, Map<String, CloudAnimationState>> byScope = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BiConsumer<String, CloudAnimationState>> listeners = new CopyOnWriteArrayList<>();

    public boolean apply(String scopeId, CloudAnimationState state) {
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(state, "state");
        if (scopeId.isBlank()) throw new IllegalArgumentException("scopeId must not be blank");
        Map<String, CloudAnimationState> scope = byScope.computeIfAbsent(scopeId, ignored -> new ConcurrentHashMap<>());
        boolean[] accepted = {false};
        String key = state.targetId() + "\u0000" + state.channel();
        scope.compute(key, (ignored, previous) -> {
            if (previous != null && state.revision() <= previous.revision()) return previous;
            accepted[0] = true;
            return state;
        });
        if (accepted[0]) listeners.forEach(listener -> listener.accept(scopeId, state));
        return accepted[0];
    }

    public int applyRecovery(String scopeId, java.util.List<CloudScopeClient.CloudRecoveredEvent> events) {
        Objects.requireNonNull(events, "events");
        int accepted = 0;
        for (CloudScopeClient.CloudRecoveredEvent event : events) {
            if (event != null && event.animation() != null && apply(scopeId, event.animation())) accepted++;
        }
        return accepted;
    }

    public CloudAnimationState get(String scopeId, String targetId, String channel) {
        Map<String, CloudAnimationState> scope = byScope.get(scopeId);
        if (scope == null) return null;
        CloudAnimationState state = scope.get(targetId + "\u0000" + channel);
        if (state != null && state.expiresAtUnixMs() <= System.currentTimeMillis()) {
            scope.remove(targetId + "\u0000" + channel, state);
            return null;
        }
        return state;
    }

    public void addListener(BiConsumer<String, CloudAnimationState> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void clearScope(String scopeId) {
        if (scopeId != null) byScope.remove(scopeId);
    }

    public void clear() {
        byScope.clear();
        listeners.clear();
    }
}
