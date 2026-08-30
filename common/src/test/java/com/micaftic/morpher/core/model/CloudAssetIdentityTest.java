package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudAssetIdentityTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void cacheKeyContainsImmutableCloudIdentity() {
        CloudAssetIdentity identity = new CloudAssetIdentity("spm-prod", "tenant-a", "asset-7", "rev-3", HASH);
        assertEquals("spm-prod/tenant-a/asset-7/rev-3/" + HASH, identity.cacheKey());
        assertEquals("cloud:spm-prod/tenant-a:asset-7@rev-3#" + HASH, identity.modelRef().toString());
    }

    @Test
    void normalizesHashAndRejectsUnsafeIdentityParts() {
        CloudAssetIdentity identity = new CloudAssetIdentity("instance", "tenant", "asset", "revision", HASH.toUpperCase());
        assertEquals(HASH, identity.contentHash());
        assertThrows(IllegalArgumentException.class,
                () -> new CloudAssetIdentity("instance/path", "tenant", "asset", "revision", HASH));
        assertThrows(IllegalArgumentException.class,
                () -> new CloudAssetIdentity("instance", "tenant", "asset", "revision", "not-a-sha256"));
    }
}
