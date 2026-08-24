package com.micaftic.morpher.audio;

import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class AudioTrackData implements AutoCloseable {

    @Nullable
    private final ByteBuffer data;
    private final boolean nativeAllocated;
    private final int byteSize;
    private boolean closed;

    private final AudioCodec codec;

    private final int sampleRate;

    private final long duration;

    public AudioTrackData(@Nullable ByteBuffer byteBuffer, int i, int i2, long j) {
        AudioCodec codec;
        if (byteBuffer != null) {
            this.byteSize = byteBuffer.remaining();
            if (i == 2) {
                this.data = MemoryUtil.memAlloc(byteBuffer.remaining());
                this.nativeAllocated = true;
                ResourceLifecycleStats.onDirectBufferAllocated(null, this.byteSize);
            } else {
                this.data = ByteBuffer.allocate(byteBuffer.remaining());
                this.nativeAllocated = false;
            }
            this.data.duplicate().put(byteBuffer.duplicate());
        } else {
            this.data = null;
            this.nativeAllocated = false;
            this.byteSize = 0;
        }
        switch (i) {
            case 1:
                codec = AudioCodec.VORBIS;
                break;
            case 2:
                codec = AudioCodec.OPUS;
                break;
            default:
                codec = AudioCodec.UNDEFINED;
                break;
        }
        this.codec = codec;
        this.sampleRate = i2;
        this.duration = j;
    }

    public long getDuration() {
        return this.duration;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public AudioCodec getCodec() {
        return this.codec;
    }

    @Nullable
    public ByteBuffer getData() {
        if (this.closed) {
            return null;
        }
        return this.data;
    }

    public int byteSize() {
        return this.byteSize;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.nativeAllocated && this.data != null) {
            ResourceLifecycleStats.onDirectBufferFreed(null, this.byteSize);
            MemoryUtil.memFree(this.data);
        }
    }
}
