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
        return CompletableFuture.supplyAsync(() -> {
            try {
                var service = MinecraftSessionServiceJoiner.sessionService(client);
                // Secure fetch bypasses Authlib's six-hour insecure profile cache.
                var result = service.fetchProfile(profileId, true);
                var property = result == null ? null : service.getPackedTextures(result.profile());
                if (property == null || !property.hasSignature() || property.value().length() > 16384
                        || property.signature().length() > 2048) {
                    throw new IllegalStateException("无法获取 Mojang 签名的角色资料，请检查角色服务连接后重试");
                }
                JsonObject proof = new JsonObject();
                proof.addProperty("value", property.value()); proof.addProperty("signature", property.signature());
                return proof;
            } catch (ReflectiveOperationException error) {
                throw new CompletionException(error);
            }
        });
    }
}
