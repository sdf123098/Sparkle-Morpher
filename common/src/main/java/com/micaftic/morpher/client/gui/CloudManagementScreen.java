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
    private static CompletableFuture<CloudSession> officialConnectInFlight;

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

    /** Restores a previously generated official account once per client session. */
    static void resumeOfficialAccount() {
        if (autoResumeAttempted || management == null) return;
        var selected = management.registry().selected();
        if (selected.isEmpty() || !CloudInstanceRegistry.isBuiltinOfficial(selected.get())) return;
        autoResumeAttempted = true;
        if (CloudClientRuntime.state(selected.get().instanceId()) != null) return;
        connectOfficialAccount(false).exceptionally(failure -> null);
    }

    static CompletableFuture<CloudSession> connectOfficialAccount() {
        return connectOfficialAccount(true);
    }

    private static synchronized CompletableFuture<CloudSession> connectOfficialAccount(boolean createIfMissing) {
        if (officialConnectInFlight != null) {
            if (!createIfMissing) return officialConnectInFlight;
            CompletableFuture<CloudSession> previous = officialConnectInFlight;
            CompletableFuture<CloudSession> upgraded = previous.exceptionallyCompose(failure -> {
                Throwable cause = unwrap(failure);
                if (!(cause instanceof NoLinkedGameAccountException)) return CompletableFuture.failedFuture(cause);
                return loginSavedOrCreate(true);
            });
            officialConnectInFlight = upgraded;
            upgraded.whenComplete((ignored, failure) -> {
                synchronized (CloudManagementScreen.class) {
                    if (officialConnectInFlight == upgraded) officialConnectInFlight = null;
                }
            });
            return upgraded;
        }
        try {
            var selected = management().registry().selected().orElseThrow();
            if (!CloudInstanceRegistry.isBuiltinOfficial(selected)) {
                return CompletableFuture.failedFuture(new IllegalStateException(text("official_only")));
            }
            if (CloudClientRuntime.state(selected.instanceId()) != null) {
                return CompletableFuture.failedFuture(new IllegalStateException(text("connected")));
            }
            CompletableFuture<CloudSession> request = loginWithLinkedGameAccount().exceptionallyCompose(failure -> {
                Throwable cause = unwrap(failure);
                if (cause instanceof CloudHttpException http && http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE) {
                    // Restore an existing local account during an outage; never create a duplicate on another device.
                    return loginSavedOrCreate(false).exceptionallyCompose(savedFailure -> CompletableFuture.failedFuture(
                            unwrap(savedFailure) instanceof NoLinkedGameAccountException ? cause : unwrap(savedFailure)));
                }
                if (!(cause instanceof NoLinkedGameAccountException)) return CompletableFuture.failedFuture(cause);
                return loginSavedOrCreate(createIfMissing);
            });
            officialConnectInFlight = request;
            request.whenComplete((ignored, failure) -> {
                synchronized (CloudManagementScreen.class) {
                    if (officialConnectInFlight == request) officialConnectInFlight = null;
                }
            });
            return request;
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static CompletableFuture<CloudSession> loginSavedOrCreate(boolean createIfMissing) {
        try {
            CloudGeneratedAccountStore store = new CloudGeneratedAccountStore(generatedAccountFile());
            var saved = store.load();
            if (saved.isPresent()) {
                var account = saved.get();
                return management().login(account.accountId(), account.password())
                        .exceptionallyCompose(loginFailure -> {
                            Throwable cause = unwrap(loginFailure);
                            if (cause instanceof CloudHttpException http
                                    && (http.statusCode() == 401 || http.statusCode() == 404)) {
                                return management().register(account.accountId(), account.password());
                            }
                            return CompletableFuture.failedFuture(cause);
                        });
            }
            if (!createIfMissing) return CompletableFuture.failedFuture(new NoLinkedGameAccountException());
            var account = CloudGeneratedAccountStore.generate();
            store.save(account);
            return management().register(account.accountId(), account.password())
                    .thenCompose(CloudManagementScreen::bindAfterLogin);
        } catch (IOException | RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static CompletableFuture<CloudSession> bindAfterLogin(CloudSession session) {
        return bindCurrentGameAccount().handle((ignored, failure) -> session);
    }

    private static CompletableFuture<CloudSession> loginWithLinkedGameAccount() {
        var selected = management().registry().selected().orElseThrow();
        var auth = new CloudAuthClient(new CloudHttpClient(selected.instance()));
        return auth.gameIdentityProviders().handle((providers, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(providers);
            Throwable cause = unwrap(failure);
            if (cause instanceof CloudHttpException http && (http.statusCode() == 401 || http.statusCode() == 404)) {
                return CompletableFuture.completedFuture(List.<CloudIdentityClient.IdentityProvider>of());
            }
            return CompletableFuture.<List<CloudIdentityClient.IdentityProvider>>failedFuture(cause);
        }).thenCompose(next -> next).thenCompose(providers -> {
            if (providers.isEmpty()) return CompletableFuture.failedFuture(new NoLinkedGameAccountException());
            final MinecraftSessionServiceJoiner.IdentityProfile profile;
            try { profile = MinecraftSessionServiceJoiner.currentProfile(); }
            catch (RuntimeException failure) { return CompletableFuture.failedFuture(new NoLinkedGameAccountException()); }
            return tryLinkedProvider(providers, 0, profile, null);
        });
    }

    private static CompletableFuture<CloudSession> tryLinkedProvider(
            List<CloudIdentityClient.IdentityProvider> providers, int index,
            MinecraftSessionServiceJoiner.IdentityProfile profile, Throwable unavailable
    ) {
        if (index >= providers.size()) return CompletableFuture.failedFuture(
                unavailable != null ? unavailable : new NoLinkedGameAccountException());
        return management().loginWithGameIdentity(providers.get(index).providerId(), profile,
                new MinecraftSessionServiceJoiner()).exceptionallyCompose(failure -> {
                    Throwable cause = unwrap(failure);
                    if (cause instanceof CloudHttpException http) {
                        if (http.errorCode() == CloudErrorCode.IDENTITY_NOT_LINKED) {
                            return CompletableFuture.failedFuture(new NoLinkedGameAccountException());
                        }
                        if (http.errorCode() == CloudErrorCode.IDENTITY_PROFILE_MISMATCH) {
                            return tryLinkedProvider(providers, index + 1, profile, unavailable);
                        }
                        if (http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE) {
                            return tryLinkedProvider(providers, index + 1, profile, unavailable != null ? unavailable : cause);
                        }
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    static CompletableFuture<CloudIdentityClient.CloudIdentity> bindCurrentGameAccount() {
        var selected = management().registry().selected().orElseThrow();
        if (!CloudInstanceRegistry.isBuiltinOfficial(selected) || CloudClientRuntime.state(selected.instanceId()) == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(text("official_only")));
        }
        final MinecraftSessionServiceJoiner.IdentityProfile profile;
        try { profile = MinecraftSessionServiceJoiner.currentProfile(); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        return management().identityProviders().thenCompose(providers -> tryBindingProvider(providers, 0, profile, null));
    }

    private static CompletableFuture<CloudIdentityClient.CloudIdentity> tryBindingProvider(
            List<CloudIdentityClient.IdentityProvider> providers, int index,
            MinecraftSessionServiceJoiner.IdentityProfile profile, Throwable unavailable
    ) {
        if (index >= providers.size()) return CompletableFuture.failedFuture(
                unavailable != null ? unavailable : new IllegalStateException(text("identity.no_matching_provider")));
        var client = CloudClientRuntime.state().identities();
        return client.createChallenge(providers.get(index).providerId(), profile.name(), profile.profileId().toString())
                .thenCompose(challenge -> client.joinAndComplete(challenge, new MinecraftSessionServiceJoiner()))
                .exceptionallyCompose(failure -> {
                    Throwable cause = unwrap(failure);
                    if (cause instanceof CloudHttpException http
                            && http.errorCode() == CloudErrorCode.IDENTITY_PROFILE_MISMATCH) {
                        return tryBindingProvider(providers, index + 1, profile, unavailable);
                    }
                    if (cause instanceof CloudHttpException http
                            && http.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE) {
                        return tryBindingProvider(providers, index + 1, profile, unavailable != null ? unavailable : cause);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    private static final class NoLinkedGameAccountException extends RuntimeException {
        private NoLinkedGameAccountException() { super("No game account is linked to an official Cloud account"); }
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

    private static Path generatedAccountFile() {
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
