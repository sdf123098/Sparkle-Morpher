package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void runtimeModelIdIsSafeAndSeparatesCloudRevisionsFromLocalIds() {
        CloudAssetIdentity first = new CloudAssetIdentity("official", "catalog", "hero", "1", HASH);
        CloudAssetIdentity otherInstance = new CloudAssetIdentity("community", "catalog", "hero", "1", HASH);
        CloudAssetIdentity otherRevision = new CloudAssetIdentity("official", "catalog", "hero", "2", HASH);

        String runtimeId = first.runtimeModelId();
        assertEquals(runtimeId, first.runtimeModelId());
        assertTrue(runtimeId.matches("cloud_[0-9a-f]{64}"));
        assertTrue(CloudAssetIdentity.isRuntimeModelId(runtimeId));
        assertNotEquals("hero", runtimeId);
        assertNotEquals(runtimeId, otherInstance.runtimeModelId());
        assertNotEquals(runtimeId, otherRevision.runtimeModelId());
        assertFalse(CloudAssetIdentity.isRuntimeModelId("hero"));
        assertFalse(CloudAssetIdentity.isRuntimeModelId("cloud_not-a-hash"));
    }
}
