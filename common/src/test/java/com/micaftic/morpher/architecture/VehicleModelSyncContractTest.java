package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleModelSyncContractTest {
    @Test
    void vehicleStateIsSentToReconnectTrackingClientsAndRider() throws IOException {
        String source = Files.readString(Path.of("../src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("syncVehicleModelToReceiver"));
        assertTrue(source.contains("VehicleModelCapability::isInitialized"));
        assertTrue(source.contains("NetworkHandler.sendToClientPlayer(packet, receiver)"));
        assertTrue(source.contains("NetworkHandler.sendToClientPlayer(packet, sp)"));
    }
}
