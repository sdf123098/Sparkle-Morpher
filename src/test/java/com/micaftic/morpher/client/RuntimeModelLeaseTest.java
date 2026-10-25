package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeModelLeaseTest {
    @Test
    void closingALeaseIsIdempotent() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease lease = residency.acquire("cloud:model");
        assertTrue(lease.isValid());
        assertEquals(1, residency.consumerCount("cloud:model"));
        lease.close();
        lease.close();
        assertEquals(0, residency.consumerCount("cloud:model"));
        assertFalse(lease.isValid());
    }

    @Test
    void leaseIdsAreCanonicalAndIndependent() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease first = residency.acquire(" Cloud/Model ");
        RuntimeModelLease second = residency.acquire("cloud/model");
        assertEquals("cloud/model", first.runtimeModelId());
        assertEquals("cloud/model", second.runtimeModelId());
        assertEquals(2, residency.consumerCount("cloud/model"));
        first.close();
        assertTrue(second.isValid());
        assertEquals(1, residency.consumerCount("cloud/model"));
        second.close();
        assertEquals(0, residency.consumerCount("cloud/model"));
    }
}
