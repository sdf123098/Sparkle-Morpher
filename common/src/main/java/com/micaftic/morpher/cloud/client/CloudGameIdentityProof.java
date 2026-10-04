package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.micaftic.morpher.cloud.CloudInstanceConfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

/** Signs a domain-bound challenge from the selected Cloud. No game token or private key is serialized. */
final class CloudGameIdentityProof {
    static boolean supports(CloudIdentityClient.CloudIdentityChallenge challenge, CloudInstanceConfig instance) {
        return "official".equals(challenge.providerId())
                && challenge.profileKeyPayload().startsWith("SPM-CLOUD-GAME-IDENTITY-V1\n" + instance.origin() + "\n");
    }

    static JsonObject sign(CloudIdentityClient.CloudIdentityChallenge challenge, CloudInstanceConfig instance,
                           String purpose, UUID profileId, PrivateKey privateKey, PublicKey publicKey,
                           long expiresAt, byte[] certificateSignature) throws GeneralSecurityException {
        if (!supports(challenge, instance)) throw new IllegalArgumentException("Player key proof requires the selected Cloud origin");
        String[] fields = challenge.profileKeyPayload().split("\n", -1);
        if (fields.length != 8 || !("link".equals(purpose) || "login".equals(purpose))
                || ("login".equals(purpose) ? !fields[3].isEmpty() : !fields[3].matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
            throw new IllegalArgumentException("Invalid Cloud player key challenge");
        }
        String expected = String.join("\n", "SPM-CLOUD-GAME-IDENTITY-V1", instance.origin().toString(), purpose,
                fields[3], "official", profileId.toString(), challenge.challengeId(), challenge.serverId());
        if (!expected.equals(challenge.profileKeyPayload()) || expiresAt <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Cloud player key challenge does not match the active profile and operation");
        }
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(expected.getBytes(StandardCharsets.UTF_8));
        Base64.Encoder encoder = Base64.getEncoder();
        JsonObject key = new JsonObject();
        key.addProperty("public_key", encoder.encodeToString(publicKey.getEncoded()));
        key.addProperty("expires_at_ms", expiresAt);
        key.addProperty("key_signature", encoder.encodeToString(certificateSignature));
        key.addProperty("challenge_signature", encoder.encodeToString(signer.sign()));
        JsonObject result = new JsonObject();
        result.add("profile_key", key);
        return result;
    }

    private CloudGameIdentityProof() { }
}
