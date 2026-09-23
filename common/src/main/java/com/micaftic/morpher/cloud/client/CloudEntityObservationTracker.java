package com.micaftic.morpher.cloud.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks visibility for the currently accepted entity bindings.
 *
 * <p>Client ticks may inspect the same loaded entity many times. This tracker
 * turns those inspections into edge-triggered observations and forgets
 * bindings that are no longer in the resolver snapshot. It deliberately
 * accepts only the binding ids supplied by the caller; it cannot create or
 * discover a Cloud binding.</p>
 */
public final class CloudEntityObservationTracker {
    private final Map<String, CloudEntityObservationCoordinator.ObservationState> states = new HashMap<>();

    public synchronized List<Observation> update(
            List<CloudScopeClient.CloudEntityBinding> bindings,
            Set<String> availableBindingIds
    ) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(availableBindingIds, "availableBindingIds");

        Set<String> currentBindingIds = new HashSet<>();
        List<Observation> changed = new ArrayList<>();
        for (CloudScopeClient.CloudEntityBinding binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            currentBindingIds.add(binding.bindingId());
            UUID entityUuid;
            try {
                entityUuid = UUID.fromString(binding.entityUuid());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            CloudEntityObservationCoordinator.ObservationState next = availableBindingIds.contains(binding.bindingId())
                    ? CloudEntityObservationCoordinator.ObservationState.VISIBLE
                    : CloudEntityObservationCoordinator.ObservationState.NOT_VISIBLE;
            if (states.put(binding.bindingId(), next) != next) {
                changed.add(new Observation(binding.bindingId(), entityUuid, binding.entityKind(), next));
            }
        }
        states.keySet().retainAll(currentBindingIds);
        return List.copyOf(changed);
    }

    public synchronized void clear() {
        states.clear();
    }

    public synchronized int trackedBindingCount() {
        return states.size();
    }

    public record Observation(
            String bindingId,
            UUID entityUuid,
            String entityKind,
            CloudEntityObservationCoordinator.ObservationState state
    ) {
        public Observation {
            Objects.requireNonNull(bindingId, "bindingId");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(entityKind, "entityKind");
            Objects.requireNonNull(state, "state");
        }
    }
}
