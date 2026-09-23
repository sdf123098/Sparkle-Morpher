package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.client.upload.CloudUploadRuntime;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-loader login/refresh/logout coordinator.
 *
 * <p>Passwords are used only for the login request. Access and refresh tokens
 * remain in memory in this controller/runtime and are released on logout or
 * client lifecycle cleanup.</p>
 */
public final class CloudConnectionController {
    private final AtomicLong requestGeneration = new AtomicLong();
    private volatile CloudInstanceRegistry.CloudInstanceProfile profile;
    private volatile CloudSession session;

    public CompletableFuture<CloudSession> login(
            CloudInstanceRegistry.CloudInstanceProfile profile,
            String accountId,
            String password,
            Path cacheRoot,
            String clientVersion
    ) {
        Objects.requireNonNull(profile, "profile");
        requireText(accountId, "accountId");
        requireText(password, "password");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        requireText(clientVersion, "clientVersion");
        long generation = requestGeneration.incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(profile.instance()));
        return auth.login(accountId, password).thenApply(nextSession -> {
            if (requestGeneration.get() != generation) {
                throw new IllegalStateException("Cloud login result is stale");
            }
            this.profile = profile;
            this.session = nextSession;
            CloudClientRuntime.configure(profile.instance(), nextSession, cacheRoot, clientVersion);
            return nextSession;
        });
    }

    public CompletableFuture<CloudSession> register(
            CloudInstanceRegistry.CloudInstanceProfile profile,
            String accountId,
            String password,
            Path cacheRoot,
            String clientVersion
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        requireText(clientVersion, "clientVersion");
        long generation = requestGeneration.incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(profile.instance()));
        return auth.register(accountId, password)
                .thenCompose(ignored -> auth.login(accountId.trim(), password))
                .thenApply(nextSession -> {
                    if (requestGeneration.get() != generation) {
                        throw new IllegalStateException("Cloud registration result is stale");
                    }
                    this.profile = profile;
                    this.session = nextSession;
                    CloudClientRuntime.configure(profile.instance(), nextSession, cacheRoot, clientVersion);
                    return nextSession;
                });
    }

    public CompletableFuture<CloudSession> refresh(Path cacheRoot, String clientVersion) {
        CloudInstanceRegistry.CloudInstanceProfile currentProfile = profile;
        CloudSession currentSession = session;
        if (currentProfile == null || currentSession == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud session is not available"));
        }
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        requireText(clientVersion, "clientVersion");
        long generation = requestGeneration.incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(currentProfile.instance()));
        return auth.refresh(currentSession.refreshToken()).thenApply(nextSession -> {
            if (requestGeneration.get() != generation) {
                throw new IllegalStateException("Cloud refresh result is stale");
            }
            session = nextSession;
            CloudClientRuntime.configure(currentProfile.instance(), nextSession, cacheRoot, clientVersion);
            return nextSession;
        });
    }

    public synchronized void logout() {
        requestGeneration.incrementAndGet();
        session = null;
        profile = null;
        CloudUploadRuntime.clear();
    }

    public boolean isAuthenticated() {
        return session != null && CloudClientRuntime.isConfigured();
    }

    public CloudInstanceRegistry.CloudInstanceProfile profile() {
        return profile;
    }

    public boolean hasSession() {
        return session != null;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
