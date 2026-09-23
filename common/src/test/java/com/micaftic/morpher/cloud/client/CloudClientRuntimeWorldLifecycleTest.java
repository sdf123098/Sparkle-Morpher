package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudClientRuntimeWorldLifecycleTest {
    @Test
    void delayedDisconnectFromPreviousConnectionCannotInvalidateCurrentWorld() {
        Object oldConnection = new Object();
        Object currentConnection = new Object();
        try {
            long oldGeneration = CloudClientRuntime.onWorldJoined(oldConnection);
            long currentGeneration = CloudClientRuntime.onWorldJoined(currentConnection);
            CloudClientRuntime.onWorldLeft(oldConnection);

            assertTrue(currentGeneration > oldGeneration);
            assertTrue(CloudClientRuntime.isCurrentWorldGeneration(currentGeneration));
            CloudClientRuntime.onWorldLeft(currentConnection);
            assertFalse(CloudClientRuntime.isCurrentWorldGeneration(currentGeneration));
        } finally {
            CloudClientRuntime.onWorldLeft(currentConnection);
        }
    }

    @Test
    void scopeCannotBeJoinedWithoutAnActiveMinecraftWorld() {
        Object sentinel = new Object();
        try {
            CloudClientRuntime.onWorldLeft(sentinel);
            assertThrows(IllegalStateException.class,
                    () -> CloudClientRuntime.joinScope("scope-a", "epoch-1"));
        } finally {
            CloudClientRuntime.onWorldLeft(sentinel);
        }
    }
}
