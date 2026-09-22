package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudIdentityClientTest {
    @Test
    void parsesChallengeWithoutAcceptingProviderUrl() {
        var challenge = CloudIdentityClient.parseChallengeForTest("{\"challenge_id\":\"challenge-1\",\"provider_id\":\"official\",\"server_id\":\"server-1\",\"expires_in_seconds\":120}");
        assertEquals("official", challenge.providerId());
        assertEquals("server-1", challenge.serverId());
    }

    @Test
    void parsesVerifiedIdentity() {
        var identity = CloudIdentityClient.parseIdentityForTest("{\"identity_id\":\"identity-1\",\"account_id\":\"account\",\"identity\":\"official:12345678-1234-1234-1234-1234567890ab\",\"display_name\":\"Player\",\"verification_status\":\"VERIFIED\"}");
        assertEquals("VERIFIED", identity.verificationStatus());
        assertEquals("Player", identity.displayName());
    }

    @Test
    void rejectsProviderUrlAsProviderId() {
        assertThrows(IllegalArgumentException.class, () -> CloudScopeClient.segment("https://provider.example"));
    }
}
