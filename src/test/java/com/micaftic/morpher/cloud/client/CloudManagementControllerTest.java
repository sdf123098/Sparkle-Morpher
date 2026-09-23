package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudManagementControllerTest {
    @Test
    void exposesEmptyUnauthenticatedStateWithoutPersistingSecrets() throws Exception {
        CloudManagementController controller = new CloudManagementController(
                new CloudInstanceRegistry(Files.createTempDirectory("spm-cloud-registry").resolve("instances.json")),
                new CloudConnectionController(),
                Files.createTempDirectory("spm-cloud-cache"),
                "2.0.0");

        CloudManagementController.CloudManagementSnapshot snapshot = controller.snapshot();
        assertFalse(snapshot.authenticated());
        assertEquals(0, snapshot.scopes().size());
        assertThrows(IllegalStateException.class, controller::refreshScopes);
    }
}
