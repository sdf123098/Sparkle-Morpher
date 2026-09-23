package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudEntityBindingResolverTest {
    private static final UUID ENTITY = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void resolvesOnlyWithinScopeEpochAndKind() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        CloudScopeClient.CloudEntityBinding binding = new CloudScopeClient.CloudEntityBinding(
                "binding", "scope-a", "epoch-1", ENTITY.toString(), "PLAYER", "target", "OBSERVED", null, 2);

        resolver.replace("scope-a", "epoch-1", List.of(binding));

        assertEquals(CloudEntityBindingResolver.Status.BOUND, resolver.resolve("scope-a", "epoch-1", ENTITY, "PLAYER").status());
        assertEquals(CloudEntityBindingResolver.Status.KIND_MISMATCH, resolver.resolve("scope-a", "epoch-1", ENTITY, "FAKE_PLAYER").status());
        assertEquals(CloudEntityBindingResolver.Status.STALE_CONTEXT, resolver.resolve("scope-b", "epoch-1", ENTITY, "PLAYER").status());
        assertEquals(CloudEntityBindingResolver.Status.UNBOUND, resolver.resolve("scope-a", "epoch-1", UUID.randomUUID(), "PLAYER").status());
    }

    @Test
    void rejectsMixedScopeOrMalformedUuid() {
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        CloudScopeClient.CloudEntityBinding wrongEpoch = new CloudScopeClient.CloudEntityBinding(
                "binding", "scope-a", "epoch-2", ENTITY.toString(), "PLAYER", "target", "UNKNOWN", null, 1);
        assertThrows(IllegalArgumentException.class, () -> resolver.replace("scope-a", "epoch-1", List.of(wrongEpoch)));

        CloudScopeClient.CloudEntityBinding malformed = new CloudScopeClient.CloudEntityBinding(
                "binding", "scope-a", "epoch-1", "not-a-uuid", "PLAYER", "target", "UNKNOWN", null, 1);
        assertThrows(IllegalArgumentException.class, () -> resolver.replace("scope-a", "epoch-1", List.of(malformed)));
    }
}
