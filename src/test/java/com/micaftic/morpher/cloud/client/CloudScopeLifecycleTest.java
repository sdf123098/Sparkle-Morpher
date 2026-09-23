package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudScopeLifecycleTest {
    @Test
    void startsWithoutWorldScopeAndRejectsBlankContext() {
        CloudInstanceConfig instance = CloudInstanceConfig.v1("test", URI.create("https://127.0.0.1:1"));
        CloudRealtimeClient realtime = new CloudRealtimeClient(instance, "token", "2.0.0", ignored -> { });
        CloudScopeLifecycle lifecycle = new CloudScopeLifecycle(
                new CloudScopeClient(new CloudHttpClient(instance)), realtime, new CloudAppearanceStore());
        try {
            assertNull(lifecycle.activeScopeId());
            assertNull(lifecycle.activeWorldEpoch());
            assertEquals(0L, lifecycle.recoveryCursor());
            assertThrows(IllegalArgumentException.class, () -> lifecycle.enter("", "epoch"));
            assertThrows(IllegalArgumentException.class, () -> lifecycle.enter("scope", ""));
        } finally {
            lifecycle.close();
        }
    }
}
