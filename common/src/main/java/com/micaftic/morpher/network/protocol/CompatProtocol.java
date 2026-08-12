package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：兼容协议（24）——TLM maid 模型设置。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class CompatProtocol {

    private CompatProtocol() {
    }

    public static void register() {
        YSMChannel.register(24, C2SSetMaidModelPacket.class, C2SSetMaidModelPacket::encode, C2SSetMaidModelPacket::decode, C2SSetMaidModelPacket::handle, PacketDirection.PLAY_TO_SERVER);
    }
}
