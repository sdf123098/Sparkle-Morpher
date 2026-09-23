package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudManagementControllerTest {
    @Test
    void selectingAnotherSavedInstanceUpdatesTheSelectedProfile() throws Exception {
        CloudInstanceRegistry registry = new CloudInstanceRegistry(
                Files.createTempDirectory("spm-cloud-registry").resolve("instances.json"));
        var official = new CloudInstanceRegistry.CloudInstanceProfile(
                com.micaftic.morpher.cloud.CloudInstanceConfig.v1("official", java.net.URI.create("https://official.example")),
                "Official");
        var community = new CloudInstanceRegistry.CloudInstanceProfile(
                com.micaftic.morpher.cloud.CloudInstanceConfig.v1("community", java.net.URI.create("https://community.example")),
                "Community");
        registry.addOrReplace(official);
        registry.addOrReplace(community);
        registry.select(official.instanceId());
        CloudManagementController controller = new CloudManagementController(
                registry, new CloudConnectionController(), Files.createTempDirectory("spm-cloud-cache"), "2.0.0");

        controller.selectInstance(community.instanceId());

        assertEquals("community", registry.selected().orElseThrow().instanceId());
    }

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
