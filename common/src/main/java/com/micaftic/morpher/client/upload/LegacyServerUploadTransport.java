package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SModelUploadChunkPacket;
import com.micaftic.morpher.network.message.C2SModelUploadFinishPacket;
import com.micaftic.morpher.network.message.C2SModelUploadStartPacket;

/**
 * Legacy 服务器上传传输（R9.3）——把现有 {@code C2SModelUploadStart/Chunk/FinishPacket}
 * 发包逻辑从 {@link ModelUploadSession} 迁出，行为与旧版本完全一致（验收：Legacy 兼容）。
 *
 * <p>可用性由 {@code NetworkHandler.isClientConnected()}（workflow 层校验）控制，
 * 本类 {@link #isAvailable()} 恒为 true——channel 协商检查在发送入口统一预检。
 */
public final class LegacyServerUploadTransport implements ModelUploadTransport {

    public static final LegacyServerUploadTransport INSTANCE = new LegacyServerUploadTransport();

    private LegacyServerUploadTransport() {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendStart(String modelId, String fileName, int dataLength, String sha256) {
        NetworkHandler.sendToServer(new C2SModelUploadStartPacket(modelId, fileName, dataLength, sha256));
    }

    @Override
    public void sendChunk(long uploadId, int nextOffset, byte[] data, int dataOffset, int length) {
        NetworkHandler.sendToServer(new C2SModelUploadChunkPacket(uploadId, nextOffset, data, dataOffset, length));
    }

    @Override
    public void sendFinish(long uploadId) {
        NetworkHandler.sendToServer(new C2SModelUploadFinishPacket(uploadId));
    }
}
