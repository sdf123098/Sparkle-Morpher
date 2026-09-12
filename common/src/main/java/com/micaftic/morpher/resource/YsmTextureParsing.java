package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

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

        if (format == 2 && data.length >= 24) {
            int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
            return new ImageMeta(w, h, format);
        }

        try {
            BufferedImage img = null;
            switch (format) {
                case 1, 3 -> img = ImageIO.read(new ByteArrayInputStream(data));
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

    public static int detectFormat(byte[] data) {
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) return 1; // 'BM'
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return 2; // PNG
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return 3; // JPEG
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return 4; // WEBP RIFF...WEBP
        if (data.length >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') return 5; // AVIF ftyp
        return 0;
    }
}
