package com.micaftic.morpher.network.state;

import com.micaftic.morpher.network.ClientNetworkBridge;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minecraft 连接状态（R9.2 拆分）——只回答「MC 连接是否存在」，不含 SPM 握手/隐私语义。
 *
 * <p>客户端：通过 {@link ClientNetworkBridge} 反射到 {@code ClientPacketHandlers}
 * （{@code Minecraft.getInstance().getConnection() != null}）。
 * <p>服务端：{@link #isPlayerConnected(ServerPlayer)} 只判连接存在性；SPM 版本协商由
 * {@code NetworkHandler.isConnectionValid} 负责（{@code NetworkHandler.isPlayerConnected}
 * 组合两者）。
 */
public final class MinecraftConnectionState {

    private MinecraftConnectionState() {
    }

    /** 客户端是否已连接到任意 Minecraft 服务器（未装 SPM 的 vanilla 服务器也算）。 */
    public static boolean isClientConnected() {
        return ClientNetworkBridge.isClientConnected();
    }

    /** 服务端视角：玩家是否持有有效的 MC 连接（不含 SPM 协商检查）。 */
    public static boolean isPlayerConnected(ServerPlayer serverPlayer) {
        return serverPlayer.connection != null && serverPlayer.connection.getConnection() != null;
    }
}
