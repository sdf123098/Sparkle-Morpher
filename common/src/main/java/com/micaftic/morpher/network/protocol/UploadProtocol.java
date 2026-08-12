package com.micaftic.morpher.network.protocol;

import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.network.message.*;

/**
 * R9.1 分组：模型上传（70-74）——start/chunk/finish/result。
 *
 * <p>仍使用同一个物理 Minecraft channel；本类只负责按协议域组织注册。</p>
 */
public final class UploadProtocol {

    private UploadProtocol() {
    }

    public static void register() {
        YSMChannel.register(70, C2SModelUploadStartPacket.class, C2SModelUploadStartPacket::encode, C2SModelUploadStartPacket::decode, C2SModelUploadStartPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(71, S2CModelUploadStartPacket.class, S2CModelUploadStartPacket::encode, S2CModelUploadStartPacket::decode, S2CModelUploadStartPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(72, C2SModelUploadChunkPacket.class, C2SModelUploadChunkPacket::encode, C2SModelUploadChunkPacket::decode, C2SModelUploadChunkPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(73, C2SModelUploadFinishPacket.class, C2SModelUploadFinishPacket::encode, C2SModelUploadFinishPacket::decode, C2SModelUploadFinishPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(74, S2CModelUploadResultPacket.class, S2CModelUploadResultPacket::encode, S2CModelUploadResultPacket::decode, S2CModelUploadResultPacket::handle, PacketDirection.PLAY_TO_CLIENT);
    }
}
