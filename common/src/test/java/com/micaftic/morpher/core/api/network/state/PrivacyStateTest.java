package com.micaftic.morpher.core.api.network.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R9.2 PrivacyState 测试：隐私模式双标志状态（sessionActive / configured）。
 *
 * <p>从 PrivacyMode.isActive()（sessionActive || GeneralConfig.PRIVACY_MODE）抽取；
 * 客户端适配器 PrivacyMode 负责把配置源同步进 configured，本类只管状态语义。</p>
 */
class PrivacyStateTest {

    @BeforeEach
    void resetState() {
        PrivacyState.setConfigured(false);
        PrivacyState.setSessionActive(false);
    }

    @Test
    void inactiveByDefault() {
        assertTrue(PrivacyState.isInactive());
        assertFalse(PrivacyState.isActive());
        assertFalse(PrivacyState.isConfigured());
    }

    @Test
    void configuredAloneActivates() {
        PrivacyState.setConfigured(true);
        assertTrue(PrivacyState.isActive());
        assertFalse(PrivacyState.isInactive());
    }

    @Test
    void sessionActiveAloneActivates() {
        PrivacyState.setSessionActive(true);
        assertTrue(PrivacyState.isActive());
    }

    @Test
    void inactiveWhenBothFlagsCleared() {
        PrivacyState.setConfigured(true);
        PrivacyState.setSessionActive(true);
        PrivacyState.setConfigured(false);
        PrivacyState.setSessionActive(false);
        assertTrue(PrivacyState.isInactive());
    }

    @Test
    void endSessionKeepsConfiguredState() {
        // 模拟：配置开启 → 会话激活；endSession 只清 session，配置仍生效 → 仍激活
        PrivacyState.setConfigured(true);
        PrivacyState.setSessionActive(true);
        PrivacyState.setSessionActive(false);
        assertTrue(PrivacyState.isActive());
    }
}
