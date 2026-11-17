package com.micaftic.morpher.core.security;

import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.peer.PeerDecodeScope;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.codec.legacy.LegacyCipher;
import dev.spm.core.codec.legacy.LegacyHash;
import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.airlift.compress.zstd.ZstdInputStream;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Unified wire, cache, and V3 archive crypto codec delegating core primitives to
 * {@code dev.spm.core.codec.legacy} ({@link LegacyCipher}, {@link LegacyHash}) and
 * {@code io.airlift.compress.zstd}.
 */
public class YsmCrypt {
    private static volatile String modelCacheIdentity;
    public static final long SEED_PACKET_VERIFICATION = 0xEE6FA63D570BD77BL;
    public static final long SEED_KEY_DERIVATION = 0xD017CBBA7B5D3581L;
    public static final long SEED_FILE_VERIFICATION = 0x9E5599DB80C67C29L;
    public static final long SEED_RES_VERIFICATION = 0xA62B1A2C43842BC3L;
    public static final long SEED_CACHE_DECRYPTION = 0xD1C3D1D13A99752BL;
    public static final long SEED_CACHE_VERIFICATION = 0xF346451E53A22261L;

    private static final SecureRandom theRandom = new SecureRandom();
    public static final byte[] publicKey = {
            0x0F, (byte) 0xC7, 0x7E, (byte) 0xF3, (byte) 0xF4, (byte) 0xB8, 0x35, 0x3A, (byte) 0xA2, (byte) 0xBA, 0x7F, (byte) 0xD3, 0x17, 0x79, 0x46, (byte) 0x8E,
            0x65, 0x42, (byte) 0xD0, (byte) 0x98, (byte) 0x8A, (byte) 0x9B, (byte) 0xB0, 0x19, (byte) 0x80, (byte) 0x4F, (byte) 0x81, 0x56, (byte) 0x36, 0x6A, 0x12, (byte) 0x62,
            (byte) 0xBE, 0x0E, (byte) 0xE5, (byte) 0xAD, 0x47, (byte) 0x01, (byte) 0xD4, 0x5E, (byte) 0xE4, (byte) 0xEB, (byte) 0xFB, 0x36, (byte) 0xCB, 0x47, 0x42, (byte) 0x98,
            (byte) 0xF9, (byte) 0xE5, 0x7A, 0x5C, 0x3C, (byte) 0xDB, 0x2C, 0x76
    };

    public record EncryptedPacket(byte[] data, byte[] nextKey) {
    }

    public static long[] calculateModelHashes(String modelHashStr, byte[] serverKey) {
        byte[] data = (getModelCacheIdentity() + "\nmodel=" + modelHashStr).getBytes(StandardCharsets.UTF_8);
        LegacyStreamCipher.mt19937XorInPlace(data, 0, data.length, serverKey, SEED_KEY_DERIVATION);
        long hash1 = LegacyHash.seeded(data, 0, data.length, SEED_CACHE_VERIFICATION);
        long hash2 = LegacyHash.seeded(data, 0, data.length, SEED_CACHE_DECRYPTION);
        return new long[]{hash1, hash2};
    }

    public static String getModelCacheIdentity() {
        String identity = modelCacheIdentity;
        if (identity == null || identity.contains("modVersion=unknown")) {
            identity = buildModelCacheIdentity();
            modelCacheIdentity = identity;
        }
        return identity;
    }

    private static String buildModelCacheIdentity() {
        return "sparkle_morpher:model_cache\nmodVersion=" + resolveModVersion();
    }

    private static String resolveModVersion() {
        return PlatformAPI.getModVersion();
    }

    public static byte[] encryptServerCache(byte[] clearText, byte[] serverKey, long hash1, long hash2) throws Exception {
        return encryptServerCache(clearText, 0, clearText.length, serverKey, hash1, hash2);
    }

