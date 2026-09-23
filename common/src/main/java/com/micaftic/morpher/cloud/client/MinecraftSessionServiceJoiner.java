package com.micaftic.morpher.cloud.client;

import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Uses the Session Service configured by the active Minecraft launcher. The
 * access token stays in-process and is never sent to an SPM Cloud instance.
 */
public final class MinecraftSessionServiceJoiner implements CloudIdentityClient.SessionJoiner {
    public static IdentityProfile currentProfile() {
        try {
            Object user = invoke(Minecraft.getInstance(), "getUser");
            UUID id = profileId(user);
            String name = (String) invoke(user, "getName");
            return new IdentityProfile(id, name);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to read the active Minecraft profile", failure);
        }
    }

    @Override
    public CompletableFuture<Void> join(CloudIdentityClient.CloudIdentityChallenge challenge) {
        return CompletableFuture.runAsync(() -> {
            try {
                Object client = Minecraft.getInstance();
                Object user = invoke(client, "getUser");
                UUID profileId = profileId(user);
                String accessToken = (String) invoke(user, "getAccessToken");
                Object sessionService = sessionService(client);
                invoke(sessionService, "joinServer", profileId, accessToken, challenge.serverId());
            } catch (ReflectiveOperationException failure) {
                Throwable cause = failure instanceof InvocationTargetException invocation
                        && invocation.getCause() != null ? invocation.getCause() : failure;
                throw new java.util.concurrent.CompletionException(
                        new IllegalStateException("Minecraft Session Service rejected the Cloud identity challenge", cause));
            }
        });
    }

    private static Object sessionService(Object client) throws ReflectiveOperationException {
        try {
            return invoke(client, "getMinecraftSessionService");
        } catch (NoSuchMethodException olderApiAbsent) {
            Object services = invoke(client, "services");
            return invoke(services, "sessionService");
        }
    }

    private static UUID profileId(Object user) throws ReflectiveOperationException {
        try {
            return (UUID) invoke(user, "getProfileId");
        } catch (NoSuchMethodException olderApiAbsent) {
            Object profile = invoke(user, "getGameProfile");
            return (UUID) invoke(profile, "getId");
        }
    }

    private static Object invoke(Object receiver, String methodName, Object... args) throws ReflectiveOperationException {
        for (Method method : receiver.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) return method.invoke(receiver, args);
        }
        throw new NoSuchMethodException(receiver.getClass().getName() + "." + methodName);
    }

    public record IdentityProfile(UUID profileId, String name) {
        public IdentityProfile {
            if (profileId == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("invalid active Minecraft profile");
            }
        }
    }
}
