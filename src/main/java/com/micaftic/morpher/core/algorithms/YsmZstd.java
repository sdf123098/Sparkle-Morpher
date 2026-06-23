package com.micaftic.morpher.core.algorithms;

import com.micaftic.morpher.NativeLibLoader;
import com.ysm.parser.YSMNative;
import org.apache.commons.io.FileUtils;
import com.micaftic.morpher.core.zstd.ZstdUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class YsmZstd {
    public static byte[] decompress(byte[] rawData) throws IOException {
        return decompress(rawData, 0, rawData.length);
    }

    public static byte[] decompress(byte[] rawData, int offset, int length) throws IOException {
/*
        if(NativeLibLoader.isLoaded())
            return YSMNative.ysmZstdDecompress(rawData);
*/
        try {
            return decompressStrict(rawData, offset, length);
        } catch (Throwable strictError) {
            try {
                byte[] zstdData = copyInput(rawData, offset, length);
                YsmZstd.washInPlace(zstdData, 0, zstdData.length, false);
                return ZstdUtil.decompress(zstdData);
            } catch (Throwable legacyError) {
                strictError.addSuppressed(legacyError);
                if (strictError instanceof IOException ioException) {
                    throw ioException;
                }
                if (strictError instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IOException("Failed to decompress YSM ZSTD data", strictError);
            }
        }
    }

    public static byte[] decompressStrict(byte[] rawData, int offset, int length) throws IOException {
        byte[] zstdData = copyInput(rawData, offset, length);
        YsmZstd.washInPlace(zstdData, 0, zstdData.length, true);
        return ZstdUtil.decompress(zstdData);
    }

    public static byte[] compress(byte[] rawData) {
        return compress(rawData, 0, rawData.length);
    }

    public static byte[] compress(byte[] rawData, int offset, int length) {
    /*    if(NativeLibLoader.isLoaded())
            return YSMNative.ysmZstdCompress(rawData,3);*/
        byte[] zstdData = ZstdUtil.compress(rawData, offset, length, 3);
        return YsmZstd.obfuscate(zstdData);
    }

    private static byte[] wash(byte[] data) {
        washInPlace(data, 0, data.length, false);
        return data;
    }

    private static void washInPlace(byte[] data, int base, int length) {
        washInPlace(data, base, length, false);
    }

    private static void washInPlace(byte[] data, int base, int length, boolean preserveChecksumFlag) {
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
        if (!preserveChecksumFlag) {
            data[base + 4] = (byte) (fhd & 0xFB);
        }

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

    private static byte[] copyInput(byte[] input, int offset, int length) {
        if (offset == 0 && length == input.length) {
            return Arrays.copyOf(input, input.length);
        }
        return Arrays.copyOfRange(input, offset, offset + length);
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
