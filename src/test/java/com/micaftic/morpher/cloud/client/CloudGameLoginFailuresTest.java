package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudGameLoginFailuresTest {
    @Test void aVerifiedUnlinkedPlayerIsNotBlockedByAnUnrelatedProviderOutage() {
        for (boolean outageFirst : new boolean[]{true, false}) {
            var failures = new CloudGameLoginFailures();
            var outage = new CloudHttpException(502, CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, "outage");
            var unlinked = new CloudHttpException(404, CloudErrorCode.IDENTITY_NOT_LINKED, "verified but unlinked");
            failures.record(outageFirst ? outage : unlinked);
            failures.record(outageFirst ? unlinked : outage);
            assertNull(failures.unavailable());
        }
    }

    @Test void mismatchingProfilesCannotMaskAnActualOutage() {
        var failures = new CloudGameLoginFailures();
        var outage = new CloudHttpException(502, CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, "outage");
        failures.record(outage);
        failures.record(new CloudHttpException(403, CloudErrorCode.IDENTITY_PROFILE_MISMATCH, "wrong issuer"));
        assertSame(outage, failures.unavailable());
    }
}
