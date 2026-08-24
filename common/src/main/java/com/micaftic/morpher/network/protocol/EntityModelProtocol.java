package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：实体模型分配与状态（4/5/16/21/22）——切换/弹射物/载具/玩家状态。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class EntityModelProtocol {

    private EntityModelProtocol() {
    }

    public static void register() {
        YSMChannel.register(4, S2CSetModelAndTexturePacket.class, S2CSetModelAndTexturePacket::encode, S2CSetModelAndTexturePacket::decode, S2CSetModelAndTexturePacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(5, C2SRequestSwitchModelPacket.class, C2SRequestSwitchModelPacket::encode, C2SRequestSwitchModelPacket::decode, C2SRequestSwitchModelPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(16, S2CSyncProjectileModelPacket.class, S2CSyncProjectileModelPacket::encode, S2CSyncProjectileModelPacket::decode, S2CSyncProjectileModelPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(21, S2CSyncPlayerStatePacket.class, S2CSyncPlayerStatePacket::encode, S2CSyncPlayerStatePacket::decode, S2CSyncPlayerStatePacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(22, S2CSyncVehicleModelPacket.class, S2CSyncVehicleModelPacket::encode, S2CSyncVehicleModelPacket::decode, S2CSyncVehicleModelPacket::handle, PacketDirection.PLAY_TO_CLIENT);
    }
}
