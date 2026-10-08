package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Cloud account session operations; game identity verification remains a separate flow. */
public final class CloudAuthClient {

    private final CloudHttpClient unauthenticated;

    public CloudAuthClient(CloudHttpClient unauthenticated) {
        this.unauthenticated = Objects.requireNonNull(unauthenticated, "unauthenticated");
    }

    /** Creates a public Cloud account; game identity verification remains a separate step. */
    public CompletableFuture<Void> register(String accountId, String password) {
        return unauthenticated.postJson("/v1/accounts", registrationRequest(accountId, password))
                .thenApply(ignored -> null);
    }

    static String registrationRequestForTest(String accountId, String password) {
        return registrationRequest(accountId, password);
    }

    private static String registrationRequest(String accountId, String password) {
        String normalizedId = accountId == null ? "" : accountId.trim();
        if (normalizedId.length() < 1 || normalizedId.length() > 128
                || !normalizedId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new CloudValidationException(CloudValidationException.Reason.INVALID_ACCOUNT_ID);
        }
        if (password == null || password.length() < 8 || password.length() > 1024
                || password.indexOf('\r') >= 0 || password.indexOf('\n') >= 0) {
            throw new CloudValidationException(CloudValidationException.Reason.INVALID_PASSWORD);
        }
        JsonObject body = new JsonObject();
        body.addProperty("account_id", normalizedId);
        body.addProperty("password", password);
        return body.toString();
    }

    public CompletableFuture<CloudSession> login(String accountId, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", Objects.requireNonNull(accountId, "accountId"));
        body.addProperty("password", Objects.requireNonNull(password, "password"));
        return unauthenticated.postJson("/v1/sessions", body.toString()).thenApply(CloudAuthClient::parseSession);
    }

    public CompletableFuture<CloudSession> refresh(String refreshToken) {
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", Objects.requireNonNull(refreshToken, "refreshToken"));
        return unauthenticated.postJson("/v1/sessions/refresh", body.toString()).thenApply(CloudAuthClient::parseSession);
    }

    public CompletableFuture<List<CloudIdentityClient.IdentityProvider>> gameIdentityProviders() {
        return new CloudIdentityClient(unauthenticated).listProviders();
    }

    public CompletableFuture<GameAccountSession> loginWithGameIdentity(
            String providerId,
            MinecraftSessionServiceJoiner.IdentityProfile profile,
            CloudIdentityClient.SessionJoiner joiner
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(joiner, "joiner");
        CloudScopeClient.segment(providerId);
        JsonObject body = new JsonObject();
        body.addProperty("provider_id", providerId);
        body.addProperty("username", profile.name());
        body.addProperty("profile_uuid", profile.profileId().toString());
        return unauthenticated.postJson("/v1/auth/login-challenges", body.toString())
                .thenApply(CloudIdentityClient::parseChallengeForTest)
                .thenCompose(challenge -> joiner.prove(challenge, unauthenticated.instance(), "login").thenCompose(proof -> {
                    JsonObject completion = proof.deepCopy();
                    completion.addProperty("challenge_id", challenge.challengeId());
                    return unauthenticated.postJson("/v1/auth/login-challenges/"
                                    + CloudScopeClient.segment(challenge.challengeId()) + "/complete", completion.toString());
                }))
                .thenApply(response -> {
                    try {
                        String accountId = JsonParser.parseString(response).getAsJsonObject().get("account_id").getAsString();
                        if (accountId.isBlank()) throw new IllegalArgumentException("Missing account ID");
                        return new GameAccountSession(accountId, parseSession(response));
                    } catch (RuntimeException failure) {
                        throw new CloudHttpException(200,
                                com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE,
                                "Malformed game identity login response");
                    }
                });
    }

    public record GameAccountSession(String accountId, CloudSession session) { }

    public CloudHttpClient authenticated(CloudSession session) {
        return new CloudHttpClient(unauthenticated.instance(), session.accessToken());
    }

    static CloudSession parseSession(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return new CloudSession(
                    root.get("access_token").getAsString(),
                    root.get("refresh_token").getAsString(),
                    root.get("access_expires_in_seconds").getAsLong(),
                    root.get("refresh_expires_in_seconds").getAsLong());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud session response");
        }
    }
}

