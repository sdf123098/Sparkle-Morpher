package com.micaftic.morpher.core.security;

import dev.spm.core.codec.legacy.LegacyHash;

import java.nio.charset.StandardCharsets;

/** Original cache derivation, with an explicit retained identity for legacy reads. */
public final class ModelCacheKeyDerivation {
    private ModelCacheKeyDerivation() {}

    public static long[] hashes(String sha256, byte[] key, String identity) {
        byte[] data = (identity + "\nmodel=" + sha256).getBytes(StandardCharsets.UTF_8);
        LegacyStreamCipher.mt19937XorInPlace(data, 0, data.length, key, YsmCrypt.SEED_KEY_DERIVATION);
        return new long[]{
                LegacyHash.seeded(data, 0, data.length, YsmCrypt.SEED_CACHE_VERIFICATION),
                LegacyHash.seeded(data, 0, data.length, YsmCrypt.SEED_CACHE_DECRYPTION)
        };
    }
}
