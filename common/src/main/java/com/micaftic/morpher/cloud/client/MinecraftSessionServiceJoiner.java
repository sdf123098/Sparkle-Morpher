package com.micaftic.morpher.cloud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.Services;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.google.gson.JsonObject;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Uses the Session Service configured by the active Minecraft launcher. The
 * access token stays in-process and is never sent to an SPM Cloud instance.
 */
public final class MinecraftSessionServiceJoiner implements CloudIdentityClient.SessionJoiner {
    public static IdentityProfile currentProfile() {
        var user = Minecraft.getInstance().getUser();
        return new IdentityProfile(user.getProfileId(), user.getName());
    }

    @Override
    public CompletableFuture<JsonObject> prove(CloudIdentityClient.CloudIdentityChallenge challenge,
                                                CloudInstanceConfig instance, String purpose) {
        if (!CloudGameIdentityProof.supports(challenge, instance)) {
            return CloudIdentityClient.SessionJoiner.super.prove(challenge, instance, purpose);
        }
        return Minecraft.getInstance().getProfileKeyPairManager().prepareKeyPair()
                .thenApply(pair -> pair)
                .completeOnTimeout(Optional.empty(), 10, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(failure -> Optional.empty()).thenCompose(optional -> {
                    if (optional.isEmpty() || optional.get().publicKey().data().hasExpired()) {
                        return CloudIdentityClient.SessionJoiner.super.prove(challenge, instance, purpose);
                    }
                    return CompletableFuture.supplyAsync(() -> {
                        var pair = optional.get();
                        var data = pair.publicKey().data();
                        try {
                            return CloudGameIdentityProof.sign(challenge, instance, purpose, currentProfile().profileId(),
                                    pair.privateKey(), data.key(), data.expiresAt().toEpochMilli(), data.keySignature());
                        } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
                            throw new java.util.concurrent.CompletionException(failure);
                        }
                    });
                });
    }

    @Override
    public CompletableFuture<Void> join(CloudIdentityClient.CloudIdentityChallenge challenge) {
        return CompletableFuture.runAsync(() -> {
            try {
                Object client = Minecraft.getInstance();
                var user = Minecraft.getInstance().getUser();
                sessionService(client).joinServer(user.getProfileId(), user.getAccessToken(), challenge.serverId());
            } catch (ReflectiveOperationException | com.mojang.authlib.exceptions.AuthenticationException failure) {
                Throwable cause = failure instanceof InvocationTargetException invocation
                        && invocation.getCause() != null ? invocation.getCause() : failure;
                CloudHttpException rejected = new CloudHttpException(502, CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                        "Minecraft Session Service rejected the Cloud identity challenge");
                rejected.initCause(cause);
                throw new java.util.concurrent.CompletionException(rejected);
            }
        });
    }

    static MinecraftSessionService sessionService(Object client) throws ReflectiveOperationException {
        // Method names are obfuscated on older Fabric versions; return types survive remapping.
        for (Method method : client.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == MinecraftSessionService.class) {
                return (MinecraftSessionService) method.invoke(client);
            }
        }
        for (Method method : client.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == Services.class) {
                return ((Services) method.invoke(client)).sessionService();
            }
        }
        throw new NoSuchMethodException("Minecraft Session Service accessor");
    }

    public record IdentityProfile(UUID profileId, String name) {
        public IdentityProfile {
            if (profileId == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("invalid active Minecraft profile");
            }
        }
    }
}
