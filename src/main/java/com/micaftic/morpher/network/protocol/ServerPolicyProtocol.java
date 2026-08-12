package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：服务器策略与协商（6/8/9/15/17/51/52）——auth/star/反馈/版本检查。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class ServerPolicyProtocol {

    private ServerPolicyProtocol() {
    }

    public static void register() {
        YSMChannel.register(6, S2CSyncAuthModelsPacket.class, S2CSyncAuthModelsPacket::encode, S2CSyncAuthModelsPacket::decode, S2CSyncAuthModelsPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(8, S2CSyncStarModelsPacket.class, S2CSyncStarModelsPacket::encode, S2CSyncStarModelsPacket::decode, S2CSyncStarModelsPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(9, C2SSetStarModelPacket.class, C2SSetStarModelPacket::encode, C2SSetStarModelPacket::decode, C2SSetStarModelPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(15, C2SCompleteFeedbackPacket.class, C2SCompleteFeedbackPacket::encode, C2SCompleteFeedbackPacket::decode, C2SCompleteFeedbackPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(17, C2SRequestExecuteMolangPacket.class, C2SRequestExecuteMolangPacket::encode, C2SRequestExecuteMolangPacket::decode, C2SRequestExecuteMolangPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(51, S2CVersionCheckPacket.class, S2CVersionCheckPacket::encode, S2CVersionCheckPacket::decode, S2CVersionCheckPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(52, C2SVersionCheckPacket.class, C2SVersionCheckPacket::encode, C2SVersionCheckPacket::decode, C2SVersionCheckPacket::handle, PacketDirection.PLAY_TO_SERVER);
    }
}
