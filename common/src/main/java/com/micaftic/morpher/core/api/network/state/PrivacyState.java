package com.micaftic.morpher.core.api.network.state;

/**
 * 隐私模式状态（R9.2 从 NetworkHandler.isClientConnected() / ClientNetworkBridge 拆出）。
 *
 * <p>纯状态持有者，双标志：{@code sessionActive}（本次游戏会话内激活）与 {@code configured}
 * （配置开关已开启）。任一为真即视为隐私模式激活。
 *
 * <p>{@code client.PrivacyMode} 是客户端适配器：负责从 {@code GeneralConfig} 读取配置并同步
 * 到本类，以及执行进入/退出隐私模式的客户端副作用（提示消息、模型切换等）。
 */
public final class PrivacyState {

    private static volatile boolean sessionActive = false;
    private static volatile boolean configured = false;

    private PrivacyState() {
    }

    public static boolean isActive() {
        return sessionActive || configured;
    }

    public static boolean isInactive() {
        return !isActive();
    }

    public static boolean isConfigured() {
        return configured;
    }

    public static void setConfigured(boolean value) {
        configured = value;
    }

    public static void setSessionActive(boolean value) {
        sessionActive = value;
    }
}
