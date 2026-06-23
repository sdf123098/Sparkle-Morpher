package com.micaftic.morpher.audio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class AudioCacheBuilder {

    private final AudioStreamCache.CachedAudioStreamProvider cacheProvider;

    private final AudioTrackData trackData;

    private final ByteBuf audioBuffer;

    private final IntArrayList chunkSizes = new IntArrayList(5);

    private boolean closed = false;

    public AudioCacheBuilder(AudioStreamCache.CachedAudioStreamProvider cacheProvider, AudioTrackData trackData) {
        this.cacheProvider = cacheProvider;
        this.trackData = trackData;
        this.audioBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(((int) trackData.getDuration()) * 2);
    }

    public synchronized void appendAudio(ByteBuffer byteBuffer) {
        if (!this.closed && this.audioBuffer.writableBytes() > 0) {
            ByteBuffer source = byteBuffer.slice();
            int iMin = Math.min(this.audioBuffer.writableBytes(), source.remaining());
            if (iMin > 0) {
                source.limit(iMin);
                this.audioBuffer.writeBytes(source);
                this.chunkSizes.add(iMin);
            }
        }
    }

    public synchronized void flushToCache() {
        if (!this.closed) {
            this.closed = true;
            ByteBuffer byteBuffer = BufferUtils.createByteBuffer(this.audioBuffer.readableBytes());
            this.audioBuffer.readBytes(byteBuffer);
            byteBuffer.flip();
            this.audioBuffer.release();
            this.cacheProvider.cacheAudioData(this.trackData, byteBuffer, this.chunkSizes);
        }
    }

    public synchronized void discard() {
        if (!this.closed) {
            this.closed = true;
            this.audioBuffer.release();
            this.cacheProvider.cancelAudioData(this.trackData);
        }
    }
}
