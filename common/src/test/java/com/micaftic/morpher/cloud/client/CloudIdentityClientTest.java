package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudIdentityClientTest {
    @Test
    void parsesOnlyEnabledTrustedIdentityProviders() {
        var providers = CloudIdentityClient.parseProvidersForTest("""
                [
                  {"provider_id":"official","display_name":"Minecraft official","enabled":true},
                  {"provider_id":"community","display_name":"Community auth","enabled":true},
                  {"provider_id":"disabled","display_name":"Disabled provider","enabled":false}
                ]
                """);

        assertEquals(2, providers.size());
        assertEquals("official", providers.get(0).providerId());
        assertEquals("community", providers.get(1).providerId());
    }

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

    @Test
    void parsesIdentityCatalogWithoutMergingNamespaces() {
        var identities = CloudIdentityClient.parseIdentitiesForTest("""
                [
                  {"identity_id":"official-id","account_id":"account","identity":"official:12345678-1234-1234-1234-1234567890ab","display_name":"Official","verification_status":"VERIFIED"},
                  {"identity_id":"provider-id","account_id":"account","identity":"yggdrasil:community:12345678-1234-1234-1234-1234567890ab","display_name":"Community","verification_status":"VERIFIED"},
                  {"identity_id":"offline-id","account_id":"account","identity":"offline:scope-a:12345678-1234-1234-1234-1234567890ab","display_name":"Local","verification_status":"PENDING_VERIFICATION"}
                ]
                """);

        assertEquals(3, identities.size());
        assertEquals(com.micaftic.morpher.cloud.identity.CloudIdentityRef.Kind.OFFICIAL, identities.get(0).identityRef().kind());
        assertEquals(com.micaftic.morpher.cloud.identity.CloudIdentityRef.Kind.YGGDRASIL, identities.get(1).identityRef().kind());
        assertEquals("community", identities.get(1).identityRef().providerId());
        assertEquals(com.micaftic.morpher.cloud.identity.CloudIdentityRef.Kind.OFFLINE, identities.get(2).identityRef().kind());
        assertEquals("scope-a", identities.get(2).identityRef().scopeId());
    }

    @Test
    void offlineRegistrationSerializesScopeAsPartOfIdentityNamespace() {
        String body = CloudIdentityClient.offlineIdentityRequestForTest(
                "scope-a", UUID.fromString("12345678-1234-1234-1234-1234567890ab"), "Local");

        assertEquals("offline:scope-a:12345678-1234-1234-1234-1234567890ab",
                com.google.gson.JsonParser.parseString(body).getAsJsonObject().get("identity").getAsString());
    }
}
