package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudClientRuntimeTest {
    @Test
    void clearRemovesProcessLocalRuntime() {
        CloudClientRuntime.clear();

        assertFalse(CloudClientRuntime.isConfigured());
        assertEquals(Path.of("config", "sparkle-morpher", "cloud-cache"), CloudClientRuntime.defaultCacheRoot());
        assertThrows(IllegalStateException.class, CloudClientRuntime::uploadTransport);
    }
}