    public static byte[] encryptServerCache(byte[] clearText, int clearOffset, int clearLength, byte[] serverKey, long hash1, long hash2) throws Exception {
        byte[] zstdData = compressYsmZstd(clearText, clearOffset, clearLength);
        int paddingLength = 16 + theRandom.nextInt(112);
        int randomTop6Bits = theRandom.nextInt(64) << 10;
        int headerWord = (paddingLength & 0x3FF) | randomTop6Bits;
        byte[] payloadToEncrypt = new byte[2 + paddingLength + zstdData.length];
        payloadToEncrypt[0] = (byte) (headerWord & 0xFF);
        payloadToEncrypt[1] = (byte) ((headerWord >> 8) & 0xFF);
        byte[] padding = new byte[paddingLength];
        theRandom.nextBytes(padding);
        System.arraycopy(padding, 0, payloadToEncrypt, 2, paddingLength);
        System.arraycopy(zstdData, 0, payloadToEncrypt, 2 + paddingLength, zstdData.length);
        byte[] chachaKeyS = Arrays.copyOfRange(serverKey, 0, 32);
        byte[] chachaIvS = Arrays.copyOfRange(serverKey, 32, 56);
        LegacyStreamCipher.mt19937XorInPlace(payloadToEncrypt, 0, payloadToEncrypt.length, serverKey, SEED_KEY_DERIVATION);
        byte[] encryptedPayload = LegacyStreamCipher.dynamicEncrypt(payloadToEncrypt, chachaKeyS, chachaIvS, SEED_CACHE_DECRYPTION);
        try (YSMByteBuf headerBuf = new YSMByteBuf(Unpooled.buffer())) {
            headerBuf.writeVarInt(1);
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(32); // format
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(0);
            headerBuf.writeVarInt(0);

            int headerLen = headerBuf.getRawBuf().readableBytes();
            int finalPayloadLen = headerLen + encryptedPayload.length;
            ByteBuffer finalBuf = ByteBuffer.allocate(finalPayloadLen + 8).order(ByteOrder.LITTLE_ENDIAN);

            headerBuf.getRawBuf().readBytes(finalBuf.array(), 0, headerLen);
            finalBuf.position(headerLen);
            finalBuf.put(encryptedPayload);

            long calculatedHash = LegacyHash.seeded(finalBuf.array(), 0, finalPayloadLen, SEED_CACHE_VERIFICATION);
            finalBuf.putLong(calculatedHash ^ hash1 ^ hash2);

            return finalBuf.array();
        }
    }

    public static boolean verifyServerCache(byte[] cacheData, long hash1, long hash2) {
        if (cacheData.length < 8) return false;
        int payloadEnd = cacheData.length - 8;
        long fileSignature = ByteBuffer.wrap(cacheData, payloadEnd, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        long calculatedHash = LegacyHash.seeded(cacheData, 0, payloadEnd, SEED_CACHE_VERIFICATION);
        return fileSignature == (calculatedHash ^ hash1 ^ hash2);
    }

    public static byte[] encryptYsmFile(byte[] rawClearText) throws Exception {
        byte[] password = new byte[56];
        theRandom.nextBytes(password);

        byte[] zstdData = compressYsmZstd(rawClearText, 0, rawClearText.length);
        int paddingLength = 16 + theRandom.nextInt(112);
        int randomTop6Bits = theRandom.nextInt(64) << 10;
        int headerWord = (paddingLength & 0x3FF) | randomTop6Bits;
        byte[] encryptedBinaryData = new byte[2 + paddingLength + zstdData.length];
        encryptedBinaryData[0] = (byte) (headerWord & 0xFF);
        encryptedBinaryData[1] = (byte) ((headerWord >> 8) & 0xFF);
        byte[] padding = new byte[paddingLength];
        theRandom.nextBytes(padding);
        System.arraycopy(padding, 0, encryptedBinaryData, 2, paddingLength);
        System.arraycopy(zstdData, 0, encryptedBinaryData, 2 + paddingLength, zstdData.length);
        LegacyCipher.encrypt(encryptedBinaryData, 0, encryptedBinaryData.length, password, new CancellationToken());

        byte[] prefix = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x59, 0x53, 0x47, 0x50};
        int totalSizeWithoutHash = prefix.length + 1 + 4 + encryptedBinaryData.length + 56;
        ByteBuffer fileBuf = ByteBuffer.allocate(totalSizeWithoutHash + 8).order(ByteOrder.LITTLE_ENDIAN);
        fileBuf.put(prefix);
        fileBuf.put((byte) 0x00);
        fileBuf.putInt(3);
        fileBuf.put(encryptedBinaryData);
        fileBuf.put(password);
        fileBuf.putLong(LegacyHash.seeded(fileBuf.array(), 0, totalSizeWithoutHash, SEED_FILE_VERIFICATION));
        return fileBuf.array();
    }

