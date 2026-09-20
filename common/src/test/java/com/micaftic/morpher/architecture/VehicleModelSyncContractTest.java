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
        String capabilityEvent = readFirstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/event/CapabilityEvent.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/event/CapabilityEvent.java"));

        assertTrue(capabilityEvent.contains("syncVehicleModelToReceiver"));
        assertTrue(capabilityEvent.contains("VehicleModelCapability::isInitialized"));
        assertTrue(capabilityEvent.contains("sendToClientPlayer(packet, receiver)"));
        assertTrue(capabilityEvent.contains("sendToClientPlayer(packet, serverPlayer)"));
    }

    private static String readFirstExisting(Path... candidates) throws IOException {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("CapabilityEvent source not found");
    }
}
