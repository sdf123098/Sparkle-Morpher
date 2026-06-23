package com.micaftic.morpher.core.algorithms;

import com.micaftic.morpher.NativeLibLoader;
import com.ysm.parser.YSMNative;
import com.micaftic.morpher.core.zstd.ZstdUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class YsmZstd {
    private static final int ZSTD_MAGIC = 0xFD2FB528;
    private static final int ZSTD_RAW_BLOCK = 0;
    private static final int ZSTD_RLE_BLOCK = 1;
    private static final int ZSTD_COMPRESSED_BLOCK = 2;
    private static final int ZSTD_MAX_BLOCK_SIZE = 128 * 1024;

    public static byte[] decompress(byte[] rawData) throws IOException {
        return decompress(rawData, 0, rawData.length);
    }

    public static byte[] decompress(byte[] rawData, int offset, int length) throws IOException {
        try {
            return decompressWithYsmNative(rawData, offset, length);
        } catch (Throwable ignored) {
        }
        YsmZstd.washInPlace(rawData, offset, length);
        return standardDecompress(rawData, offset, length);
    }

    public static byte[] compress(byte[] rawData) {
        return compress(rawData, 0, rawData.length);
    }

    public static byte[] compress(byte[] rawData, int offset, int length) {
        try {
            return compressWithYsmNative(rawData, offset, length, 3);
        } catch (Throwable ignored) {
        }
        byte[] zstdData = standardCompress(rawData, offset, length, 3);
        return YsmZstd.obfuscate(zstdData);
    }

    private static byte[] decompressWithYsmNative(byte[] rawData, int offset, int length) {
        if (!NativeLibLoader.isLoaded()) {
            throw new UnsatisfiedLinkError("YSM native library is not loaded");
        }
        return YSMNative.ysmZstdDecompress(copyInput(rawData, offset, length));
    }

    private static byte[] compressWithYsmNative(byte[] rawData, int offset, int length, int level) {
        if (!NativeLibLoader.isLoaded()) {
            throw new UnsatisfiedLinkError("YSM native library is not loaded");
        }
        return YSMNative.ysmZstdCompress(copyInput(rawData, offset, length), level);
    }

    private static byte[] standardDecompress(byte[] rawData, int offset, int length) throws IOException {
        byte[] zstdData = offset == 0 && length == rawData.length
                ? rawData
                : Arrays.copyOfRange(rawData, offset, offset + length);
        Throwable failure = null;
        try {
            return decompressWithNative(zstdData);
        } catch (Throwable nativeError) {
            failure = nativeError;
        }
        // zstd-jni layer removed (was: YSM native → zstd-jni → Java); now: YSM native → Java ZstdUtil → raw-frame fallback.
        try {
            return ZstdUtil.decompress(zstdData);
        } catch (Throwable fallbackError) {
            failure.addSuppressed(fallbackError);
            try {
                return decompressRawFrame(zstdData);
            } catch (Throwable rawFrameError) {
                failure.addSuppressed(rawFrameError);
            }
            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to decompress YSM ZSTD data", failure);
        }
    }

    private static byte[] decompressWithNative(byte[] zstdData) {
        if (!NativeLibLoader.isLoaded()) {
            throw new UnsatisfiedLinkError("YSM native library is not loaded");
        }
        return YSMNative.zstdDecompress(zstdData);
    }

    private static byte[] standardCompress(byte[] rawData, int offset, int length, int level) {
        byte[] input = offset == 0 && length == rawData.length
                ? rawData
                : Arrays.copyOfRange(rawData, offset, offset + length);
        try {
            return compressWithNative(input, level);
        } catch (Throwable nativeError) {
            // zstd-jni compress layer removed; fall back to pure-Java ZstdUtil, then raw-frame as last resort.
            if (NativeLibLoader.isOnAndroid()) {
                return compressRawFrame(input);
            }
            try {
                byte[] javaCompressed = ZstdUtil.compress(input, 0, input.length, level);
                ZstdUtil.decompress(javaCompressed);
                return javaCompressed;
            } catch (Throwable fallbackError) {
                return compressRawFrame(input);
            }
        }
    }

    private static byte[] compressWithNative(byte[] input, int level) {
        if (!NativeLibLoader.isLoaded()) {
            throw new UnsatisfiedLinkError("YSM native library is not loaded");
        }
        return YSMNative.zstdCompress(input, level);
    }

    private static byte[] copyInput(byte[] input, int offset, int length) {
        if (offset == 0 && length == input.length) {
            return input;
        }
        return Arrays.copyOfRange(input, offset, offset + length);
    }

    private static byte[] wash(byte[] data) {
        washInPlace(data, 0, data.length);
        return data;
    }

    private static void washInPlace(byte[] data, int base, int length) {
        if (data == null || length < 5) {
            throw new IllegalArgumentException("Invalid data length");
        }

        int magic = (data[base] & 0xFF)
                | ((data[base + 1] & 0xFF) << 8)
                | ((data[base + 2] & 0xFF) << 16)
                | ((data[base + 3] & 0xFF) << 24);
        if (magic != 0xFD2FB528) {
            throw new IllegalArgumentException("Not a standard ZSTD Magic Number. May be skippable frame or unknown.");
        }

        byte fhd = data[base + 4];

        int frameHeaderSize = calculateFrameHeaderSize(fhd);
        int offset = base + 4 + frameHeaderSize;
        int end = base + length;

        while (offset + 3 <= end) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int lastBlock = (b0 >> 7) & 1;
            int blockTypeYSM = (b0 >> 5) & 3;

            int rawSize = ((b0 & 0x1F) << 16) | b1 | (b2 << 8);
            int cSize = rawSize ^ 0xD4E9;
            int blockTypeStd = switch (blockTypeYSM) {
                case 0 -> 2;
                case 1 -> 1;
                case 2 -> 3;
                case 3 -> 0;
                default -> throw new IllegalStateException("Unknown block type");
            };

            int stdHeader = lastBlock | (blockTypeStd << 1) | (cSize << 3);

            data[offset] = (byte) (stdHeader & 0xFF);
            data[offset + 1] = (byte) ((stdHeader >> 8) & 0xFF);
            data[offset + 2] = (byte) ((stdHeader >> 16) & 0xFF);

            int blockDataSize = (blockTypeStd == 1) ? 1 : cSize;
            offset += 3 + blockDataSize;

            if (lastBlock == 1) {
                break;
            }
        }
    }

    private static byte[] obfuscate(byte[] data) {
        if (data == null || data.length < 5) {
            throw new IllegalArgumentException("Invalid data length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt(0);
        if (magic != 0xFD2FB528) {
            throw new IllegalArgumentException("Not a standard ZSTD frame.");
        }

        byte fhd = data[4];
        int frameHeaderSize = calculateFrameHeaderSize(fhd);
        int offset = 4 + frameHeaderSize;

        while (offset + 3 <= data.length) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int cBlockHeader = b0 | (b1 << 8) | (b2 << 16);

            int lastBlock = cBlockHeader & 1;
            int blockTypeStd = (cBlockHeader >> 1) & 3;
            int cSize = cBlockHeader >> 3;

            int blockDataSize = (blockTypeStd == 1) ? 1 : cSize;

            int blockTypeYSM = switch (blockTypeStd) {
                case 0 -> 3;
                case 1 -> 1;
                case 2 -> 0;
                case 3 -> 2;
                default -> throw new IllegalStateException("Unknown block type");
            };

            int rawSize = cSize ^ 0xD4E9;
            int ysmB0 = (lastBlock << 7) | (blockTypeYSM << 5) | ((rawSize >> 16) & 0x1F);
            int ysmB1 = rawSize & 0xFF;
            int ysmB2 = (rawSize >> 8) & 0xFF;

            data[offset] = (byte) ysmB0;
            data[offset + 1] = (byte) ysmB1;
            data[offset + 2] = (byte) ysmB2;

            offset += 3 + blockDataSize;

            if (lastBlock == 1) {
                break;
            }
        }

        return data;
    }

    private static byte[] compressRawFrame(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length + 32 + ((input.length / ZSTD_MAX_BLOCK_SIZE) * 3));
        writeIntLE(out, ZSTD_MAGIC);
        writeFrameHeader(out, input.length);

        int inputOffset = 0;
        int remaining = input.length;
        if (remaining == 0) {
            writeBlockHeader(out, true, ZSTD_RAW_BLOCK, 0);
            return out.toByteArray();
        }

        while (remaining > 0) {
            int blockSize = Math.min(remaining, ZSTD_MAX_BLOCK_SIZE);
            remaining -= blockSize;
            writeBlockHeader(out, remaining == 0, ZSTD_RAW_BLOCK, blockSize);
            out.write(input, inputOffset, blockSize);
            inputOffset += blockSize;
        }
        return out.toByteArray();
    }

    private static byte[] decompressRawFrame(byte[] data) throws IOException {
        if (data == null || data.length < 6 || readIntLE(data, 0) != ZSTD_MAGIC) {
            throw new IOException("Not a raw ZSTD frame");
        }

        int offset = 4;
        int descriptor = data[offset++] & 0xFF;
        boolean singleSegment = (descriptor & 0x20) != 0;
        int dictionaryDescriptor = descriptor & 0x03;
        int contentSizeDescriptor = descriptor >>> 6;

        if (!singleSegment) {
            ensureAvailable(data, offset, 1);
            offset++;
        }

        int dictionarySize = dictionaryDescriptor == 0 ? 0 : 1 << (dictionaryDescriptor - 1);
        if (dictionarySize != 0) {
            throw new IOException("ZSTD dictionaries are not supported by raw-frame fallback");
        }

        long contentSize = -1;
        switch (contentSizeDescriptor) {
            case 0 -> {
                if (singleSegment) {
                    ensureAvailable(data, offset, 1);
                    contentSize = data[offset++] & 0xFFL;
                }
            }
            case 1 -> {
                ensureAvailable(data, offset, 2);
                contentSize = (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8);
                contentSize += 256;
                offset += 2;
            }
            case 2 -> {
                ensureAvailable(data, offset, 4);
                contentSize = readIntLE(data, offset) & 0xFFFF_FFFFL;
                offset += 4;
            }
            case 3 -> {
                ensureAvailable(data, offset, 8);
                contentSize = readLongLE(data, offset);
                offset += 8;
            }
            default -> throw new IOException("Invalid ZSTD frame content size descriptor");
        }

        if (contentSize > Integer.MAX_VALUE) {
            throw new IOException("ZSTD frame is too large: " + contentSize);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(contentSize >= 0 ? (int) contentSize : Math.max(64 * 1024, data.length * 2));
        boolean lastBlock;
        do {
            ensureAvailable(data, offset, 3);
            int header = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16);
            offset += 3;

            lastBlock = (header & 1) != 0;
            int blockType = (header >>> 1) & 0x03;
            int blockSize = header >>> 3;

            switch (blockType) {
                case ZSTD_RAW_BLOCK -> {
                    ensureAvailable(data, offset, blockSize);
                    out.write(data, offset, blockSize);
                    offset += blockSize;
                }
                case ZSTD_RLE_BLOCK -> {
                    ensureAvailable(data, offset, 1);
                    byte value = data[offset++];
                    for (int i = 0; i < blockSize; i++) {
                        out.write(value);
                    }
                }
                case ZSTD_COMPRESSED_BLOCK -> throw new IOException("Compressed ZSTD blocks require native or Java ZSTD support");
                default -> throw new IOException("Invalid ZSTD block type");
            }
        } while (!lastBlock);

        byte[] result = out.toByteArray();
        if (contentSize >= 0 && result.length != (int) contentSize) {
            throw new IOException("ZSTD raw frame size mismatch: expected " + contentSize + ", got " + result.length);
        }
        return result;
    }

    private static void writeFrameHeader(ByteArrayOutputStream out, int contentSize) {
        if (contentSize < 256) {
            out.write(0x20);
            out.write(contentSize);
        } else if (contentSize < 65536 + 256) {
            out.write(0x60);
            writeShortLE(out, contentSize - 256);
        } else {
            out.write(0xA0);
            writeIntLE(out, contentSize);
        }
    }

    private static void writeBlockHeader(ByteArrayOutputStream out, boolean lastBlock, int blockType, int blockSize) {
        int header = (lastBlock ? 1 : 0) | (blockType << 1) | (blockSize << 3);
        out.write(header & 0xFF);
        out.write((header >>> 8) & 0xFF);
        out.write((header >>> 16) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static long readLongLE(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24)
                | ((data[offset + 4] & 0xFFL) << 32)
                | ((data[offset + 5] & 0xFFL) << 40)
                | ((data[offset + 6] & 0xFFL) << 48)
                | ((data[offset + 7] & 0xFFL) << 56);
    }

    private static void ensureAvailable(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IOException("Truncated ZSTD frame");
        }
    }

    private static int calculateFrameHeaderSize(byte fhd) {
        int size = 1;
        int fcsFieldSize = fhd & 3;
        boolean singleSegment = ((fhd >> 5) & 1) == 1;
        int dictIdFlag = (fhd >> 0) & 3;

        int dictIdSize = 0;
        int dictIdBits = fhd & 3;
        if (dictIdBits == 1) dictIdSize = 1;
        else if (dictIdBits == 2) dictIdSize = 2;
        else if (dictIdBits == 3) dictIdSize = 4;

        int fcsSize = 0;
        int fcsBits = (fhd >> 6) & 3;
        if (fcsBits == 0) fcsSize = singleSegment ? 1 : 0;
        else if (fcsBits == 1) fcsSize = 2;
        else if (fcsBits == 2) fcsSize = 4;
        else if (fcsBits == 3) fcsSize = 8;

        int windowDescSize = singleSegment ? 0 : 1;

        return size + windowDescSize + dictIdSize + fcsSize;
    }
}
