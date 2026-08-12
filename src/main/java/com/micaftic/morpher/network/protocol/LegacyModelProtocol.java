package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：legacy 模型同步 payload（1/2）——握手后的模型数据同步。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class LegacyModelProtocol {

    private LegacyModelProtocol() {
    }

    public static void register() {
        YSMChannel.register(1, S2CModelSyncPayload.class, S2CModelSyncPayload::encode, S2CModelSyncPayload::decode, S2CModelSyncPayload::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(2, C2SModelSyncPayload.class, C2SModelSyncPayload::encode, C2SModelSyncPayload::decode, C2SModelSyncPayload::handle, PacketDirection.PLAY_TO_SERVER);
    }
}
