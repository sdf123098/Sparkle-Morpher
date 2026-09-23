package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudAnimationStoreTest {
    @Test
    void acceptsMonotonicAnimationRevisionsAndDropsExpiredState() {
        CloudAnimationStore store = new CloudAnimationStore();
        CloudAnimationState first = new CloudAnimationState(
                "target", 1, "body", "PLAY", "idle", System.currentTimeMillis() + 1_000);
        assertTrue(store.apply("scope", first));
        assertFalse(store.apply("scope", new CloudAnimationState(
                "target", 1, "body", "STOP", "idle", System.currentTimeMillis() + 1_000)));
        assertEquals(first, store.get("scope", "target", "body"));
        assertTrue(store.apply("scope", new CloudAnimationState(
                "target", 2, "body", "PLAY", "run", System.currentTimeMillis() - 1)));
        assertNull(store.get("scope", "target", "body"));
    }
}
