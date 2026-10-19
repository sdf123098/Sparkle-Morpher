package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelReloadBackoffTest {
    @Test
    void repeatedRenderLookupsDoNotRestartFailedResidentReloadEveryFrame() {
        ModelReloadBackoff<String> backoff = new ModelReloadBackoff<>();
        KeyedRequestLeaseRegistry<String> requests = new KeyedRequestLeaseRegistry<>();
        Object source = new Object();
        int attempts = 0;
        for (long now = 0; now < 120_000; now += 16) {
            if (!backoff.allows("resident", source, now)) continue;
            var lease = requests.begin("resident");
            assertNotNull(lease);
            attempts++;
            backoff.failed("resident", source, now);
            assertTrue(requests.complete(lease));
        }
        assertEquals(6, attempts, "A fast failure must not reparse this source on every render lookup");
        assertFalse(requests.isInFlight("resident"), "Cooldown must not retain the request lease");
    }

    @Test
    void retryDelayGrowsToOneMinuteAndStillAllowsRecovery() {
        ModelReloadBackoff<String> backoff = new ModelReloadBackoff<>();
        Object source = new Object();
        long now = 0;
        for (long delay : new long[]{2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000}) {
            assertTrue(backoff.allows("model", source, now));
            backoff.failed("model", source, now);
            assertFalse(backoff.allows("model", source, now + delay - 1));
            assertTrue(backoff.allows("model", source, now + delay));
            now += delay;
        }
        backoff.clear("model");
        backoff.failed("model", source, now);
        assertTrue(backoff.allows("model", source, now + 2_000), "Successful reload resets exponential delay");
    }

    @Test
    void replacementSourcesAndOtherModelsAreNotHeldByAnOldFailure() {
        ModelReloadBackoff<String> backoff = new ModelReloadBackoff<>();
        Object source = new Object();
        backoff.failed("model", source, 100);
        assertFalse(backoff.allows("model", source, 101));
        assertTrue(backoff.allows("model", new Object(), 101));
        assertTrue(backoff.allows("other", source, 101));
    }

    @Test
    void userRetryAndRuntimeShutdownClearTheCooldown() {
        ModelReloadBackoff<String> backoff = new ModelReloadBackoff<>();
        Object source = new Object();
        backoff.failed("selected", source, 100);
        backoff.failed("other", source, 100);
        backoff.clear("selected");
        assertTrue(backoff.allows("selected", source, 101));
        assertFalse(backoff.allows("other", source, 101));
        backoff.clearAll();
        assertTrue(backoff.allows("other", source, 101));
    }

}
