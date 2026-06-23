package com.micaftic.morpher.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
}