    public static byte[] decryptYsmFile(byte[] fileData) throws Exception {
        if (fileData.length < 8 + 24 + 32 + 8) {
            throw new RuntimeException("Invalid YSM file: File too short.");
        }
        int headerLength = 0;
        while (headerLength < fileData.length && fileData[headerLength] != 0x00) {
            headerLength++;
        }
        int tailOffset = fileData.length - 64;
        byte[] password = Arrays.copyOfRange(fileData, tailOffset, tailOffset + 56);
        long fileHash = ByteBuffer.wrap(fileData, tailOffset + 56, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        if (LegacyHash.seeded(fileData, 0, fileData.length - 8, SEED_FILE_VERIFICATION) != fileHash) {
            throw new RuntimeException("Corrupted YSM file: File hash mismatch.");
        }
        int ptrBinaryData = headerLength + 1;
        int crypto = ByteBuffer.wrap(fileData, ptrBinaryData, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (crypto != 3) {
            throw new RuntimeException("Invalid YSM file: Crypto version is not 3.");
        }
        ptrBinaryData += 4;
        byte[] plain = Arrays.copyOfRange(fileData, ptrBinaryData, tailOffset);
        LegacyCipher.decrypt(plain, 0, plain.length, password, new CancellationToken());
        int n = ((plain[0] & 0xFF) | ((plain[1] & 0xFF) << 8)) & 0x3FF;
        int zstdOffset = 2 + n;
        return decompressYsmZstd(plain, zstdOffset, plain.length - zstdOffset, false);
    }

    public static byte[] transcodeServerDataToClientCache(byte[] serverData, byte[] serverKey, byte[] clientKey, long hash1, long hash2) throws Exception {
        try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(serverData))) {
            int headerStart = buf.getRawBuf().readerIndex();
            if (buf.readVarInt() != 1) throw new RuntimeException("Invalid YSM cache format");
            for (int i = 0; i < 8; i++) buf.readVarInt();

            int headerEnd = buf.getRawBuf().readerIndex();
            int payloadEnd = serverData.length - 8;
            if (payloadEnd <= headerEnd) {
                throw new RuntimeException("Invalid server payload size!");
            }
            int headerLen = headerEnd - headerStart;

            byte[] chachaKeyS = Arrays.copyOfRange(serverKey, 0, 32);
            byte[] chachaIvS = Arrays.copyOfRange(serverKey, 32, 56);
            byte[] plainText = LegacyStreamCipher.dynamicDecrypt(serverData, headerEnd, payloadEnd - headerEnd, chachaKeyS, chachaIvS, SEED_CACHE_DECRYPTION);
            LegacyStreamCipher.mt19937XorInPlace(plainText, 0, plainText.length, serverKey, SEED_KEY_DERIVATION);

            byte[] chachaKeyC = Arrays.copyOfRange(clientKey, 0, 32);
            byte[] chachaIvC = Arrays.copyOfRange(clientKey, 32, 56);
            LegacyStreamCipher.mt19937XorInPlace(plainText, 0, plainText.length, clientKey, SEED_KEY_DERIVATION);
            byte[] clientEncryptedPayload = LegacyStreamCipher.dynamicEncrypt(plainText, chachaKeyC, chachaIvC, SEED_CACHE_DECRYPTION);

            int finalPayloadLen = headerLen + clientEncryptedPayload.length;
            ByteBuffer finalBuf = ByteBuffer.allocate(finalPayloadLen + 8).order(ByteOrder.LITTLE_ENDIAN);
            finalBuf.put(serverData, headerStart, headerLen);
            finalBuf.put(clientEncryptedPayload);
            long calculatedHash = LegacyHash.seeded(finalBuf.array(), 0, finalPayloadLen, SEED_CACHE_VERIFICATION);
            finalBuf.putLong(calculatedHash ^ hash1 ^ hash2);
            return finalBuf.array();
        }
    }

