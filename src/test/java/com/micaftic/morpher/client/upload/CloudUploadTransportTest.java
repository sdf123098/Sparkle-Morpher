package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.core.api.network.state.CloudState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R9.3 CloudUploadTransport 测试：云传输占位——云后端接入前不可用，
 * 可用性跟随 CloudState；未接线时发包抛 UnsupportedOperationException。
 */
class CloudUploadTransportTest {

    @AfterEach
    void resetState() {
        CloudState.setTransportAvailable(false);
    }

    @Test
    void unavailableBeforeCloudWired() {
        assertFalse(CloudUploadTransport.INSTANCE.isAvailable());
    }

    @Test
    void availabilityFollowsCloudState() {
        CloudState.setTransportAvailable(true);
        assertTrue(CloudUploadTransport.INSTANCE.isAvailable());
        CloudState.setTransportAvailable(false);
        assertFalse(CloudUploadTransport.INSTANCE.isAvailable());
    }

    @Test
    void sendMethodsThrowWhenNotWired() {
        CloudUploadTransport t = CloudUploadTransport.INSTANCE;
        assertThrows(UnsupportedOperationException.class, () -> t.sendStart("m", "f.ysm", 1, "h"));
        assertThrows(UnsupportedOperationException.class, () -> t.sendChunk(1L, 0, new byte[1], 0, 1));
        assertThrows(UnsupportedOperationException.class, () -> t.sendFinish(1L));
    }
}
