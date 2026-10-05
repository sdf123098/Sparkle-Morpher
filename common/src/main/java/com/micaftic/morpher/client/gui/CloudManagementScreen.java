package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudConnectionController;
import com.micaftic.morpher.cloud.client.CloudAuthClient;
import com.micaftic.morpher.cloud.client.CloudGeneratedAccountStore;
import com.micaftic.morpher.cloud.client.CloudHttpException;
import com.micaftic.morpher.cloud.client.CloudIdentityClient;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.CloudSession;
import com.micaftic.morpher.cloud.client.CloudHttpClient;
import com.micaftic.morpher.cloud.client.MinecraftSessionServiceJoiner;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Cloud connection facade used by the model panel account tab. */
public final class CloudManagementScreen {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SparkleMorpher/Cloud");
    private static final String CLIENT_VERSION = "2.0.0";
    private static CloudManagementController management;
    private static long nextRealtimeReconnectMillis;
    private static boolean autoResumeAttempted;

    public static void open(Screen parent) {
        try {
            Class.forName("com.micaftic.morpher.client.gui.ModernPlayerModelScreen")
                    .getMethod("openCloudManagement", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to open Cloud account management in the model panel", failure);
        }
    }

    public static void tickConnections() {
        CloudManagementController current = management;
        if (current == null) return;
        current.tickConnections();
        long now = System.currentTimeMillis();
        if (now >= nextRealtimeReconnectMillis) {
            nextRealtimeReconnectMillis = now + 10_000L;
            CloudClientRuntime.reconnectRealtime();
        }
    }

    static synchronized CloudManagementController management() {
        if (management == null) {
            Path config = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("sparkle-morpher").resolve("cloud-instances.json");
            CloudInstanceRegistry registry = new CloudInstanceRegistry(config);
            management = new CloudManagementController(registry, new CloudConnectionController(),
                    CloudClientRuntime.defaultCacheRoot(), CLIENT_VERSION);
            try {
                management.loadInstances();
                resumeOfficialAccount();
            } catch (IOException failure) {
                throw new IllegalStateException("Failed to load the SPM Cloud instance registry", failure);
            }
        }
        return management;
    }

    /** Restores only the selected instance; automatic recovery never creates an account. */
    static void resumeOfficialAccount() {
        if (autoResumeAttempted || management == null || management.registry().selected().isEmpty()) return;
        autoResumeAttempted = true;
        if (CloudClientRuntime.state(management.registry().selected().orElseThrow().instanceId()) == null)
            connectOfficialAccount(false).exceptionally(failure -> null);
    }

    record AccountContext(CloudManagementController controller,
                          CloudInstanceRegistry.CloudInstanceProfile selected, long generation) {
        void check() {
            if (controller.accountGeneration() != generation
                    || !controller.registry().selected().map(selected::equals).orElse(false))
                throw new java.util.concurrent.CancellationException("Cloud instance selection changed");
        }
        CloudClientRuntime.RuntimeState runtime() {
            check();
            var runtime = CloudClientRuntime.state(selected.instanceId());
            if (runtime == null || !runtime.instance().equals(selected.instance()))
                throw new java.util.concurrent.CancellationException("Cloud account changed");
            return runtime;
        }
        void checkSession(CloudSession session) {
            if (!runtime().session().equals(session)) throw new java.util.concurrent.CancellationException("Cloud login session changed");
        }
        void check(CloudClientRuntime.RuntimeState runtime) {
            if (runtime() != runtime) throw new java.util.concurrent.CancellationException("Cloud account changed");
        }
    }

    static AccountContext accountContext() {
        var controller = management();
        synchronized (controller) {
            return new AccountContext(controller, controller.registry().selected().orElseThrow(), controller.accountGeneration());
        }
    }

    static CompletableFuture<CloudSession> connectOfficialAccount() { return connectOfficialAccount(true); }

    private static CompletableFuture<CloudSession> connectOfficialAccount(boolean createIfMissing) {
        try {
            var context = accountContext();
            if (CloudClientRuntime.state(context.selected().instanceId()) != null)
                return CompletableFuture.failedFuture(new IllegalStateException(text("connected")));
            return loginWithLinkedGameAccount(context).exceptionallyCompose(failure -> {
                context.check();
                Throwable cause = unwrap(failure);
                if (cause instanceof CloudHttpException http && http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
                    return loginSavedOrCreate(context, false).exceptionallyCompose(savedFailure -> CompletableFuture.failedFuture(
                            unwrap(savedFailure) instanceof NoLinkedGameAccountException ? cause : unwrap(savedFailure)));
                if (!(cause instanceof NoLinkedGameAccountException)) return CompletableFuture.failedFuture(cause);
                return loginSavedOrCreate(context, createIfMissing);
            }).thenApply(session -> { context.checkSession(session); return session; });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static CompletableFuture<CloudSession> loginSavedOrCreate(AccountContext context, boolean createIfMissing) {
        try {
            context.check();
            CloudGeneratedAccountStore store = new CloudGeneratedAccountStore(generatedAccountFile(context.selected()));
            var saved = store.load();
            if (saved.isPresent()) {
                var account = saved.get();
                synchronized (context.controller()) {
                    context.check();
                    return context.controller().login(account.accountId(), account.password()).exceptionallyCompose(failure -> {
                        context.check();
                        var cause = unwrap(failure);
                        if (createIfMissing && cause instanceof CloudHttpException http && (http.statusCode() == 401 || http.statusCode() == 404))
                            return registerAccount(context, account.accountId(), account.password());
                        return CompletableFuture.failedFuture(cause);
                    }).thenCompose(session -> {
                        context.checkSession(session);
                        return createIfMissing ? bindCurrentGameAccount(context).thenApply(identity -> session)
                                : CompletableFuture.completedFuture(session);
                    });
                }
            }
            if (!createIfMissing) return CompletableFuture.failedFuture(new NoLinkedGameAccountException());
            return instanceInfo(context).thenCompose(info -> {
                context.check(); requireRegistration(info);
                var account = CloudGeneratedAccountStore.generate();
                try { store.save(account); } catch (IOException failure) { return CompletableFuture.failedFuture(failure); }
                return registerAccount(context, account.accountId(), account.password())
                        .thenCompose(session -> { context.checkSession(session); return bindCurrentGameAccount(context).thenApply(identity -> session); });
            });
        } catch (IOException | RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static CompletableFuture<com.micaftic.morpher.cloud.client.CloudInstanceInfo> instanceInfo(AccountContext context) {
        context.check();
        return new CloudHttpClient(context.selected().instance()).discoverInstance().thenApply(info -> { context.check(); return info; });
    }

    private static void requireRegistration(com.micaftic.morpher.cloud.client.CloudInstanceInfo info) {
        if (info.auth() != null && !info.auth().selfRegistration()) throw new IllegalStateException(text("registration_disabled"));
    }

    private static CompletableFuture<CloudSession> registerAccount(AccountContext context, String account, String password) {
        return instanceInfo(context).thenCompose(info -> {
            requireRegistration(info);
            synchronized (context.controller()) { context.check(); return context.controller().register(account, password).thenApply(session -> { context.checkSession(session); return session; }); }
        });
    }

    static CompletableFuture<CloudSession> submitAccount(boolean register, String account, String password) {
        try {
            var context = accountContext();
            if (register) return registerAccount(context, account, password).thenApply(session -> { context.checkSession(session); return session; });
            synchronized (context.controller()) {
                context.check(); return context.controller().login(account, password).thenApply(session -> { context.checkSession(session); return session; });
            }
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static CompletableFuture<CloudSession> loginWithLinkedGameAccount(AccountContext context) {
        var auth = new CloudAuthClient(new CloudHttpClient(context.selected().instance()));
        return auth.gameIdentityProviders().handle((providers, failure) -> {
            context.check();
            if (failure == null) return CompletableFuture.completedFuture(providers);
            Throwable cause = unwrap(failure);
            if (cause instanceof CloudHttpException http && (http.statusCode() == 401 || http.statusCode() == 404))
                return CompletableFuture.completedFuture(List.<CloudIdentityClient.IdentityProvider>of());
            return CompletableFuture.<List<CloudIdentityClient.IdentityProvider>>failedFuture(cause);
        }).thenCompose(next -> next).thenCompose(providers -> {
            context.check();
            if (providers.isEmpty()) return CompletableFuture.failedFuture(new NoLinkedGameAccountException());
            return tryLinkedProvider(context, providers, 0, MinecraftSessionServiceJoiner.currentProfile(), null);
        });
    }

    private static CompletableFuture<CloudSession> tryLinkedProvider(AccountContext context,
            List<CloudIdentityClient.IdentityProvider> providers, int index,
            MinecraftSessionServiceJoiner.IdentityProfile profile, Throwable unavailable) {
        context.check();
        if (index >= providers.size()) return CompletableFuture.failedFuture(unavailable != null ? unavailable : new NoLinkedGameAccountException());
        CompletableFuture<CloudSession> request;
        synchronized (context.controller()) {
            context.check();
            request = context.controller().loginWithGameIdentity(providers.get(index).providerId(), profile, guardedJoiner(context, null));
        }
        return request.thenApply(session -> { context.checkSession(session); return session; }).exceptionallyCompose(failure -> {
            context.check(); Throwable cause = unwrap(failure);
            if (cause instanceof CloudHttpException http) {
                if (http.errorCode() == CloudErrorCode.IDENTITY_NOT_LINKED || http.errorCode() == CloudErrorCode.IDENTITY_PROFILE_MISMATCH)
                    return tryLinkedProvider(context, providers, index + 1, profile, unavailable);
                if (http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
                    return tryLinkedProvider(context, providers, index + 1, profile, unavailable != null ? unavailable : cause);
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    static CompletableFuture<CloudIdentityClient.CloudIdentity> bindCurrentGameAccount() {
        return bindCurrentGameAccount(accountContext());
    }

    private static CompletableFuture<CloudIdentityClient.CloudIdentity> bindCurrentGameAccount(AccountContext context) {
        try {
            var runtime = context.runtime();
            var profile = MinecraftSessionServiceJoiner.currentProfile();
            return runtime.identities().listProviders().thenCompose(providers -> tryBindingProvider(context, runtime, providers, 0, profile, null))
                    .thenApply(identity -> { context.check(runtime); com.micaftic.morpher.cloud.client.CloudPlayerModelSync.requestIdentityRefresh(runtime); return identity; });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    static CompletableFuture<CloudIdentityClient.CloudIdentity> bindProvider(String providerId) {
        try {
            var context = accountContext(); var runtime = context.runtime(); var profile = MinecraftSessionServiceJoiner.currentProfile();
            return runtime.identities().listProviders().thenCompose(providers -> {
                context.check(runtime);
                var enabled = providers.stream().filter(provider -> provider.providerId().equals(providerId)).toList();
                return tryBindingProvider(context, runtime, enabled, 0, profile, null);
            }).thenApply(identity -> { context.check(runtime); com.micaftic.morpher.cloud.client.CloudPlayerModelSync.requestIdentityRefresh(runtime); return identity; });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static CloudIdentityClient.SessionJoiner guardedJoiner(AccountContext context, CloudClientRuntime.RuntimeState runtime) {
        var adapter = new MinecraftSessionServiceJoiner();
        return new CloudIdentityClient.SessionJoiner() {
            private void check() { if (runtime == null) context.check(); else context.check(runtime); }
            public CompletableFuture<Void> join(CloudIdentityClient.CloudIdentityChallenge challenge) {
                check(); return adapter.join(challenge).thenApply(ignored -> { check(); return null; });
            }
            public CompletableFuture<com.google.gson.JsonObject> prove(CloudIdentityClient.CloudIdentityChallenge challenge,
                    com.micaftic.morpher.cloud.CloudInstanceConfig instance, String purpose) {
                check(); return adapter.prove(challenge, instance, purpose).thenApply(proof -> { check(); return proof; });
            }
        };
    }

    private static CompletableFuture<CloudIdentityClient.CloudIdentity> tryBindingProvider(AccountContext context,
            CloudClientRuntime.RuntimeState runtime, List<CloudIdentityClient.IdentityProvider> providers, int index,
            MinecraftSessionServiceJoiner.IdentityProfile profile, Throwable unavailable) {
        context.check(runtime);
        if (index >= providers.size()) return CompletableFuture.failedFuture(unavailable != null ? unavailable : new IllegalStateException(text("identity.no_matching_provider")));
        var client = runtime.identities();
        return client.createChallenge(providers.get(index).providerId(), profile.name(), profile.profileId().toString())
                .thenCompose(challenge -> { context.check(runtime); return client.joinAndComplete(challenge, guardedJoiner(context, runtime)); })
                .exceptionallyCompose(failure -> {
                    context.check(runtime); Throwable cause = unwrap(failure);
                    if (cause instanceof CloudHttpException http && http.errorCode() == CloudErrorCode.IDENTITY_PROFILE_MISMATCH)
                        return tryBindingProvider(context, runtime, providers, index + 1, profile, unavailable);
                    if (cause instanceof CloudHttpException http && http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
                        return tryBindingProvider(context, runtime, providers, index + 1, profile, unavailable != null ? unavailable : cause);
                    return CompletableFuture.failedFuture(cause);
                });
    }

    static CompletableFuture<Boolean> currentIdentityBound(AccountContext context) {
        try {
            var runtime = context.runtime(); var profile = MinecraftSessionServiceJoiner.currentProfile();
            return runtime.identities().listIdentities().thenApply(identities -> {
                context.check(runtime);
                return identities.stream().filter(identity -> identity.verificationStatus().equals("VERIFIED")
                        && identity.identityRef().profileUuid().equals(profile.profileId())).count() == 1;
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static final class NoLinkedGameAccountException extends RuntimeException {
        private NoLinkedGameAccountException() { super("No game identity is linked to a Cloud account"); }
    }

    public static void maskPassword(EditBox field) {
        java.util.function.BiFunction<String, Integer, net.minecraft.util.FormattedCharSequence> formatter =
                (value, offset) -> net.minecraft.util.FormattedCharSequence.forward(
                        "*".repeat(value.length()), net.minecraft.network.chat.Style.EMPTY);
        // 1.21 uses BiFunction; 26.x uses TextFormatter. Discover types so older Fabric names can be remapped.
        for (var setter : EditBox.class.getMethods()) {
            if (setter.getParameterCount() != 1 || setter.getReturnType() != void.class) continue;
            Class<?> callback = setter.getParameterTypes()[0];
            Object adapter = null;
            if (callback == java.util.function.BiFunction.class) adapter = formatter;
            else if (callback.isInterface() && java.util.Arrays.stream(callback.getMethods()).anyMatch(method ->
                    method.getReturnType() == net.minecraft.util.FormattedCharSequence.class
                            && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class, int.class}))) {
                adapter = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "toString" -> "SPM password formatter";
                                default -> throw new UnsupportedOperationException();
                            };
                            return formatter.apply((String) args[0], (Integer) args[1]);
                        });
            }
            if (adapter == null) continue;
            try { setter.invoke(field, adapter); return; }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unable to mask Cloud password input", failure); }
        }
        throw new IllegalStateException("Minecraft password formatter is unavailable");
    }

    private static Path generatedAccountFile(CloudInstanceRegistry.CloudInstanceProfile profile) {
        if (!CloudInstanceRegistry.isBuiltinOfficial(profile)) {
            String key = profile.instanceId() + "\n" + profile.instance().origin();
            try {
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("sparkle-morpher")
                        .resolve("cloud-generated-accounts").resolve(hash + ".json");
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("sparkle-morpher").resolve("cloud-generated-account.json");
    }

    static String text(String key, Object... args) {
        return Component.translatable("gui.sparkle_morpher.cloud.manage." + key, args).getString();
    }

    static String displayName(CloudInstanceRegistry.CloudInstanceProfile profile) {
        return CloudInstanceRegistry.isBuiltinOfficial(profile) && profile.name().equals("Official Cloud")
                ? text("official_name") : profile.name();
    }

    static String errorText(Throwable failure) {
        LOGGER.warn("SPM Cloud operation failed", failure);
        Throwable cause = unwrap(failure);
        String key = errorKey(cause);
        if (key != null) return text(key);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    static String errorKey(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CloudHttpException http) {
            if (http.errorCode() == CloudErrorCode.ACCOUNT_EXISTS) return "registration_account_exists";
            if (http.errorCode() == CloudErrorCode.IDENTITY_ALREADY_LINKED) return "identity.already_linked";
            if (http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE) return "identity.provider_unavailable";
        }
        if (cause instanceof java.net.ConnectException
                || cause instanceof java.nio.channels.ClosedChannelException) {
            return "error.connection";
        }
        return null;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        // Keep transport and Cloud error types: their nested causes are diagnostic details.
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause;
    }

}
