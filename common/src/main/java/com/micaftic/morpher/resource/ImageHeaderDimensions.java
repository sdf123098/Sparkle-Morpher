package com.micaftic.morpher.resource;

/**
 * 直接从容器头读取声明尺寸，不触发任何解码。
 *
 * <p>ImageIO 的 {@code ImageReader#getWidth} 不保证只读头：部分 SPI（如 ImageStream 的 JPEG
 * reader）会先解码整张图。若先用 getWidth 再做像素预算检查，超大图已经分配完像素，预检形同虚设；
 * 因此尺寸元数据与预算判断都只依赖这里。</p>
 */
public final class ImageHeaderDimensions {

    public record Size(int width, int height) {
        public Size {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Non-positive image dimensions");
            }
        }
    }

    private ImageHeaderDimensions() {
    }

    /** format 与 {@link YsmTextureParsing#detectFormat(byte[])} 一致；头不完整或无法识别时返回 null。 */
    public static Size read(byte[] data, int format) {
        if (data == null) {
            return null;
        }
        try {
            return switch (format) {
                case 1 -> bmp(data);
                case 2 -> png(data);
                case 3 -> jpeg(data);
                default -> null;
            };
        } catch (IndexOutOfBoundsException malformed) {
            return null;
        }
    }

    private static Size png(byte[] data) {
        if (data.length < 24
                || data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            return null;
        }
        return size(u32be(data, 16), u32be(data, 20));
    }

    private static Size jpeg(byte[] data) {
        int offset = 2;
        while (offset + 4 <= data.length) {
            if ((data[offset] & 0xFF) != 0xFF) {
                offset++;
                continue;
            }
            int marker = data[offset + 1] & 0xFF;
            if (marker == 0xFF) {
                offset++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                offset += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                return null;
            }
            int segmentLength = u16be(data, offset + 2);
            if (segmentLength < 2 || offset + 2 + segmentLength > data.length) {
                return null;
            }
            boolean startOfFrame = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (startOfFrame) {
                if (segmentLength < 7) {
                    return null;
                }
                return size(u16be(data, offset + 7), u16be(data, offset + 5));
            }
            offset += 2 + segmentLength;
        }
        return null;
    }

    private static Size bmp(byte[] data) {
        if (data.length < 26 || data[0] != 'B' || data[1] != 'M') {
            return null;
        }
        long dibHeaderSize = u32le(data, 14);
        if (dibHeaderSize == 12) {
            return size(u16le(data, 18), u16le(data, 20));
        }
        if (dibHeaderSize < 40) {
            return null;
        }
        long width = s32le(data, 18);
        long height = Math.abs((long) s32le(data, 22));
        return size(width, height);
    }

    private static Size size(long width, long height) {
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            return null;
        }
        return new Size((int) width, (int) height);
    }

    private static int u16be(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static long u32be(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long u32le(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static int s32le(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
