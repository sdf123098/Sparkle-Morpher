package com.micaftic.morpher.core.security;

import dev.spm.core.codec.legacy.LegacyHash;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class YSMClientCache {
    private static final String CACHE_IDENTITY_FILE = ".ysm_cache_identity";

    public static String generateCacheFileName(long hash1, long hash2, byte[] rtKey) {
        if (rtKey == null || rtKey.length != 56) return null;
        int seed = 114514;

        LegacyStreamCipher.Random64 mt = new LegacyStreamCipher.Random64(Integer.toUnsignedLong(seed));
        long m1 = hash1 ^ mt.next();
        long m2 = hash2 ^ mt.next();

        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(seed);
        buf.putLong(m1);
        buf.putLong(m2);
        byte[] bufArray = buf.array();

        for (int i = 0; i < bufArray.length; i++) {
            bufArray[i] ^= rtKey[i % rtKey.length];
        }

        StringBuilder sb = new StringBuilder(40);
        for (byte b : bufArray) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public static boolean verifyFileContent(File cacheFile, long hash1, long hash2) {
        return verifyFileContent(cacheFile, hash1, hash2, null);
    }

    /** Verifies trailer checksum and, when {@code rtKey} is supplied, tests decryption+decompression. */
    public static boolean verifyFileContent(File cacheFile, long hash1, long hash2, byte[] rtKey) {
        if (cacheFile == null || !cacheFile.exists() || cacheFile.length() <= 8) {
            return false;
        }
        try {
            byte[] fileData = Files.readAllBytes(cacheFile.toPath());
            int payloadLen = fileData.length - 8;
            long realHash = ByteBuffer.wrap(fileData, payloadLen, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
            long calculatedHash = LegacyHash.seeded(fileData, 0, payloadLen, YsmCrypt.SEED_CACHE_VERIFICATION);
            long verif = calculatedHash ^ hash1 ^ hash2;
            if (verif != realHash) {
                return false;
            }
            if (rtKey != null) {
                YsmCrypt.read(fileData, rtKey);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static UUID getModelUUIDFromFileName(String fileName, byte[] rtKey) {
        if (fileName == null || fileName.length() != 40 || rtKey == null || rtKey.length != 56) {
            return null;
        }
        try {
            byte[] buf = new byte[20];
            for (int i = 0; i < 20; i++) {
                int high = Character.digit(fileName.charAt(i * 2), 16);
                int low = Character.digit(fileName.charAt(i * 2 + 1), 16);
                if (high == -1 || low == -1) return null;
                buf[i] = (byte) ((high << 4) | low);
            }
            for (int i = 0; i < buf.length; i++) {
                buf[i] ^= rtKey[i % rtKey.length];
            }
            ByteBuffer byteBuf = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            int seed = byteBuf.getInt();
            long m1 = byteBuf.getLong();
            long m2 = byteBuf.getLong();

            LegacyStreamCipher.Random64 mt = new LegacyStreamCipher.Random64(Integer.toUnsignedLong(seed));
            long hash1 = m1 ^ mt.next();
            long hash2 = m2 ^ mt.next();
            return new UUID(hash1, hash2);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<UUID, File> buildCacheIndex(File cacheDir, byte[] rtKey) {
        Map<UUID, File> cacheIndex = new HashMap<>();
        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            return cacheIndex;
        }
        File[] files = cacheDir.listFiles();
        if (files == null) return cacheIndex;
        for (File file : files) {
            if (file.isFile()) {
                UUID realModelUuid = getModelUUIDFromFileName(file.getName(), rtKey);
                if (realModelUuid != null) {
                    cacheIndex.put(realModelUuid, file);
                }
            }
        }
        return cacheIndex;
    }

    public static void prepareCacheDirectory(File cacheDir) {
        if (cacheDir == null || !cacheDir.isDirectory()) {
            return;
        }
        Path marker = cacheDir.toPath().resolve(CACHE_IDENTITY_FILE);
        String currentIdentity = YsmCrypt.getModelCacheIdentity();
        try {
            String existingIdentity = Files.exists(marker) ? Files.readString(marker, StandardCharsets.UTF_8) : "";
            if (!currentIdentity.equals(existingIdentity)) {
                deleteGeneratedFiles(cacheDir);
                Files.writeString(marker, currentIdentity, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteGeneratedFiles(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || CACHE_IDENTITY_FILE.equals(file.getName())) continue;
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    public static void cleanupCacheDirectory(File cacheDir, Set<UUID> expectedModels, byte[] rtKey) {
        if (cacheDir == null || expectedModels == null || rtKey == null || rtKey.length != 56 || !cacheDir.isDirectory()) {
            return;
        }
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || CACHE_IDENTITY_FILE.equals(file.getName())) continue;
            UUID realModelUuid = getModelUUIDFromFileName(file.getName(), rtKey);
            if (realModelUuid == null || !expectedModels.contains(realModelUuid)) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void deleteCacheFile(File cacheFile) {
        if (cacheFile == null || !cacheFile.isFile()) {
            return;
        }
        try {
            Files.deleteIfExists(cacheFile.toPath());
        } catch (Exception ignored) {
        }
    }
}
