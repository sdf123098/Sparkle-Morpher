package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.resource.YSMFolderDeserializer;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的贴图解码职责（行为等价，纯搬运）。
 *
 * 只负责"字节 → BufferedImage → PNG"这一段格式处理，不涉及模型装配策略。
 * 旧入口 {@code YSMClientMapper.toPng} 保留为 delegate（Facade First）。
 */
public final class TextureDecoder {

    private TextureDecoder() {
    }

    private static final byte[] TRANSPARENT_PIXEL_PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAEtAJJXIDTjwAAAABJRU5ErkJggg==");

    public static BufferedImage decodeToImage(byte[] data, int imageFormat, int width, int height) {
        if (data == null || data.length == 0) {
            return null;
        }

        imageFormat = resolveImageFormat(data, imageFormat);

        try {
            if (imageFormat == -1) {
                long pixelCount = (long) width * height;
                if (width > 0 && height > 0 && pixelCount <= data.length / 4L) {
                    requireTextureMemory(width, height, 1);
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    // TYPE_INT_ARGB owns a contiguous raster. Write it directly instead of
                    // allocating another full-sized pixel array and copying via setRGB.
                    int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                    for (int i = 0; i < pixels.length; i++) {
                        int r = data[i * 4] & 0xFF;
                        int g = data[i * 4 + 1] & 0xFF;
                        int b = data[i * 4 + 2] & 0xFF;
                        int a = data[i * 4 + 3] & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    return img;
                } else throw new RuntimeException("Invalid RGBA texture");
            } else {
                switch (imageFormat) {
                    case 1:
                    case 2:
                    case 3:
                        return readImageWithMemoryGuard(data);
                    case 4:
                        if (width > 0 && height > 0) requireTextureMemory(width, height, 3);
                        return new WebpDecoder().read(data);
                    case 5:
                        if (width > 0 && height > 0) requireTextureMemory(width, height, 3);
                        return new AvifDecoder().read(data);
                }
            }
        } catch (UncheckedIOException memoryLimit) {
            throw memoryLimit;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static BufferedImage readImageWithMemoryGuard(byte[] data) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // Trust the image header rather than potentially stale model metadata.
                requireTextureMemory(reader.getWidth(0), reader.getHeight(0), 2);
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private static void requireTextureMemory(int width, int height, int rasterCopies) {
        if (!com.micaftic.morpher.core.model.lifecycle.ModelDecodeAdmission.textureFits(width, height, rasterCopies)) {
            throw new UncheckedIOException(new IOException("Not enough free heap to decode texture "
                    + width + "x" + height + "; model import cancelled before allocating pixels"));
        }
    }

    public static boolean isPng(byte[] data) {
        return data != null
                && data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    public static byte[] encodeToPng(BufferedImage img, byte[] fallbackData) {
        if (img != null) {
            // PNG compression also allocates scanlines and a growing output buffer.
            requireTextureMemory(img.getWidth(), img.getHeight(), 3);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (ImageIO.write(img, "png", baos)) {
                    return baos.toByteArray();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (isPng(fallbackData)) {
            return fallbackData;
        }
        System.err.println("[SM] Warning: Texture decode failed; using transparent fallback PNG.");
        return TRANSPARENT_PIXEL_PNG;
    }

    public static int resolveImageFormat(byte[] data, int imageFormat) {
        if (imageFormat == -1 || data == null || data.length == 0) {
            return imageFormat;
        }
        int detectedFormat = YSMFolderDeserializer.detectFormat(data);
        if (detectedFormat != 0) {
            return detectedFormat;
        }
        return imageFormat == 0 ? 1 : imageFormat;
    }

    public static byte[] toPng(byte[] data, int imageFormat, int width, int height) {
        imageFormat = resolveImageFormat(data, imageFormat);
        if (imageFormat == 2) {
            return data;
        }
        BufferedImage img = decodeToImage(data, imageFormat, width, height);
        return encodeToPng(img, data);
    }
}
