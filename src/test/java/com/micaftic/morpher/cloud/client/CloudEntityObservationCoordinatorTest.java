package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEntityObservationCoordinatorTest {
    @Test
    void reportsOnlyBindingsFromTheCurrentResolverContext() {
        CloudInstanceConfig instance = CloudInstanceConfig.v1("test", URI.create("https://127.0.0.1:1"));
        CloudEntityBindingResolver resolver = new CloudEntityBindingResolver();
        CloudEntityObservationCoordinator coordinator = new CloudEntityObservationCoordinator(
                new CloudScopeClient(new CloudHttpClient(instance)), resolver);
        try {
            coordinator.enter("scope", "epoch");
            CloudEntityObservationCoordinator.ObservationResult result = coordinator.report(
                    UUID.randomUUID(), "PLAYER", CloudEntityObservationCoordinator.ObservationState.VISIBLE).join();

            assertEquals(CloudEntityBindingResolver.Status.STALE_CONTEXT, result.status());
            assertFalse(result.submitted());
            coordinator.leave();
            assertTrue(coordinator.activeScopeId().isBlank());
        } finally {
            coordinator.close();
        }
    }
}
