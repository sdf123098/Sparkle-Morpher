package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Public Mojang-signed metadata; no game access token or private key leaves the client. */
final class CloudOfficialProfileNameProof {
    private CloudOfficialProfileNameProof() {}

    static CompletableFuture<JsonObject> fetch(UUID profileId) {
        Minecraft client = Minecraft.getInstance();
        return com.micaftic.morpher.core.network.NetworkAccess.cloud(() -> CompletableFuture.supplyAsync(() -> {
            try {
                var service = MinecraftSessionServiceJoiner.sessionService(client);
                // Secure fetch bypasses Authlib's six-hour insecure profile cache.
                var result = service.fetchProfile(profileId, true);
                var property = result == null ? null : service.getPackedTextures(result.profile());
                if (property == null || !property.hasSignature() || property.value().length() > 16384
                        || property.signature().length() > 2048) {
                    throw new IllegalStateException("sparkle_morpher:profile_proof_unavailable");
                }
                JsonObject proof = new JsonObject();
                proof.addProperty("value", property.value()); proof.addProperty("signature", property.signature());
                return proof;
            } catch (ReflectiveOperationException error) {
                throw new CompletionException(error);
            }
        }));
    }
}
