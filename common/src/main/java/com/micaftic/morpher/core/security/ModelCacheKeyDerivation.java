package com.micaftic.morpher.core.security;

import com.micaftic.morpher.core.algorithms.CityHash;
import com.micaftic.morpher.core.algorithms.MT19937;
import java.nio.charset.StandardCharsets;

/** Original cache derivation, with an explicit retained identity for legacy reads. */
public final class ModelCacheKeyDerivation {
    private ModelCacheKeyDerivation() {}
    public static long[] hashes(String sha256, byte[] key, String identity) {
        byte[] data = (identity + "\nmodel=" + sha256).getBytes(StandardCharsets.UTF_8);
        CityHash city = new CityHash();
        MT19937 random = new MT19937(city.hash64WithSeed(key, YsmCrypt.SEED_KEY_DERIVATION));
        for (int i = 0; i < data.length;) {
            long value = random.extract_number();
            for (int j = 0; j < 8 && i < data.length; j++, i++) {
                data[i] ^= (byte) (value >>> (j * 8));
            }
        }
        return new long[] {city.hash64WithSeed(data, YsmCrypt.SEED_CACHE_VERIFICATION),
                city.hash64WithSeed(data, YsmCrypt.SEED_CACHE_DECRYPTION)};
    }
}
