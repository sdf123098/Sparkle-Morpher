package com.micaftic.morpher.core.api.network.state;

/**
 * 云传输状态（R9.2 预留接缝，R9.3 CloudUploadTransport 接入后驱动）。
 *
 * <p>当前模型引用层已支持 {@code cloud:<uuid>:<asset-id>}（见 {@code ModelSourceType.CLOUD}），
 * 但尚无云传输通道，因此默认不可用；R9.3 实现云上传传输层时通过 {@link #setTransportAvailable}
 * 置位。在接入前，所有依赖云状态的调用一律按不可用处理，与现状行为一致。
 */
public final class CloudState {

    private static volatile boolean transportAvailable = false;

    private CloudState() {
    }

    public static boolean isAvailable() {
        return transportAvailable;
    }

    public static void setTransportAvailable(boolean value) {
        transportAvailable = value;
    }
}
