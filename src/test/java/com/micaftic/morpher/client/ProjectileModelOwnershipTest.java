package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileModelOwnershipTest {
    @Test
    void modelStaysResidentUntilTheLastProjectileLeaseCloses() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease first = residency.acquire("cloud:arrow");
        RuntimeModelLease second = residency.acquire("cloud:arrow");
        AtomicInteger retirements = new AtomicInteger();

        assertTrue(residency.deferWhileConsumed("cloud:arrow", retirements::incrementAndGet));
        assertEquals(0, retirements.get());

        first.close();
        assertEquals(1, residency.consumerCount("cloud:arrow"));
        assertEquals(0, retirements.get());

        second.close();
        assertEquals(0, residency.consumerCount("cloud:arrow"));
        assertEquals(1, retirements.get());
    }

    @Test
    void retirementIsDeferredOnlyWhileAConsumerExists() {
        ClientModelResidency residency = new ClientModelResidency();
        AtomicInteger retirements = new AtomicInteger();

        assertFalse(residency.deferWhileConsumed("cloud:arrow", retirements::incrementAndGet));
        assertEquals(0, retirements.get());

        RuntimeModelLease lease = residency.acquire("cloud:arrow");
        assertTrue(residency.deferWhileConsumed("cloud:arrow", retirements::incrementAndGet));
        lease.close();
        lease.close();
        assertEquals(1, retirements.get());
    }

    @Test
    void consumedIdsAreProtectedAndEmptyAfterRelease() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease lease = residency.acquire("Cloud/Arrow");
        assertTrue(residency.consumedModelIds().contains("cloud/arrow"));
        lease.close();
        assertTrue(residency.consumedModelIds().isEmpty());
    }
}
