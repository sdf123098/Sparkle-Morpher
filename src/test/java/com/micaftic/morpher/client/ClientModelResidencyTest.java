package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelResidencyTest {
    private static final String MODEL = "cloud_" + "b".repeat(64);

    @Test
    void multipleDisplaysShareOneRetirementUntilTheLastLeaseCloses() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease player = residency.acquire(MODEL);
        RuntimeModelLease entity = residency.acquire(MODEL.toUpperCase(java.util.Locale.ROOT));
        RuntimeModelLease preview = residency.acquire(" " + MODEL + " ");
        AtomicInteger retired = new AtomicInteger();

        assertEquals(3, residency.consumerCount(MODEL));
        assertTrue(residency.deferWhileConsumed(MODEL, retired::incrementAndGet));

        player.close();
        entity.close();
        assertEquals(1, residency.consumerCount(MODEL));
        assertEquals(0, retired.get());

        preview.close();
        assertEquals(0, residency.consumerCount(MODEL));
        assertEquals(1, retired.get());
    }

    @Test
    void reacquiringAfterAFullReleaseCreatesAFreshConsumerSet() {
        ClientModelResidency residency = new ClientModelResidency();
        residency.acquire(MODEL).close();
        assertFalse(residency.deferWhileConsumed(MODEL, () -> { }));

        RuntimeModelLease retry = residency.acquire(MODEL);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(residency.deferWhileConsumed(MODEL, retired::incrementAndGet));
        retry.close();
        assertEquals(1, retired.get());
    }

    @Test
    void closingAnAlreadyReleasedLeaseDoesNotRetireAgain() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease lease = residency.acquire(MODEL);
        AtomicInteger retired = new AtomicInteger();

        assertTrue(residency.deferWhileConsumed(MODEL, retired::incrementAndGet));
        lease.close();
        lease.close();

        assertEquals(1, retired.get());
        assertEquals(0, residency.consumerCount(MODEL));
    }

    @Test
    void switchingBetweenModelsRetiresOnlyTheReleasedModel() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease first = residency.acquire("cloud_" + "c".repeat(64));
        RuntimeModelLease second = residency.acquire("cloud_" + "d".repeat(64));
        AtomicInteger firstRetired = new AtomicInteger();
        AtomicInteger secondRetired = new AtomicInteger();

        assertTrue(residency.deferWhileConsumed(first.runtimeModelId(), firstRetired::incrementAndGet));
        assertTrue(residency.deferWhileConsumed(second.runtimeModelId(), secondRetired::incrementAndGet));

        first.close();
        assertEquals(1, firstRetired.get());
        assertEquals(0, secondRetired.get());
        assertEquals(1, residency.consumerCount(second.runtimeModelId()));

        second.close();
        assertEquals(1, secondRetired.get());
    }
}
