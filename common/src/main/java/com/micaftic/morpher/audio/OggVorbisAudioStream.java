package com.micaftic.morpher.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class OggVorbisAudioStream implements IAudioStreamSupport {

    private static final ByteBuffer EMPTY_BUFFER = BufferUtils.createByteBuffer(0);

    private final AudioFormat audioFormat;
    private final long decoderHandle;
    private final ByteBuffer inputBuffer;
    private final int channels;

    @Nullable
    private final AudioCacheBuilder cacheBuilder;

    private volatile boolean isClosed;
    private boolean isEndOfStream;

    public OggVorbisAudioStream(ByteBuffer byteBuffer, @Nullable AudioCacheBuilder cacheBuilder)
            throws UnsupportedAudioFileException, IOException {

        ByteBuffer directBuffer = MemoryUtil.memAlloc(byteBuffer.remaining());
        directBuffer.put(byteBuffer.duplicate());
        directBuffer.flip();

        long handle = MemoryUtil.NULL;
        boolean success = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);

            handle = STBVorbis.stb_vorbis_open_memory(directBuffer, error, null);
            if (handle == MemoryUtil.NULL) {
                throw new IOException("Failed to open OGG Vorbis stream, error: " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.malloc();
            int channelCount;
            int sampleRate;
            try {
                STBVorbis.stb_vorbis_get_info(handle, info);
                channelCount = info.channels();
                sampleRate = info.sample_rate();
            } finally {
                info.free();
            }

            if (channelCount != 1 && channelCount != 2) {
                throw new UnsupportedAudioFileException("Unsupported channel count: " + channelCount);
            }

            this.decoderHandle = handle;
            this.inputBuffer = directBuffer;
            this.channels = channelCount;
            this.audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
            this.cacheBuilder = cacheBuilder;
            success = true;
        } finally {
            if (!success) {
                if (handle != MemoryUtil.NULL) {
                    STBVorbis.stb_vorbis_close(handle);
                }
                MemoryUtil.memFree(directBuffer);
                if (cacheBuilder != null) {
                    cacheBuilder.discard();
                }
            }
        }
    }

    @NotNull
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    @NotNull
    public ByteBuffer read(int frameCount) throws IOException {
        if (this.isEndOfStream || this.isClosed) {
            return EMPTY_BUFFER;
        }

        int samplesPerChannel = frameCount * channels;
        ShortBuffer shortBuffer = MemoryUtil.memAllocShort(samplesPerChannel);
        try {
            int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                    decoderHandle, channels, shortBuffer);

            if (samplesRead == 0) {
                if (this.cacheBuilder != null) {
                    this.cacheBuilder.flushToCache();
                }
                this.isEndOfStream = true;
                return EMPTY_BUFFER;
            }

            ShortBuffer readView = shortBuffer.duplicate();
            readView.limit(samplesRead * channels);

            ByteBuffer byteBuf = BufferUtils.createByteBuffer(samplesRead * 2).order(ByteOrder.LITTLE_ENDIAN);

            if (channels == 2) {
                ByteBuffer monoBuf = byteBuf;
                for (int i = 0; i < samplesRead; i++) {
                    short left = readView.get();
                    short right = readView.get();
                    monoBuf.putShort((short) Math.round((left + right) / 2.0f));
                }
                shortBuffer.limit(samplesRead); // for view consistency
                monoBuf.flip();
                if (this.cacheBuilder != null) {
                    this.cacheBuilder.appendAudio(monoBuf.duplicate());
                }
                return monoBuf;
            } else {
                for (int i = 0; i < samplesRead; i++) {
                    byteBuf.putShort(readView.get());
                }
                byteBuf.flip();
                if (this.cacheBuilder != null) {
                    this.cacheBuilder.appendAudio(byteBuf.duplicate());
                }
                return byteBuf;
            }
        } finally {
            MemoryUtil.memFree(shortBuffer);
        }
    }

    public void close() {
        if (!this.isClosed) {
            this.isClosed = true;
            try {
                STBVorbis.stb_vorbis_close(decoderHandle);
            } finally {
                MemoryUtil.memFree(this.inputBuffer);
                if (!this.isEndOfStream && this.cacheBuilder != null) {
                    this.cacheBuilder.discard();
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return this.isClosed;
    }
}
