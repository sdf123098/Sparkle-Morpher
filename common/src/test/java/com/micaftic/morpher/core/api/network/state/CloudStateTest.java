package com.micaftic.morpher.core.api.network.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R9.2 CloudState 测试：云传输可用性接缝（R9.3 CloudUploadTransport 接入前默认不可用）。
 *
 * <p>现状无云传输通道，任何依赖云状态的调用必须按不可用处理；接入后由传输层置位。</p>
 */
class CloudStateTest {

    @BeforeEach
    void resetState() {
        CloudState.setTransportAvailable(false);
    }

    @Test
    void unavailableBeforeTransportWired() {
        assertFalse(CloudState.isAvailable());
    }

    @Test
    void transportAvailabilityRoundTrip() {
        CloudState.setTransportAvailable(true);
        assertTrue(CloudState.isAvailable());
        CloudState.setTransportAvailable(false);
        assertFalse(CloudState.isAvailable());
    }
}
