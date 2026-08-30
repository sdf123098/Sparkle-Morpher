package com.micaftic.morpher.core.api.network.state;

import com.micaftic.morpher.legacy.compat.LegacyCompatState;

/**
 * Legacy SPM 握手状态（R9.2 从 NetworkHandler.isClientConnected() 拆分出的客户端语义）。
 *
 * <p>只收敛<b>客户端侧</b>的 legacy 握手会话状态：
 * <ul>
 *   <li>握手完成标志（版本协商成功且版本匹配，见 {@code S2CVersionCheckPacket}）</li>
 *   <li>legacy 服务器能力标志（oySm 服务器 / 允许上传，由版本检查包下发）</li>
 *   <li>{@link #isClientSessionActive(boolean)}：组合查询——握手完成或已协商的 MC 连接</li>
 * </ul>
 *
 * <p>服务端按连接记录的协商版本（WeakHashMap / netty Channel attribute）是平台相关存储，
 * 留在各加载器的 {@code NetworkHandler}，不属于本类。
 */
@Deprecated(forRemoval = true)
public final class LegacySpmHandshakeState {

    private LegacySpmHandshakeState() {
    }

    /** 客户端版本协商成功（版本与 {@code NetworkHandler.VERSION} 一致）时置位。 */
    public static void markClientComplete() {
        LegacyCompatState.markClientComplete();
    }

    /** 退出服务器 / 进入隐私模式时复位。 */
    public static void resetClientComplete() {
        LegacyCompatState.resetClientComplete();
    }

    public static boolean isClientComplete() {
        return LegacyCompatState.isClientComplete();
    }

    /**
     * 客户端是否处于活跃的 SPM 会话中：
     * 握手已完成，或当前 MC 连接已与 SPM 服务器协商（26.x 分支为连接存在，fa 分支为 channel 已协商）。
     */
    public static boolean isClientSessionActive(boolean mcConnectionNegotiated) {
        return LegacyCompatState.isClientSessionActive(mcConnectionNegotiated);
    }

    public static void setOysmServer(boolean value) {
        LegacyCompatState.setOysmServer(value);
    }

    public static boolean isOysmServer() {
        return LegacyCompatState.isOysmServer();
    }

    public static void setAllowUpload(boolean value) {
        LegacyCompatState.setAllowUpload(value);
    }

    public static boolean isAllowUpload() {
        return LegacyCompatState.isAllowUpload();
    }

    /** 清空整个客户端 legacy 会话状态（握手 + 服务器能力），用于退出服务器 / 进入隐私模式。 */
    public static void resetClientSession() {
        LegacyCompatState.resetClientSession();
    }
}
