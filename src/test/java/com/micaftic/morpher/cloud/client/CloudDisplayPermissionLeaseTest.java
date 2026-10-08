package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudDisplayPermissionLeaseTest {
    @Test void residentBytesDoNotAuthorizeAndOnlyOneCheckCanRun() {
        var lease = new CloudDisplayPermissionLease();
        assertFalse(lease.valid(0));
        assertTrue(lease.start(-100));
        assertFalse(lease.start(0));
        lease.complete(true, 0);
        assertTrue(lease.valid(29_999_999_999L));
        assertFalse(lease.valid(30_000_000_000L));
        assertFalse(lease.start(14_999_999_999L));
        assertTrue(lease.start(15_000_000_000L));
    }

    @Test void revocationInvalidatesImmediatelyAndOfflineRefreshCannotExtendTtl() {
        var lease = new CloudDisplayPermissionLease();
        lease.start(0);
        lease.complete(true, 0);
        assertTrue(lease.start(15_000_000_000L));
        assertFalse(lease.valid(30_000_000_000L), "hung refresh must not keep a resident model alive");
        lease.complete(false, 31_000_000_000L);
        assertFalse(lease.valid(31_000_000_000L));
        assertFalse(lease.start(35_999_999_999L));
        assertTrue(lease.start(36_000_000_000L));
        lease.complete(true, 36_000_000_000L);
        assertTrue(lease.valid(36_000_000_001L));
    }

    @Test void monotonicTimerWrapDoesNotKeepAnExpiredLease() {
        var lease = new CloudDisplayPermissionLease();
        long now = Long.MAX_VALUE - 10_000_000_000L;
        assertTrue(lease.start(now));
        lease.complete(true, now);
        assertTrue(lease.valid(now + 29_000_000_000L));
        assertFalse(lease.valid(now + 30_000_000_000L));
    }
}
