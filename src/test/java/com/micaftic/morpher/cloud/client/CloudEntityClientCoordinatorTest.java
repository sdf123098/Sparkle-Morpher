package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEntityClientCoordinatorTest {
    private static final UUID ENTITY = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void appliesAppearanceOnlyForExplicitBindingAndCurrentGeneration() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        resolver.replace("scope", "epoch", List.of(binding(3L)));
        CloudAppearanceStore appearances = new CloudAppearanceStore();
        appearances.apply("scope", appearance(4L));
        AtomicLong worldGeneration = new AtomicLong(8L);
        RecordingProvider provider = new RecordingProvider(CloudEntityProvider.Kind.PLAYER);
        CloudEntityClientCoordinator coordinator = new CloudEntityClientCoordinator(
                resolver, appearances, worldGeneration::get, Runnable::run);
        coordinator.register(provider);
        coordinator.activate("scope", "epoch", 8L);

        assertTrue(coordinator.applyAppearance("scope", "epoch", 8L, "target"));
        assertEquals(1, provider.applied.size());
        assertEquals(4L, provider.applied.get(0).appearance().revision());
        assertEquals(3L, provider.applied.get(0).bindingRevision());

        worldGeneration.set(9L);
        assertFalse(coordinator.applyAppearance("scope", "epoch", 8L, "target"));
        assertEquals(1, provider.applied.size());
    }

    @Test
    void rejectsProviderKindMismatchAndUnknownTarget() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        resolver.replace("scope", "epoch", List.of(binding(1L)));
        CloudAppearanceStore appearances = new CloudAppearanceStore();
        appearances.apply("scope", appearance(1L));
        RecordingProvider maid = new RecordingProvider(CloudEntityProvider.Kind.MAID);
        CloudEntityClientCoordinator coordinator = new CloudEntityClientCoordinator(
                resolver, appearances, () -> 1L, Runnable::run);
        coordinator.register(maid);
        coordinator.activate("scope", "epoch", 1L);

        assertFalse(coordinator.applyAppearance("scope", "epoch", 1L, "target"));
        assertFalse(coordinator.applyAppearance("scope", "epoch", 1L, "missing"));
        assertTrue(maid.applied.isEmpty());
    }

    @Test
    void observesOnlyExplicitBindingsAndOnlyWhenAvailabilityChanges() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        resolver.replace("scope", "epoch", List.of(binding(1L)));
        CloudEntityClientCoordinator coordinator = new CloudEntityClientCoordinator(
                resolver, new CloudAppearanceStore(), () -> 1L, Runnable::run);
        RecordingProvider provider = new RecordingProvider(CloudEntityProvider.Kind.PLAYER);
        provider.available = true;
        coordinator.register(provider);
        coordinator.activate("scope", "epoch", 1L);

        assertEquals(CloudEntityObservationCoordinator.ObservationState.VISIBLE,
                coordinator.collectObservations().get(0).state());
        assertTrue(coordinator.collectObservations().isEmpty());

        provider.available = false;
        assertEquals(CloudEntityObservationCoordinator.ObservationState.NOT_VISIBLE,
                coordinator.collectObservations().get(0).state());
    }

    @Test
    void queuedAppearanceCannotSurviveScopeExitOrReentry() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        resolver.replace("scope", "epoch", List.of(binding(1L)));
        CloudAppearanceStore appearances = new CloudAppearanceStore();
        appearances.apply("scope", appearance(1L));
        java.util.ArrayList<Runnable> queue = new java.util.ArrayList<>();
        RecordingProvider provider = new RecordingProvider(CloudEntityProvider.Kind.PLAYER);
        CloudEntityClientCoordinator coordinator = new CloudEntityClientCoordinator(
                resolver, appearances, () -> 1L, queue::add);
        coordinator.register(provider);
        assertFalse(coordinator.applyAppearance("scope", "epoch", 1L, "target"));
        coordinator.activate("scope", "epoch", 1L);
        assertTrue(coordinator.applyAppearance("scope", "epoch", 1L, "target"));
        coordinator.deactivate();
        queue.remove(0).run();
        assertTrue(provider.applied.isEmpty());
        coordinator.activate("scope", "epoch", 1L);
        coordinator.applyAppearance("scope", "epoch", 1L, "target");
        coordinator.activate("scope", "epoch", 1L);
        queue.remove(0).run();
        assertTrue(provider.applied.isEmpty(), "reentering the same scope must invalidate the old task");
        coordinator.applyAppearance("scope", "epoch", 1L, "target");
        queue.remove(0).run();
        assertEquals(1, provider.applied.size());
    }

    private static CloudScopeClient.CloudEntityBinding binding(long revision) {
        return new CloudScopeClient.CloudEntityBinding(
                "binding", "scope", "epoch", ENTITY.toString(), "PLAYER", "target", "VISIBLE", null, revision);
    }

    @Test
    void replaysCachedAppearanceWhenMaidAppearsAndRespawns() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        resolver.replace("scope", "epoch", List.of(new CloudScopeClient.CloudEntityBinding(
                "binding", "scope", "epoch", ENTITY.toString(), "MAID", "target", "REGISTERED", null, 1L)));
        CloudAppearanceStore appearances = new CloudAppearanceStore();
        appearances.apply("scope", appearance(2L));
        CloudEntityClientCoordinator coordinator = new CloudEntityClientCoordinator(
                resolver, appearances, () -> 1L, Runnable::run);
        RecordingProvider maid = new RecordingProvider(CloudEntityProvider.Kind.MAID);
        coordinator.register(maid);
        coordinator.activate("scope", "epoch", 1L);
        coordinator.collectObservations();
        assertTrue(maid.applied.isEmpty());
        maid.available = true;
        coordinator.collectObservations();
        assertEquals(1, maid.applied.size(), "appearance received before spawn must be applied on visibility");
        coordinator.collectObservations();
        assertEquals(1, maid.applied.size(), "unchanged visibility must not reapply each tick");
        maid.available = false;
        coordinator.collectObservations();
        maid.available = true;
        coordinator.collectObservations();
        assertEquals(2, maid.applied.size(), "same UUID respawn must restore the cached appearance");
    }

    private static CloudScopeClient.CloudAppearance appearance(long revision) {
        return new CloudScopeClient.CloudAppearance("target", revision, "asset", 1L, "sha", "default", 1.0f, false);
    }

    private static final class RecordingProvider implements CloudEntityProvider {
        private final Kind kind;
        private final java.util.ArrayList<AppliedAppearance> applied = new java.util.ArrayList<>();
        private boolean available;

        private RecordingProvider(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public void applyAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
            applied.add(new AppliedAppearance(entityUuid, appearance, bindingRevision));
        }

        @Override
        public boolean isAvailable(UUID entityUuid) {
            return available;
        }
    }

    private record AppliedAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
    }
}
