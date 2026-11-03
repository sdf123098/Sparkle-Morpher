package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.audio.AudioCodec;
import com.micaftic.morpher.audio.AudioTrackData;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.vorbis.VorbisFile;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的音频资源映射职责（行为等价，纯搬运）。
 *
 * 只做"OGG 字节 → AudioTrackData"的格式识别（Opus / Vorbis）与时长解析，
 * 不涉及模型装配策略。
 */
public final class AudioResourceMapper {

    private AudioResourceMapper() {
    }

    public static AudioTrackData parseAudioTrackData(byte[] oggData) {
        if (oggData == null || oggData.length < 8) return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(oggData);
            OggFile oggFile = new OggFile(bais);
            String header = new String(oggData, 0, Math.min(oggData.length, 100), StandardCharsets.US_ASCII);
            boolean isOpus = header.contains("OpusHead");

            AudioCodec codec = isOpus ? AudioCodec.OPUS : AudioCodec.VORBIS;
            int sampleRate;
            if (isOpus) {
                OpusFile opus = new OpusFile(oggFile);
                sampleRate = (int) opus.getInfo().getRate();
            } else {
                VorbisFile vorbis = new VorbisFile(oggFile);
                sampleRate = (int) vorbis.getInfo().getRate();
            }

            OggPacketReader reader = oggFile.getPacketReader();
            long durationSamples = 0;
            var packet = reader.getNextPacket();
            while (packet != null) {
                long granule = packet.getGranulePosition();
                if (granule > 0) durationSamples = granule;
                packet = reader.getNextPacket();
            }

            return new AudioTrackData(ByteBuffer.wrap(oggData), codec.ordinal(), sampleRate, durationSamples);
        } catch (Exception e) {
            return null;
        }
    }
}
