package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        String normalizedId = Objects.requireNonNull(accountId, "accountId").trim();
        if (normalizedId.length() < 1 || normalizedId.length() > 128
                || !normalizedId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Cloud account ID must start with a letter or number and contain only letters, numbers, '.', '_' or '-'");
        }
        if (password == null || password.length() < 8 || password.length() > 1024
                || password.indexOf('\r') >= 0 || password.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Cloud password must be 8 to 1024 characters");
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

