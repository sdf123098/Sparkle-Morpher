package com.micaftic.morpher.resource.bundle;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class TextureDecodeMemoryTest {
    @Test void rgbaDecodingAllocatesOnePixelRaster() {
        byte[] rgba = new byte[1024 * 1024 * 4];
        rgba[0] = 0x12; rgba[1] = 0x34; rgba[2] = 0x56; rgba[3] = (byte) 0xAB;
        TextureDecoder.decodeToImage(new byte[4], -1, 1, 1);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        BufferedImage image = TextureDecoder.decodeToImage(rgba, -1, 1024, 1024);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(0xAB123456, image.getRGB(0, 0));
        assertTrue(allocated < 6L * 1024 * 1024, "RGBA decoder allocated duplicate raster: " + allocated);
    }

    @Test void rgbaDimensionsCannotOverflowByteLengthCheck() {
        assertNull(TextureDecoder.decodeToImage(new byte[4], -1, 65536, 65536));
    }

    @Test void encodedImageHeaderCannotBypassGuardWithSmallMetadataDimensions() {
        // A valid PNG header advertises a huge raster while source metadata claims 1x1.
        // The reader must reject it before reading pixel data (this fixture has no IDAT).
        byte[] png = new byte[33];
        ByteBuffer buffer = ByteBuffer.wrap(png);
        buffer.putLong(0x89504E470D0A1A0AL).putInt(13).putInt(0x49484452);
        buffer.putInt(100000).putInt(100000).put((byte) 8).put((byte) 6);
        buffer.put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32(); crc.update(png, 12, 17); buffer.putInt((int) crc.getValue());
        assertThrows(UncheckedIOException.class, () -> TextureDecoder.decodeToImage(png, 2, 1, 1));
    }
}
