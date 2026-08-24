package com.micaftic.morpher.core.api.network.upload;

/**
 * 模型上传传输层（R9.3 拆分）——{@code client.upload.ModelUploadSession} 只做 workflow，
 * 具体发包交给 transport 实现。
 *
 * <p>当前仅 legacy 服务器通道可用（{@code LegacyServerUploadTransport}，行为与旧版本完全一致）；
 * 云传输（{@code CloudUploadTransport}）是 R9.3 预留实现，在云后端接入前
 * {@link #isAvailable()} 恒为 false（与 {@code CloudState} 联动）。
 */
public interface ModelUploadTransport {

    /** 该传输通道当前是否可用。调用方在不可用时不得发包。 */
    boolean isAvailable();

    /** 发起上传（对应 C2SModelUploadStartPacket）。 */
    void sendStart(String modelId, String fileName, int dataLength, String sha256);

    /** 发送一个数据分片（对应 C2SModelUploadChunkPacket）。 */
    void sendChunk(long uploadId, int nextOffset, byte[] data, int dataOffset, int length);

    /** 通知服务端上传结束（对应 C2SModelUploadFinishPacket）。 */
    void sendFinish(long uploadId);
}
