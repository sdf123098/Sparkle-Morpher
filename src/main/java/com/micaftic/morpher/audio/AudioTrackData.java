package com.micaftic.morpher.audio;

import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class AudioTrackData implements AutoCloseable {

    @Nullable
    private ByteBuffer data;

    private final AudioCodec codec;

    private final int sampleRate;

    private final long duration;
    private final int byteSize;
    private final boolean directAllocated;

    public AudioTrackData(@Nullable ByteBuffer byteBuffer, int i, int i2, long j) {
        AudioCodec codec;
        if (byteBuffer != null) {
            if (i == 2) {
                this.data = MemoryUtil.memAlloc(byteBuffer.remaining());
                this.directAllocated = true;
                ResourceLifecycleStats.onDirectBufferAllocated(null, byteBuffer.remaining());
            } else {
                this.data = ByteBuffer.allocate(byteBuffer.remaining());
                this.directAllocated = false;
            }
            this.byteSize = this.data.capacity();
            this.data.duplicate().put(byteBuffer.duplicate());
        } else {
            this.data = null;
            this.byteSize = 0;
            this.directAllocated = false;
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
        return this.data;
    }

    public int getByteSize() {
        return this.byteSize;
    }

    @Override
    public void close() {
        ByteBuffer buffer = this.data;
        this.data = null;
        if (buffer != null && this.directAllocated) {
            MemoryUtil.memFree(buffer);
            ResourceLifecycleStats.onDirectBufferFreed(null, this.byteSize);
        }
    }
}
