package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;
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

        assertFalse(coordinator.applyAppearance("scope", "epoch", 1L, "target"));
        assertFalse(coordinator.applyAppearance("scope", "epoch", 1L, "missing"));
        assertTrue(maid.applied.isEmpty());
    }

    private static CloudScopeClient.CloudEntityBinding binding(long revision) {
        return new CloudScopeClient.CloudEntityBinding(
                "binding", "scope", "epoch", ENTITY.toString(), "PLAYER", "target", "VISIBLE", null, revision);
    }

    private static CloudScopeClient.CloudAppearance appearance(long revision) {
        return new CloudScopeClient.CloudAppearance("target", revision, "asset", 1L, "sha", "default", 1.0f, false);
    }

    private static final class RecordingProvider implements CloudEntityProvider {
        private final Kind kind;
        private final java.util.ArrayList<AppliedAppearance> applied = new java.util.ArrayList<>();

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
    }

    private record AppliedAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
    }
}
