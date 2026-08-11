package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

/**
 * 云上传传输（R9.3 预留实现）——云后端接入前不可用。
 *
 * <p>当前模型引用层支持 {@code cloud:<uuid>:<asset-id>}（{@code ModelSourceType.CLOUD}），
 * 但尚无云传输通道：{@link #isAvailable()} 跟随 {@link CloudState#isAvailable()}（恒 false）。
 * 接入云后端时由 {@code CloudState.setTransportAvailable(true)} 置位，届时本类才允许发包。
 */
public final class CloudUploadTransport implements ModelUploadTransport {

    public static final CloudUploadTransport INSTANCE = new CloudUploadTransport();

    private CloudUploadTransport() {
    }

    @Override
    public boolean isAvailable() {
        return CloudState.isAvailable();
    }

    @Override
    public void sendStart(String modelId, String fileName, int dataLength, String sha256) {
        throw new UnsupportedOperationException("Cloud upload transport is not wired yet (CloudState unavailable)");
    }

    @Override
    public void sendChunk(long uploadId, int nextOffset, byte[] data, int dataOffset, int length) {
        throw new UnsupportedOperationException("Cloud upload transport is not wired yet (CloudState unavailable)");
    }

    @Override
    public void sendFinish(long uploadId) {
        throw new UnsupportedOperationException("Cloud upload transport is not wired yet (CloudState unavailable)");
    }
}
