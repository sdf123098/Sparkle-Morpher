package com.micaftic.morpher.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DigestUtil {
    private static final ThreadLocal<MessageDigest> MD5_TL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    });

    private static final ThreadLocal<MessageDigest> SHA256_TL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    });

    public static MessageDigest md5Digest() {
        MessageDigest md = MD5_TL.get();
        md.reset();
        return md;
    }

    public static MessageDigest sha256Digest() {
        MessageDigest md = SHA256_TL.get();
        md.reset();
        return md;
    }

    public static byte[] md5(byte[] input) {
        MessageDigest md = MD5_TL.get();
        md.reset();
        return md.digest(input);
    }

    public static byte[] sha256(byte[] input) {
        MessageDigest md = SHA256_TL.get();
        md.reset();
        return md.digest(input);
    }

    public static String md5Hex(byte[] input) {
        return HexFormat.of().formatHex(md5(input));
    }

    public static String sha256Hex(byte[] input) {
        return HexFormat.of().formatHex(sha256(input));
    }

    public static String sha256Hex(Path input) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream stream = Files.newInputStream(input)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
