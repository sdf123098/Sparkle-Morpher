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
    private final ByteBuffer inputBuffer;
    private final long decoderHandle;
    private final int channels;

    @Nullable
    private final AudioCacheBuilder cacheBuilder;

    private volatile boolean isClosed;
    private boolean isEndOfStream;

    public OggVorbisAudioStream(ByteBuffer byteBuffer, @Nullable AudioCacheBuilder cacheBuilder)
            throws UnsupportedAudioFileException, IOException {

        this.inputBuffer = MemoryUtil.memAlloc(byteBuffer.remaining());
        this.inputBuffer.put(byteBuffer.duplicate());
        this.inputBuffer.flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);

            decoderHandle = STBVorbis.stb_vorbis_open_memory(this.inputBuffer, error, null);
            if (decoderHandle == MemoryUtil.NULL) {
                MemoryUtil.memFree(this.inputBuffer);
                throw new IOException("Failed to open OGG Vorbis stream, error: " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.malloc();
            STBVorbis.stb_vorbis_get_info(decoderHandle, info);
            channels = info.channels();
            int sampleRate = info.sample_rate();
            info.free();

            if (channels != 1 && channels != 2) {
                STBVorbis.stb_vorbis_close(decoderHandle);
                MemoryUtil.memFree(this.inputBuffer);
                throw new UnsupportedAudioFileException("Unsupported channel count: " + channels);
            }

            this.audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
            this.cacheBuilder = cacheBuilder;
        }
    }

    @NotNull
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    @NotNull
    public ByteBuffer read(int byteCount) throws IOException {
        if (this.isEndOfStream || this.isClosed) {
            return EMPTY_BUFFER;
        }

        int requestedMonoSamples = byteCount / 2;
        if (requestedMonoSamples <= 0) {
            return EMPTY_BUFFER;
        }

        ShortBuffer shortBuffer = MemoryUtil.memAllocShort(requestedMonoSamples * channels);
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

            ByteBuffer byteBuf = BufferUtils.createByteBuffer(samplesRead * channels * 2).order(ByteOrder.LITTLE_ENDIAN);

            if (channels == 2) {
                ByteBuffer monoBuf = BufferUtils.createByteBuffer(samplesRead * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < samplesRead; i++) {
                    short left = readView.get();
                    short right = readView.get();
                    monoBuf.putShort((short) Math.round((left + right) / 2.0f));
                }
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
            STBVorbis.stb_vorbis_close(decoderHandle);
            MemoryUtil.memFree(this.inputBuffer);
            this.isClosed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return this.isClosed;
    }
}
