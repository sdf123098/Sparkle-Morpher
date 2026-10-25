package com.micaftic.morpher.resource;

import com.micaftic.morpher.core.model.lifecycle.ModelDecodeAdmission;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static com.micaftic.morpher.util.DigestUtil.sha256Hex;

/**
 * 1.2.7 §24.2：从 {@code YSMFolderDeserializer} 外提的贴图解析职责（行为等价，纯搬运）。
 *
 * 负责图像格式嗅探（{@link #detectFormat(byte[])}）、尺寸元数据解析与 RawTexture 包装；
 * 不涉及模型装配策略。
 */
public final class YsmTextureParsing {

    private YsmTextureParsing() {
    }

    /** 静态入口：把 PNG 等图像字节包装成 RawTexture（供 Bedrock 直读导入用）。 */
    public static RawYsmModel.RawTexture parseBedrockTexture(byte[] imageBytes, String name) {
        RawYsmModel.RawTexture texture = new RawYsmModel.RawTexture();
        if (imageBytes == null || imageBytes.length == 0) {
            return texture;
        }
        try {
            ImageMeta meta = parseImageMeta(imageBytes, name);
            texture.hash = sha256Hex(imageBytes);
            texture.width = meta.width();
            texture.height = meta.height();
            texture.imageFormat = meta.format();
            texture.name = name;
            texture.data = imageBytes;
            texture.unknownFlag = 1;
        } catch (Exception e) {
            System.err.println("[SM] Failed to parse Bedrock texture " + name + ": " + e);
        }
        return texture;
    }

    public static ImageMeta parseImageMeta(byte[] data, String path) {
        if (data == null || data.length < 8) {
            throw new RuntimeException("Invalid image data. File too small: " + path);
        }

        int format = detectFormat(data);
        if (format == 0) {
            throw new RuntimeException("Unsupported image format for: " + path);
        }

        try {
            ImageHeaderDimensions.Size declared = ImageHeaderDimensions.read(data, format);
            if (declared != null) {
                if (!ModelDecodeAdmission.textureFits(declared.width(), declared.height(), 2)) {
                    throw new IOException("Image exceeds the decode budget ("
                            + declared.width() + "x" + declared.height() + "): " + path);
                }
                return new ImageMeta(declared.width(), declared.height(), format);
            }

            BufferedImage img = null;
            switch (format) {
                case 1, 3 -> img = readImageInMemory(data, path);
                case 4 -> img = new WebpDecoder().read(data);
                case 5 -> img = new AvifDecoder().read(data);
            }
            if (img != null) {
                return new ImageMeta(img.getWidth(), img.getHeight(), format);
            }
            throw new RuntimeException("Failed to decode image dimensions for: " + path);
        } catch (Exception e) {
            throw new RuntimeException("Error processing image: " + path, e);
        }
    }

    /** MemoryCacheImageInputStream：不启用 ImageIO 磁盘缓存；解码前按像素预算预检。 */
    private static BufferedImage readImageInMemory(byte[] data, String path) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No image reader available: " + path);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || !ModelDecodeAdmission.textureFits(width, height, 2)) {
                    throw new IOException("Image exceeds the decode budget ("
                            + width + "x" + height + "): " + path);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    public static int detectFormat(byte[] data) {
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) return 1; // 'BM'
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return 2; // PNG
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return 3; // JPEG
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return 4; // WEBP RIFF...WEBP
        if (data.length >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') return 5; // AVIF ftyp
        return 0;
    }
}
