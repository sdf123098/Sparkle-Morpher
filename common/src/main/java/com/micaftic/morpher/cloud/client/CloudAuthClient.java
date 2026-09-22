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

