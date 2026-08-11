package com.micaftic.morpher.core.model;

import com.micaftic.morpher.core.model.catalog.LocalModelScanner.Kind;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/**
 * R8-5 ModelUploadSession — 模型上传会话状态机（从 ServerModelManager.ModelUploadState 抽取）。
 *
 * <p>持有一次上传的全部状态：目标元数据 + 接收缓冲 + 进度/失败标记。
 * {@link #appendChunk} 校验 offset 连续性（乱序/越界/null 标记失败并拒绝），
 * 网络传输、带宽控制、sha256 校验与落盘由调用方负责——session 只维护"接收进度"。</p>
 */
public final class ModelUploadSession {

    private final long uploadId;
    private final UUID owner;
    private final String modelId;
    private final String fileName;
    private final Kind importKind;
    private final byte[] data;
    private final String sha256;
    private int receivedBytes;
    private boolean failed;
    private long lastTouchedMs;

    public ModelUploadSession(long uploadId, UUID owner, String modelId, String fileName,
                              Kind importKind, int totalBytes, String sha256) {
        this.uploadId = uploadId;
        this.owner = owner;
        this.modelId = modelId;
        this.fileName = fileName;
        this.importKind = importKind;
        this.data = new byte[totalBytes];
        this.sha256 = sha256;
        touch();
    }

    /**
     * 追加一个 chunk（原 receiveModelUploadChunk 的进度推进部分）。
     *
     * @param offset 声明偏移（必须 == receivedBytes）
     * @param chunk  chunk 字节（null 拒绝）
     * @return 接受则 true；校验失败标记 failed 并返回 false
     */
    public boolean appendChunk(int offset, @Nullable byte[] chunk) {
        touch();
        if (chunk == null || offset < 0 || offset + chunk.length > data.length || offset != receivedBytes) {
            failed = true;
            return false;
        }
        System.arraycopy(chunk, 0, data, offset, chunk.length);
        receivedBytes += chunk.length;
        return true;
    }

    /** 刷新最后活跃时间（用于过期清理）。 */
    public void touch() {
        this.lastTouchedMs = System.currentTimeMillis();
    }

    /** 是否完整接收（未失败且 receivedBytes == totalBytes）。 */
    public boolean isComplete() {
        return !failed && receivedBytes == data.length;
    }

    public boolean isFailed() {
        return failed;
    }

    /** 超过超时窗口（now - lastTouchedMs > timeoutMs）返回 true。 */
    public boolean isExpired(long now, long timeoutMs) {
        return now - lastTouchedMs > timeoutMs;
    }

    public long uploadId() {
        return uploadId;
    }

    public UUID owner() {
        return owner;
    }

    public String modelId() {
        return modelId;
    }

    public String fileName() {
        return fileName;
    }

    public Kind importKind() {
        return importKind;
    }

    public String sha256() {
        return sha256;
    }

    /** 接收缓冲（完整数据）；仅供调用方在完成后读取。 */
    public byte[] data() {
        return data;
    }

    public int totalBytes() {
        return data.length;
    }

    public int receivedBytes() {
        return receivedBytes;
    }

    @Override
    public String toString() {
        return "ModelUploadSession{" + modelId + "/" + fileName + " " + receivedBytes + "/" + data.length
                + (failed ? " FAILED" : "") + " sha=" + Arrays.hashCode(data) + "}";
    }
}
