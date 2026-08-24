package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：动画与 molang（3/7/18/19/23）——播放/表达式/挥臂。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class AnimationProtocol {

    private AnimationProtocol() {
    }

    public static void register() {
        YSMChannel.register(3, S2CExecuteMolangPacket.class, S2CExecuteMolangPacket::encode, S2CExecuteMolangPacket::decode, S2CExecuteMolangPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(7, C2SPlayAnimationPacket.class, C2SPlayAnimationPacket::encode, C2SPlayAnimationPacket::decode, C2SPlayAnimationPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(18, C2SSyncAnimationExpressionPacket.class, C2SSyncAnimationExpressionPacket::encode, C2SSyncAnimationExpressionPacket::decode, C2SSyncAnimationExpressionPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(19, S2CSyncAnimationExpressionPacket.class, S2CSyncAnimationExpressionPacket::encode, S2CSyncAnimationExpressionPacket::decode, S2CSyncAnimationExpressionPacket::handleCapability, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(23, C2SSwingArmPacket.class, C2SSwingArmPacket::encode, C2SSwingArmPacket::decode, C2SSwingArmPacket::handle, PacketDirection.PLAY_TO_SERVER);
    }
}
