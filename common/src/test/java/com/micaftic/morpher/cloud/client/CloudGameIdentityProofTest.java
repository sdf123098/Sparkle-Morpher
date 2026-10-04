package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CloudGameIdentityProofTest {
    private final CloudInstanceConfig official = CloudInstanceConfig.v1("official",
            URI.create("https://spm-cloud-official.robinson171.workers.dev"));
    private final UUID uuid = UUID.randomUUID();

    private CloudIdentityClient.CloudIdentityChallenge challenge(String origin, String purpose, UUID profile) {
        String payload = String.join("\n", "SPM-CLOUD-GAME-IDENTITY-V1", origin, purpose,
                purpose.equals("link") ? "cloud_account" : "", "official", profile.toString(), "challenge_1", "nonce_1");
        return new CloudIdentityClient.CloudIdentityChallenge("challenge_1", "official", "nonce_1", 120, payload);
    }

    @Test
    void signsChallengeAndSendsOnlyPublicCertificate() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var challenge = challenge(official.origin().toString(), "login", uuid);
        var proof = CloudGameIdentityProof.sign(challenge, official, "login", uuid, pair.getPrivate(), pair.getPublic(),
                System.currentTimeMillis() + 60_000, new byte[]{1, 2, 3});
        var key = proof.getAsJsonObject("profile_key");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(challenge.profileKeyPayload().getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(key.get("challenge_signature").getAsString())));
        assertEquals(4, key.size());
        assertFalse(proof.toString().contains(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())));
    }

    @Test
    void signsSelectedSelfHostedCloudWithoutRelayingOtherOrigins() throws Exception {
        var selfHosted = CloudInstanceConfig.v1("community", URI.create("https://cloud.example.com"));
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var own = challenge(selfHosted.origin().toString(), "login", uuid);
        assertTrue(CloudGameIdentityProof.supports(own, selfHosted));
        assertNotNull(CloudGameIdentityProof.sign(own, selfHosted, "login", uuid, pair.getPrivate(), pair.getPublic(),
                System.currentTimeMillis() + 60000, new byte[]{1}));
        assertFalse(CloudGameIdentityProof.supports(challenge(official.origin().toString(), "login", uuid), selfHosted));
    }

    @Test
    void refusesRelayedCommunityChallengesAndDifferentOperationsOrProfiles() throws Exception {
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        long expires = System.currentTimeMillis() + 60_000;
        var valid = challenge(official.origin().toString(), "login", uuid);
        var community = CloudInstanceConfig.v1("official", URI.create("https://community.example.com"));
        assertFalse(CloudGameIdentityProof.supports(valid, community));
        assertThrows(IllegalArgumentException.class, () -> CloudGameIdentityProof.sign(valid, community, "login", uuid,
                pair.getPrivate(), pair.getPublic(), expires, new byte[]{1}));
        for (var invalid : new CloudIdentityClient.CloudIdentityChallenge[]{
                challenge("https://community.example.com", "login", uuid),
                challenge(official.origin().toString(), "link", uuid),
                challenge(official.origin().toString(), "login", UUID.randomUUID()),
        }) {
            assertThrows(IllegalArgumentException.class, () -> CloudGameIdentityProof.sign(invalid, official, "login", uuid,
                    pair.getPrivate(), pair.getPublic(), expires, new byte[]{1}));
        }
    }
}
