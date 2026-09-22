package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Offline identity approval and one-time claim-code operations. */
public final class CloudIdentityBindingClient {

    private final CloudHttpClient http;

    public CloudIdentityBindingClient(CloudHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    public CompletableFuture<CloudBinding> requestApproval(String identityId, String scopeId, String worldEpoch, String targetId) {
        JsonObject body = new JsonObject();
        body.addProperty("scope_id", segment(scopeId));
        body.addProperty("world_epoch", segment(worldEpoch));
        body.addProperty("identity_id", segment(identityId));
        body.addProperty("target_id", segment(targetId));
        return http.postJson("/v1/identities/" + segment(identityId) + "/offline-bindings", body.toString()).thenApply(CloudIdentityBindingClient::parseBinding);
    }

    public CompletableFuture<CloudClaimCode> createClaimCode(String targetId, String worldEpoch, String entityUuid, Long expiresInSeconds) {
        JsonObject body = new JsonObject();
        body.addProperty("world_epoch", segment(worldEpoch));
        body.addProperty("entity_uuid", Objects.requireNonNull(entityUuid, "entityUuid"));
        if (expiresInSeconds != null) body.addProperty("expires_in_seconds", expiresInSeconds);
        return http.postJson("/v1/targets/" + segment(targetId) + "/claim-codes", body.toString()).thenApply(CloudIdentityBindingClient::parseClaimCode);
    }

    public CompletableFuture<CloudBinding> redeemClaimCode(String code, String identityId) {
        JsonObject body = new JsonObject();
        body.addProperty("code", Objects.requireNonNull(code, "code"));
        body.addProperty("identity_id", segment(identityId));
        return http.postJson("/v1/claim-codes/redeem", body.toString()).thenApply(CloudIdentityBindingClient::parseBinding);
    }

    public CompletableFuture<Void> revokeClaimCode(String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", Objects.requireNonNull(code, "code"));
        return http.postJson("/v1/claim-codes/revoke", body.toString()).thenApply(ignored -> null);
    }

    public CompletableFuture<CloudBinding> approve(String bindingId, String status) {
        return approve(bindingId, status, 0L);
    }

    public CompletableFuture<CloudBinding> approve(String bindingId, String status, long expectedRevision) {
        JsonObject body = new JsonObject();
        body.addProperty("status", Objects.requireNonNull(status, "status"));
        body.addProperty("expected_revision", expectedRevision);
        return http.putJson("/v1/scoped-identity-bindings/" + segment(bindingId), body.toString()).thenApply(CloudIdentityBindingClient::parseBinding);
    }

    static CloudBinding parseBindingForTest(String body) { return parseBinding(body); }
    static CloudClaimCode parseClaimCodeForTest(String body) { return parseClaimCode(body); }

    private static CloudBinding parseBinding(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return new CloudBinding(string(root, "binding_id"), string(root, "account_id"), string(root, "identity_id"), string(root, "target_id"), string(root, "scope_id"), string(root, "world_epoch"), string(root, "entity_uuid"), string(root, "verification_method"), string(root, "status"), root.has("approved_by") && !root.get("approved_by").isJsonNull() ? root.get("approved_by").getAsString() : null, root.get("revision").getAsLong());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud identity binding");
        }
    }

    private static CloudClaimCode parseClaimCode(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return new CloudClaimCode(string(root, "code"), string(root, "scope_id"), string(root, "world_epoch"), string(root, "target_id"), string(root, "entity_uuid"), root.get("expires_in_seconds").getAsLong());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud claim code");
        }
    }

    private static String string(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull() || object.get(name).getAsString().isBlank()) throw new IllegalArgumentException("Missing Cloud field: " + name);
        return object.get(name).getAsString();
    }

    private static String segment(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw new IllegalArgumentException("Cloud path segment must be a slug");
        return value;
    }

    public record CloudBinding(String bindingId, String accountId, String identityId, String targetId, String scopeId, String worldEpoch, String entityUuid, String verificationMethod, String status, String approvedBy, long revision) {}
    public record CloudClaimCode(String code, String scopeId, String worldEpoch, String targetId, String entityUuid, long expiresInSeconds) {}
}
