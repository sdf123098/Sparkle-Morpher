package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEntityObservationTrackerTest {
    private static final UUID ENTITY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void emitsOnlyVisibilityTransitionsForCurrentBindings() {
        CloudScopeClient.CloudEntityBinding binding = new CloudScopeClient.CloudEntityBinding(
                "binding", "scope", "epoch", ENTITY.toString(), "PLAYER", "target", "UNKNOWN", null, 1L);
        CloudEntityObservationTracker tracker = new CloudEntityObservationTracker();

        List<CloudEntityObservationTracker.Observation> first = tracker.update(
                List.of(binding), Set.of("binding"));
        assertEquals(List.of(new CloudEntityObservationTracker.Observation(
                "binding", ENTITY, "PLAYER", CloudEntityObservationCoordinator.ObservationState.VISIBLE)), first);

        assertTrue(tracker.update(List.of(binding), Set.of("binding")).isEmpty());

        assertEquals(List.of(new CloudEntityObservationTracker.Observation(
                "binding", ENTITY, "PLAYER", CloudEntityObservationCoordinator.ObservationState.NOT_VISIBLE)),
                tracker.update(List.of(binding), Set.of()));
    }

    @Test
    void dropsStateForBindingsThatLeaveTheSnapshot() {
        CloudScopeClient.CloudEntityBinding binding = new CloudScopeClient.CloudEntityBinding(
                "binding", "scope", "epoch", ENTITY.toString(), "PLAYER", "target", "UNKNOWN", null, 1L);
        CloudEntityObservationTracker tracker = new CloudEntityObservationTracker();

        tracker.update(List.of(binding), Set.of("binding"));

        assertTrue(tracker.update(List.of(), Set.of()).isEmpty());
        assertEquals(0, tracker.trackedBindingCount());
    }
}
