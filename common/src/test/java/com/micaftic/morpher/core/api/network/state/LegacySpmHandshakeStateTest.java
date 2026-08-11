package com.micaftic.morpher.core.api.network.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R9.2 LegacySpmHandshakeState 测试：客户端 legacy 握手会话状态
 * （握手完成标志 + oySm/allowUpload 服务器能力 + isClientSessionActive 组合查询）。
 *
 * <p>从 NetworkHandler.isClientConnected() / ClientModelManager.isOysmServer 等语义抽取；
 * 服务端按连接协商版本（WeakHashMap / Channel attribute）属平台存储，不在本类测试范围。</p>
 */
class LegacySpmHandshakeStateTest {

    @BeforeEach
    void resetState() {
        LegacySpmHandshakeState.resetClientSession();
    }

    @Test
    void clientComplete_markAndReset() {
        assertFalse(LegacySpmHandshakeState.isClientComplete());
        LegacySpmHandshakeState.markClientComplete();
        assertTrue(LegacySpmHandshakeState.isClientComplete());
        LegacySpmHandshakeState.resetClientComplete();
        assertFalse(LegacySpmHandshakeState.isClientComplete());
    }

    @Test
    void sessionActive_trueWhenHandshakeCompleteEvenWithoutMcConnection() {
        LegacySpmHandshakeState.markClientComplete();
        assertTrue(LegacySpmHandshakeState.isClientSessionActive(false));
    }

    @Test
    void sessionActive_trueWhenMcConnectionNegotiated() {
        assertTrue(LegacySpmHandshakeState.isClientSessionActive(true));
    }

    @Test
    void sessionActive_falseWhenNothingNegotiated() {
        assertFalse(LegacySpmHandshakeState.isClientSessionActive(false));
    }

    @Test
    void serverCapabilities_defaultFalseAndRoundTrip() {
        assertFalse(LegacySpmHandshakeState.isOysmServer());
        assertFalse(LegacySpmHandshakeState.isAllowUpload());
        LegacySpmHandshakeState.setOysmServer(true);
        LegacySpmHandshakeState.setAllowUpload(true);
        assertTrue(LegacySpmHandshakeState.isOysmServer());
        assertTrue(LegacySpmHandshakeState.isAllowUpload());
    }

    @Test
    void resetClientSession_clearsCompleteAndCapabilities() {
        LegacySpmHandshakeState.markClientComplete();
        LegacySpmHandshakeState.setOysmServer(true);
        LegacySpmHandshakeState.setAllowUpload(true);
        LegacySpmHandshakeState.resetClientSession();
        assertFalse(LegacySpmHandshakeState.isClientComplete());
        assertFalse(LegacySpmHandshakeState.isOysmServer());
        assertFalse(LegacySpmHandshakeState.isAllowUpload());
        assertFalse(LegacySpmHandshakeState.isClientSessionActive(false));
    }
}
