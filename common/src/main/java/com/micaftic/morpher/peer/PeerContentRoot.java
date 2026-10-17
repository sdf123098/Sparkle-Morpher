package com.micaftic.morpher.peer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Full content identity, deliberately unrelated to legacy or Cloud short fingerprints. */
public record PeerContentRoot(String sha256) {
    public PeerContentRoot {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid Peer SHA-256");
    }
    public static PeerContentRoot digest(byte[] bytes) {
        return new PeerContentRoot(HexFormat.of().formatHex(newDigest().digest(bytes)));
    }
    public static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public byte[] bytes() { return HexFormat.of().parseHex(sha256); }
    @Override public String toString() { return sha256; }
}