    public static byte[] decrypt(byte[] packet, byte[] key) throws Exception {
        if (packet.length <= 11) throw new RuntimeException("Packet too short!");
        int payloadLen = packet.length - 8;
        long packetHash = ByteBuffer.wrap(packet, payloadLen, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        long calculatedHash = LegacyHash.seeded(packet, 0, payloadLen, SEED_PACKET_VERIFICATION);
        if (calculatedHash != packetHash) {
            System.err.println("Integrity compromised: " + Base64.getEncoder().encodeToString(packet));
        }
        byte[] xoredData = LegacyStreamCipher.mt19937Xor(packet, 0, payloadLen, key, SEED_KEY_DERIVATION);
        byte[] chachaKey = Arrays.copyOfRange(key, 0, 32);
        byte[] chachaIv = Arrays.copyOfRange(key, 32, 56);
        LegacyStreamCipher.xchacha30Into(chachaKey, chachaIv, xoredData, 0, xoredData, 0, xoredData.length);
        return xoredData;
    }

    public static EncryptedPacket encrypt(byte[] payload, byte[] currentKeyIv, boolean appendNextKey) throws Exception {
        byte[] fullPlaintext;
        byte[] nextKeyIv = null;
        if (appendNextKey) {
            nextKeyIv = new byte[56];
            theRandom.nextBytes(nextKeyIv);
            fullPlaintext = new byte[payload.length + 56];
            System.arraycopy(payload, 0, fullPlaintext, 0, payload.length);
            System.arraycopy(nextKeyIv, 0, fullPlaintext, payload.length, 56);
        } else {
            fullPlaintext = payload;
        }
        byte[] key = Arrays.copyOfRange(currentKeyIv, 0, 32);
        byte[] iv = Arrays.copyOfRange(currentKeyIv, 32, 56);
        byte[] step1Encrypted = LegacyStreamCipher.xchacha30(key, iv, fullPlaintext, 0, fullPlaintext.length);
        byte[] step2Xorred = LegacyStreamCipher.mt19937Xor(step1Encrypted, 0, step1Encrypted.length, currentKeyIv, SEED_KEY_DERIVATION);
        long hash = LegacyHash.seeded(step2Xorred, 0, step2Xorred.length, SEED_PACKET_VERIFICATION);
        ByteBuffer finalPacket = ByteBuffer.allocate(step2Xorred.length + 8).order(ByteOrder.LITTLE_ENDIAN);
        finalPacket.put(step2Xorred);
        finalPacket.putLong(hash);
        return new EncryptedPacket(finalPacket.array(), nextKeyIv);
    }

    public static byte[] read(byte[] cacheFileData, byte[] clientKey) throws Exception {
        return read(cacheFileData, clientKey, false);
    }

    public static byte[] readStrict(byte[] cacheFileData, byte[] clientKey) throws Exception {
        return read(cacheFileData, clientKey, true);
    }

    public static byte[] readInPlace(byte[] cacheFileData, byte[] clientKey) throws Exception {
        try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(cacheFileData))) {
            for (int i = 0; i < 9; i++) buf.readVarInt();
            int headerEnd = buf.getRawBuf().readerIndex();
            int payloadEnd = cacheFileData.length - 8;
            if (payloadEnd <= headerEnd) {
                throw new RuntimeException("Cache file is too small or corrupted!");
            }
            byte[] chachaKeyC = Arrays.copyOfRange(clientKey, 0, 32);
            byte[] chachaIvC = Arrays.copyOfRange(clientKey, 32, 56);
            int payloadLength = payloadEnd - headerEnd;
            LegacyStreamCipher.dynamicDecryptInPlace(cacheFileData, headerEnd, payloadLength, chachaKeyC, chachaIvC, SEED_CACHE_DECRYPTION);
            LegacyStreamCipher.mt19937XorInPlace(cacheFileData, headerEnd, payloadLength, clientKey, SEED_KEY_DERIVATION);
            int n = ((cacheFileData[headerEnd] & 0xFF) | ((cacheFileData[headerEnd + 1] & 0xFF) << 8)) & 0x3FF;
            int zstdOffset = headerEnd + 2 + n;
            return decompressYsmZstd(cacheFileData, zstdOffset, payloadEnd - zstdOffset, false);
        }
    }

    private static byte[] read(byte[] cacheFileData, byte[] clientKey, boolean strictZstd) throws Exception {
        try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(cacheFileData))) {
            for (int i = 0; i < 9; i++) buf.readVarInt();
            int headerEnd = buf.getRawBuf().readerIndex();
            int payloadEnd = cacheFileData.length - 8;
            if (payloadEnd <= headerEnd) {
                throw new RuntimeException("Cache file is too small or corrupted!");
            }
            byte[] chachaKeyC = Arrays.copyOfRange(clientKey, 0, 32);
            byte[] chachaIvC = Arrays.copyOfRange(clientKey, 32, 56);
            byte[] plainText = LegacyStreamCipher.dynamicDecrypt(cacheFileData, headerEnd, payloadEnd - headerEnd, chachaKeyC, chachaIvC, SEED_CACHE_DECRYPTION);
            LegacyStreamCipher.mt19937XorInPlace(plainText, 0, plainText.length, clientKey, SEED_KEY_DERIVATION);
            int n = ((plainText[0] & 0xFF) | ((plainText[1] & 0xFF) << 8)) & 0x3FF;
            int zstdOffset = 2 + n;
            return decompressYsmZstd(plainText, zstdOffset, plainText.length - zstdOffset, strictZstd);
        }
    }

    private static byte[] compressYsmZstd(byte[] rawData, int offset, int length) {
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] buf = new byte[compressor.maxCompressedLength(length)];
        int compressedLen = compressor.compress(rawData, offset, length, buf, 0, buf.length);
        byte[] zstdData = Arrays.copyOf(buf, compressedLen);
        obfuscateZstdBlocks(zstdData);
        return zstdData;
    }

    private static byte[] decompressYsmZstd(byte[] rawData, int offset, int length, boolean strict) throws IOException {
        if (PeerDecodeScope.active()) {
            PeerDecodeScope.allocate(length);
            return decompressYsmZstdOnce(rawData, offset, length, true);
        }
        if (strict) {
            return decompressYsmZstdOnce(rawData, offset, length, true);
        }
        try {
            return decompressYsmZstdOnce(rawData, offset, length, true);
        } catch (Throwable strictError) {
            try {
                return decompressYsmZstdOnce(rawData, offset, length, false);
            } catch (Throwable legacyError) {
                strictError.addSuppressed(legacyError);
                if (strictError instanceof IOException io) throw io;
                if (strictError instanceof RuntimeException rt) throw rt;
                throw new IOException("Failed to decompress YSM ZSTD data", strictError);
            }
        }
    }

    private static byte[] decompressYsmZstdOnce(byte[] rawData, int offset, int length, boolean preserveChecksum) throws IOException {
        byte[] clean = Arrays.copyOfRange(rawData, offset, offset + length);
        washZstdInPlace(clean, preserveChecksum);
        long declared = ZstdDecompressor.getDecompressedSize(clean, 0, clean.length);
        if (declared >= 0) {
            PeerDecodeScope.expanded(declared);
            PeerDecodeScope.allocate(declared);
            byte[] out = new byte[(int) declared];
            new ZstdDecompressor().decompress(clean, 0, clean.length, out, 0, out.length);
            return out;
        }
        try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(clean))) {
            PeerDecodeScope.allocate(Math.max(64 * 1024L, clean.length * 2L) + 64 * 1024L);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64 * 1024, clean.length * 2));
            byte[] tmp = new byte[64 * 1024];
            int n;
            while ((n = in.read(tmp)) != -1) {
                PeerDecodeScope.expanded((long) out.size() + n);
                PeerDecodeScope.allocate(3L * n);
                out.write(tmp, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void washZstdInPlace(byte[] data, boolean preserveChecksumFlag) {
        if (data.length < 5) throw new IllegalArgumentException("Invalid zstd data length");
        int magic = LegacyHash.littleInt(data, 0);
        if (magic != 0xFD2FB528) throw new IllegalArgumentException("Not a standard ZSTD Magic Number");
        byte fhd = data[4];
        if (!preserveChecksumFlag) {
            data[4] = (byte) (fhd & 0xFB);
        }
        int cursor = 4 + zstdFrameHeaderSize(fhd);
        while (cursor + 3 <= data.length) {
            int b0 = data[cursor] & 0xFF;
            int b1 = data[cursor + 1] & 0xFF;
            int b2 = data[cursor + 2] & 0xFF;
            int lastBlock = (b0 >> 7) & 1;
            int blockTypeYsm = (b0 >> 5) & 3;
            int rawSize = ((b0 & 0x1F) << 16) | b1 | (b2 << 8);
            int cSize = rawSize ^ 0xD4E9;
            int blockTypeStd = switch (blockTypeYsm) {
                case 0 -> 2;
                case 1 -> 1;
                case 2 -> 3;
                case 3 -> 0;
                default -> throw new IllegalStateException("Unknown block type");
            };
            int stdHeader = lastBlock | (blockTypeStd << 1) | (cSize << 3);
            data[cursor] = (byte) (stdHeader & 0xFF);
            data[cursor + 1] = (byte) ((stdHeader >> 8) & 0xFF);
            data[cursor + 2] = (byte) ((stdHeader >> 16) & 0xFF);
            int blockDataSize = (blockTypeStd == 1) ? 1 : cSize;
            cursor += 3 + blockDataSize;
            if (lastBlock == 1) break;
        }
    }

    private static void obfuscateZstdBlocks(byte[] data) {
        if (data.length < 5 || LegacyHash.littleInt(data, 0) != 0xFD2FB528) {
            throw new IllegalArgumentException("Not a standard ZSTD frame.");
        }
        int cursor = 4 + zstdFrameHeaderSize(data[4]);
        while (cursor + 3 <= data.length) {
            int b0 = data[cursor] & 0xFF;
            int b1 = data[cursor + 1] & 0xFF;
            int b2 = data[cursor + 2] & 0xFF;
            int hdr = b0 | (b1 << 8) | (b2 << 16);
            int lastBlock = hdr & 1;
            int blockTypeStd = (hdr >> 1) & 3;
            int cSize = hdr >> 3;
            int blockDataSize = (blockTypeStd == 1) ? 1 : cSize;
            int blockTypeYsm = switch (blockTypeStd) {
                case 0 -> 3;
                case 1 -> 1;
                case 2 -> 0;
                case 3 -> 2;
                default -> throw new IllegalStateException("Unknown block type");
            };
            int rawSize = cSize ^ 0xD4E9;
            data[cursor] = (byte) ((lastBlock << 7) | (blockTypeYsm << 5) | ((rawSize >> 16) & 0x1F));
            data[cursor + 1] = (byte) (rawSize & 0xFF);
            data[cursor + 2] = (byte) ((rawSize >> 8) & 0xFF);
            cursor += 3 + blockDataSize;
            if (lastBlock == 1) break;
        }
    }

    private static int zstdFrameHeaderSize(byte fhd) {
        boolean singleSegment = ((fhd >> 5) & 1) == 1;
        int dictIdSize = switch (fhd & 3) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            default -> 0;
        };
        int fcsSize = switch ((fhd >> 6) & 3) {
            case 0 -> singleSegment ? 1 : 0;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> 0;
        };
        return 1 + (singleSegment ? 0 : 1) + dictIdSize + fcsSize;
    }
}
