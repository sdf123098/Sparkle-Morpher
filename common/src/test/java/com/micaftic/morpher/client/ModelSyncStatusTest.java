package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSyncStatusTest {
    @Test
    void aFreshSessionDoesNotWaitForARemovedServerManifest() {
        ClientModelManager.SyncStatus status = new ClientModelManager.SyncStatus();

        assertEquals(ClientModelManager.SyncState.IDLE, status.getCurrentState(),
                "Cloud models load on demand; a fresh world must not wait for a server model list");
        assertEquals(0L, status.getTerminalSinceMillis());
        assertNull(status.getMessage());
    }

    @Test
    void returningToIdleClearsThePreviousSessionProgressAndPopup() {
        ClientModelManager.SyncStatus status = new ClientModelManager.SyncStatus();
        status.startSyncing(2);
        assertEquals(ClientModelManager.SyncState.SYNCING, status.getCurrentState());
        assertEquals(2, status.getTotalModels());
        status.finishSuccess();
        assertTrue(status.getTerminalSinceMillis() > 0L);

        status.setState(ClientModelManager.SyncState.IDLE);

        assertEquals(ClientModelManager.SyncState.IDLE, status.getCurrentState());
        assertEquals(-1, status.getTotalModels());
        assertEquals(-1, status.getSyncedModels());
        assertEquals(0L, status.getTerminalSinceMillis());
        assertNull(status.getMessage());
    }
}
