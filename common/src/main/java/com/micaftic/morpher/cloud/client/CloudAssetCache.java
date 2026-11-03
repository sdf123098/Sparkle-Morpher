package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies Cloud display bytes against the trusted exact revision.
 *
 * <p>Hashing here stays pure: this class never chooses a storage location. Only
 * {@link CloudDiskCache} may write display bytes, and it re-derives the same
 * digest as the file name so a cached blob is self-verifying.</p>
 */
public final class CloudAssetCache {

    static String verify(CloudAssetRef ref, byte[] bytes) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(bytes, "bytes");
        String actualSha = sha256(bytes);
        if (!actualSha.equals(ref.rawSha256())) {
            throw new CloudHttpException(200, CloudErrorCode.ASSET_HASH_MISMATCH, "Cloud asset SHA-256 does not match its trusted reference");
        }
        return actualSha;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
