package com.micaftic.morpher.network;

import com.micaftic.morpher.core.compat.api.MaidNetworkService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * R11.2 {@link MaidNetworkService} adapter：委托 {@link NetworkHandler} 服务端发送。
 *
 * <p>兼容逻辑（TLM MaidModelSync 等）经 {@link com.micaftic.morpher.core.compat.api.CompatServices}
 * 取本服务，不再直接访问 NetworkHandler 内部静态。</p>
 */
public final class NetworkHandlerService implements MaidNetworkService {

    public static final NetworkHandlerService INSTANCE = new NetworkHandlerService();

    private NetworkHandlerService() {
    }

    @Override
    public void sendToTrackingEntity(Object packet, Entity entity) {
        NetworkHandler.sendToTrackingEntity(packet, entity);
    }

    @Override
    public void sendToClientPlayer(Object packet, Player player) {
        NetworkHandler.sendToClientPlayer(packet, player);
    }

    @Override
    public boolean isPlayerConnected(ServerPlayer player) {
        return NetworkHandler.isPlayerConnected(player);
    }
}
