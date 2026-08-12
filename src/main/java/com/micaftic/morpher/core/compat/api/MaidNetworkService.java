package com.micaftic.morpher.core.compat.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * R11.1/R11.2 Compat API — 服务端网络发送 hook。
 *
 * <p>核心只定义 hook；具体实现由 adapter 提供（{@code NetworkHandlerService}），
 * 经 {@link CompatServices} 注册。兼容逻辑（如 TLM {@code MaidModelSync}）只依赖本接口，
 * 不直接访问 {@code NetworkHandler} 内部静态。</p>
 */
public interface MaidNetworkService {

    /** 向追踪该实体的玩家发送数据包。 */
    void sendToTrackingEntity(Object packet, Entity entity);

    /** 向指定玩家发送数据包。 */
    void sendToClientPlayer(Object packet, Player player);

    /** 玩家是否已连接（发送前守卫）。 */
    boolean isPlayerConnected(ServerPlayer player);

    /** 未注册服务时的 no-op 默认。 */
    MaidNetworkService NONE = new MaidNetworkService() {
        @Override
        public void sendToTrackingEntity(Object packet, Entity entity) {
        }

        @Override
        public void sendToClientPlayer(Object packet, Player player) {
        }

        @Override
        public boolean isPlayerConnected(ServerPlayer player) {
            return false;
        }
    };
}
