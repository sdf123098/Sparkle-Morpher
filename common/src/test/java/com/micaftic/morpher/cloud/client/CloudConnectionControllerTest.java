package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudConnectionControllerTest {
    @Test
    void logoutDiscardsInMemoryConnectionState() throws Exception {
        CloudConnectionController controller = new CloudConnectionController();
        controller.logout();

        assertFalse(controller.hasSession());
        assertFalse(controller.isAuthenticated());
        assertNull(controller.profile());
        assertThrows(CompletionException.class, () -> controller.refresh(Files.createTempDirectory("spm-cloud-cache"), "2.0.0").join());
    }
}
