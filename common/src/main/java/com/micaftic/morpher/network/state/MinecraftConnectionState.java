package com.micaftic.morpher.network.state;

import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.mixin.client.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minecraft 连接状态（R9.2 拆分）——只回答「MC 连接是否存在」，不含 SPM 握手/隐私语义。
 *
 * <p>客户端：fabric 分支直接经 {@link MinecraftAccessor} 取 {@code ClientPacketListener}；
 * SPM channel 协商校验由 {@code NetworkHandler.isConnectionValid} 负责（{@code NetworkHandler.isClientConnected}
 * 组合两者）。
 * <p>服务端：{@link #isPlayerConnected(ServerPlayer)} 只判连接存在性。
 */
public final class MinecraftConnectionState {

    private MinecraftConnectionState() {
    }

    /** 客户端是否已连接到任意 Minecraft 服务器（未装 SPM 的 vanilla 服务器也算）。 */
    public static boolean isClientConnected() {
        return clientConnection() != null;
    }

    /** 客户端当前连接监听器（可能为 null）。 */
    public static ClientPacketListener clientConnection() {
        return ((MinecraftAccessor) Minecraft.getInstance()).ysm$getConnection();
    }

    /** 服务端视角：玩家是否持有有效的 MC 连接（不含 SPM 协商检查）。 */
    public static boolean isPlayerConnected(ServerPlayer serverPlayer) {
        return serverPlayer.connection != null
                && ((ServerCommonPacketListenerImplAccessor) serverPlayer.connection).ysm$getConnection() != null;
    }
}
