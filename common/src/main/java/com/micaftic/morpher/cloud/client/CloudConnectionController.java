package com.micaftic.morpher.cloud.client;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, AtomicLong> requestGenerations = new ConcurrentHashMap<>();
    private final Map<String, CloudSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CloudInstanceRegistry.CloudInstanceProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, String> accounts = new ConcurrentHashMap<>();
    private final Map<String, Long> refreshAtMillis = new ConcurrentHashMap<>();
    private final Map<String, Boolean> refreshing = new ConcurrentHashMap<>();
    private volatile CloudInstanceRegistry.CloudInstanceProfile profile;

    private AtomicLong generation(String instanceId) {
        return requestGenerations.computeIfAbsent(instanceId, ignored -> new AtomicLong());
    }

    public void select(CloudInstanceRegistry.CloudInstanceProfile next) {
        Objects.requireNonNull(next, "next");
        CloudClientRuntime.RuntimeState configured = CloudClientRuntime.state(next.instanceId());
        if (configured != null && !configured.instance().equals(next.instance())) {
            discardSession(next.instanceId());
        }
        profile = next;
        CloudClientRuntime.selectInstance(next.instanceId());
    }

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
        long generation = generation(profile.instanceId()).incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(profile.instance()));
        return auth.login(accountId, password).thenApply(nextSession -> {
            if (generation(profile.instanceId()).get() != generation) {
                throw new IllegalStateException("Cloud login result is stale");
            }
            remember(profile, accountId, nextSession);
            CloudClientRuntime.configure(profile.instance(), nextSession, cacheRoot, clientVersion);
            if (this.profile == null || this.profile.instanceId().equals(profile.instanceId())) select(profile);
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
        long generation = generation(profile.instanceId()).incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(profile.instance()));
        return auth.register(accountId, password)
                .thenCompose(ignored -> auth.login(accountId.trim(), password))
                .thenApply(nextSession -> {
                    if (generation(profile.instanceId()).get() != generation) {
                        throw new IllegalStateException("Cloud registration result is stale");
                    }
                    remember(profile, accountId, nextSession);
                    CloudClientRuntime.configure(profile.instance(), nextSession, cacheRoot, clientVersion);
                    if (this.profile == null || this.profile.instanceId().equals(profile.instanceId())) select(profile);
                    return nextSession;
                });
    }

    public CompletableFuture<CloudSession> loginWithGameIdentity(
            CloudInstanceRegistry.CloudInstanceProfile profile,
            String providerId,
            MinecraftSessionServiceJoiner.IdentityProfile gameProfile,
            CloudIdentityClient.SessionJoiner joiner,
            Path cacheRoot,
            String clientVersion
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        requireText(clientVersion, "clientVersion");
        long expectedGeneration = generation(profile.instanceId()).incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(profile.instance()));
        return auth.loginWithGameIdentity(providerId, gameProfile, joiner).thenApply(result -> {
            if (generation(profile.instanceId()).get() != expectedGeneration) {
                throw new IllegalStateException("Cloud login result is stale");
            }
            remember(profile, result.accountId(), result.session());
            CloudClientRuntime.configure(profile.instance(), result.session(), cacheRoot, clientVersion);
            if (this.profile == null || this.profile.instanceId().equals(profile.instanceId())) select(profile);
            return result.session();
        });
    }

    public CompletableFuture<CloudSession> refresh(Path cacheRoot, String clientVersion) {
        CloudInstanceRegistry.CloudInstanceProfile currentProfile = profile;
        CloudSession currentSession = currentProfile == null ? null : sessions.get(currentProfile.instanceId());
        if (currentProfile == null || currentSession == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud session is not available"));
        }
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        requireText(clientVersion, "clientVersion");
        return refresh(currentProfile, currentSession, cacheRoot, clientVersion);
    }

    private CompletableFuture<CloudSession> refresh(CloudInstanceRegistry.CloudInstanceProfile currentProfile,
                                                    CloudSession currentSession, Path cacheRoot, String clientVersion) {
        String instanceId = currentProfile.instanceId();
        if (refreshing.putIfAbsent(instanceId, Boolean.TRUE) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud refresh already in progress"));
        }
        long generation = generation(instanceId).incrementAndGet();
        CloudAuthClient auth = new CloudAuthClient(new CloudHttpClient(currentProfile.instance()));
        return auth.refresh(currentSession.refreshToken()).thenApply(nextSession -> {
            if (generation(instanceId).get() != generation) {
                throw new IllegalStateException("Cloud refresh result is stale");
            }
            remember(currentProfile, accounts.getOrDefault(instanceId, ""), nextSession);
            CloudClientRuntime.configure(currentProfile.instance(), nextSession, cacheRoot, clientVersion);
            if (profile != null && profile.instanceId().equals(instanceId)) CloudClientRuntime.selectInstance(instanceId);
            return nextSession;
        }).whenComplete((ignored, failure) -> {
            refreshing.remove(instanceId);
            if (failure != null && sessions.containsKey(instanceId)) {
                refreshAtMillis.put(instanceId, System.currentTimeMillis() + 30_000L);
            }
        });
    }

    public void tick(Path cacheRoot, String clientVersion) {
        long now = System.currentTimeMillis();
        for (var entry : sessions.entrySet()) {
            String instanceId = entry.getKey();
            if (now < refreshAtMillis.getOrDefault(instanceId, Long.MAX_VALUE) || refreshing.containsKey(instanceId)) continue;
            CloudInstanceRegistry.CloudInstanceProfile saved = profiles.get(instanceId);
            if (saved != null) refresh(saved, entry.getValue(), cacheRoot, clientVersion);
        }
    }

    private void remember(CloudInstanceRegistry.CloudInstanceProfile profile, String accountId, CloudSession nextSession) {
        String id = profile.instanceId();
        profiles.put(id, profile);
        sessions.put(id, nextSession);
        accounts.put(id, accountId.trim());
        long lead = Math.min(120, Math.max(1, nextSession.accessExpiresInSeconds() / 5));
        refreshAtMillis.put(id, System.currentTimeMillis() + Math.max(1, nextSession.accessExpiresInSeconds() - lead) * 1000L);
    }

    public synchronized void logout() {
        CloudInstanceRegistry.CloudInstanceProfile selected = profile;
        if (selected == null) return;
        discardSession(selected.instanceId());
        profile = null;
    }

    private void discardSession(String id) {
        generation(id).incrementAndGet();
        sessions.remove(id);
        profiles.remove(id);
        accounts.remove(id);
        refreshAtMillis.remove(id);
        refreshing.remove(id);
        CloudClientRuntime.removeInstance(id);
    }

    public boolean isAuthenticated() {
        return profile != null && sessions.containsKey(profile.instanceId())
                && CloudClientRuntime.state(profile.instanceId()) != null;
    }

    public CloudInstanceRegistry.CloudInstanceProfile profile() {
        return profile;
    }

    public boolean hasSession() {
        return profile != null && sessions.containsKey(profile.instanceId());
    }

    public String accountId(String instanceId) {
        return accounts.getOrDefault(instanceId, "");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
