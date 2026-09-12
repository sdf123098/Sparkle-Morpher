package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.resource.YSMFolderDeserializer;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
                if (width > 0 && height > 0 && data.length >= width * height * 4) {
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    int[] pixels = new int[width * height];
                    for (int i = 0; i < pixels.length; i++) {
                        int r = data[i * 4] & 0xFF;
                        int g = data[i * 4 + 1] & 0xFF;
                        int b = data[i * 4 + 2] & 0xFF;
                        int a = data[i * 4 + 3] & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    img.setRGB(0, 0, width, height, pixels, 0, width);
                    return img;
                } else throw new RuntimeException("Invalid RGBA texture");
            } else {
                switch (imageFormat) {
                    case 1:
                    case 2:
                    case 3:
                        return ImageIO.read(new ByteArrayInputStream(data));
                    case 4:
                        return new WebpDecoder().read(data);
                    case 5:
                        return new AvifDecoder().read(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
