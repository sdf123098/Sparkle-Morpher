package com.micaftic.morpher.audio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class AudioCacheBuilder implements AutoCloseable {

    private final AudioStreamCache.CachedAudioStreamProvider cacheProvider;

    private final AudioTrackData trackData;

    private final ByteBuf audioBuffer;

    private final IntArrayList chunkSizes = new IntArrayList(5);

    private boolean isClosed = false;

    public AudioCacheBuilder(AudioStreamCache.CachedAudioStreamProvider cacheProvider, AudioTrackData trackData) {
        this.cacheProvider = cacheProvider;
        this.trackData = trackData;
        this.audioBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(((int) trackData.getDuration()) * 2);
        ResourceLifecycleStats.onDirectBufferAllocated(null, this.audioBuffer.capacity());
    }

    public void appendAudio(ByteBuffer byteBuffer) {
        if (!this.isClosed && this.audioBuffer.writableBytes() > 0) {
            int iMin = Math.min(this.audioBuffer.writableBytes(), byteBuffer.remaining());
            ByteBuffer chunk = byteBuffer.duplicate();
            chunk.limit(chunk.position() + iMin);
            this.audioBuffer.writeBytes(chunk);
            this.chunkSizes.add(iMin);
        }
    }

    public void flushToCache() {
        if (!this.isClosed) {
            this.isClosed = true;
            int readableBytes = this.audioBuffer.readableBytes();
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(readableBytes);
            ResourceLifecycleStats.onDirectBufferAllocated(null, readableBytes);
            boolean cached = false;
            try {
                this.audioBuffer.readBytes(byteBuffer.duplicate());
                this.cacheProvider.cacheAudioData(this.trackData, byteBuffer, this.chunkSizes);
                cached = true;
            } finally {
                if (!cached) {
                    MemoryUtil.memFree(byteBuffer);
                    ResourceLifecycleStats.onDirectBufferFreed(null, readableBytes);
                }
                ResourceLifecycleStats.onDirectBufferFreed(null, this.audioBuffer.capacity());
                this.audioBuffer.release();
            }
        }
    }

    @Override
    public void close() {
        if (!this.isClosed) {
            this.isClosed = true;
            ResourceLifecycleStats.onDirectBufferFreed(null, this.audioBuffer.capacity());
            this.audioBuffer.release();
        }
    }
}
