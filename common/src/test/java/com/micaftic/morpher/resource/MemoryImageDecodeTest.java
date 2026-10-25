package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.bundle.TextureDecoder;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2：纹理元数据解码只走内存流，不落 ImageIO 磁盘缓存，并在分配像素前做预算预检。 */
class MemoryImageDecodeTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static byte[] writeBmp(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "BMP", out), "JDK BMP writer must be available");
        return out.toByteArray();
    }

    /** 只有 SOF0 头的 JPEG：足以读出声明尺寸，但没有像素数据。 */
    private static byte[] jpegHeaderOnly(int width, int height) {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
                'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 8,
                (byte) (height >> 8), (byte) height,
                (byte) (width >> 8), (byte) width,
                1, 1, 0x11, 0,
                (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 1, 1, 0, 0, 0x3F, 0,
                (byte) 0xFF, (byte) 0xD9
        };
    }

    @Test
    void pngDimensionsComeFromHeaderWithoutDiskCache() {
        ImageMeta meta = YsmTextureParsing.parseImageMeta(PNG_1X1, "tex.png");
        assertEquals(1, meta.width());
        assertEquals(1, meta.height());
        assertEquals(2, meta.format());
    }

    @Test
    void bmpDecodesThroughMemoryStream() throws Exception {
        ImageMeta meta = YsmTextureParsing.parseImageMeta(writeBmp(5, 3), "tex.bmp");
        assertEquals(5, meta.width());
        assertEquals(3, meta.height());
        assertEquals(1, meta.format());
    }

    @Test
    void pixelBombIsRejectedBeforeAllocatingRaster() {
        // 65500x65500 exceeds the signed-int raster limit regardless of the test worker heap;
        // the JDK JPEG reader itself caps dimensions at 65500.
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> YsmTextureParsing.parseImageMeta(jpegHeaderOnly(65_500, 65_500), "bomb.jpg"));
        assertTrue(String.valueOf(failure.getCause()).contains("decode budget"),
                "expected pixel budget rejection, got: " + failure.getCause());
    }

    @Test
    void textureDecodeAlsoRejectsPixelBombFromHeader() {
        assertThrows(UncheckedIOException.class,
                () -> TextureDecoder.toPng(jpegHeaderOnly(65_500, 65_500), 3, 0, 0));
    }

    @Test
    void bedrockTextureWrapsBytesWithoutCopyingFormat() {
        RawYsmModel.RawTexture texture = YsmTextureParsing.parseBedrockTexture(PNG_1X1, "tex.png");
        assertArrayEquals(PNG_1X1, texture.data);
        assertEquals(1, texture.width);
        assertEquals(1, texture.height);
        assertEquals(2, texture.imageFormat);
        assertTrue(texture.hash != null && !texture.hash.isEmpty());
    }
}
