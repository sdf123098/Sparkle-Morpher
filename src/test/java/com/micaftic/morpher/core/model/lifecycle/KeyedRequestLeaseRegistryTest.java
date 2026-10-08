package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyedRequestLeaseRegistryTest {
    @Test
    void onlyOneRequestOwnsAKeyAtATime() {
        KeyedRequestLeaseRegistry<String> requests = new KeyedRequestLeaseRegistry<>();

        KeyedRequestLeaseRegistry.Lease<String> first = requests.begin("model");

        assertNotNull(first);
        assertNull(requests.begin("model"));
        assertTrue(requests.isCurrent(first));
    }

    @Test
    void oldCompletionCannotClearAReplacementRequest() {
        KeyedRequestLeaseRegistry<String> requests = new KeyedRequestLeaseRegistry<>();
        KeyedRequestLeaseRegistry.Lease<String> oldRequest = requests.begin("model");

        assertTrue(requests.invalidate("model"));
        KeyedRequestLeaseRegistry.Lease<String> replacement = requests.begin("model");

        assertNotNull(replacement);
        assertFalse(requests.complete(oldRequest));
        assertTrue(requests.isCurrent(replacement));
    }

    @Test
    void completionOnlyRemovesItsOwnLiveRequest() {
        KeyedRequestLeaseRegistry<String> requests = new KeyedRequestLeaseRegistry<>();
        KeyedRequestLeaseRegistry.Lease<String> request = requests.begin("model");

        assertTrue(requests.complete(request));
        assertFalse(requests.complete(request));
        assertFalse(requests.isInFlight("model"));
    }

    @Test
    void shutdownInvalidatesEveryRequestLease() {
        KeyedRequestLeaseRegistry<String> requests = new KeyedRequestLeaseRegistry<>();
        KeyedRequestLeaseRegistry.Lease<String> first = requests.begin("first");
        KeyedRequestLeaseRegistry.Lease<String> second = requests.begin("second");

        requests.clearAll();

        assertFalse(requests.isCurrent(first));
        assertFalse(requests.isCurrent(second));
        assertFalse(requests.isInFlight("first"));
        assertFalse(requests.isInFlight("second"));
    }
}
