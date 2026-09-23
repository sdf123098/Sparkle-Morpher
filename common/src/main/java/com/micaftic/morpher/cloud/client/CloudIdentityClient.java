package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.micaftic.morpher.cloud.identity.CloudIdentityRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cloud game-identity verification boundary. The loader/version adapter owns
 * the Session Service join; this client only sends the opaque challenge id to
 * the trusted Cloud origin and never accepts a provider URL from the client.
 */
public final class CloudIdentityClient {

    private final CloudHttpClient http;

    public CloudIdentityClient(CloudHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    public CompletableFuture<List<CloudIdentity>> listIdentities() {
        return http.getJson("/v1/identities").thenApply(CloudIdentityClient::parseIdentities);
    }

    /** Returns only providers explicitly enabled by the selected Cloud operator. */
    public CompletableFuture<List<IdentityProvider>> listProviders() {
        return http.getJson("/v1/identity-providers").thenApply(CloudIdentityClient::parseProviders);
    }

    /** Registers only a scope-local offline identity; it is not verified or globally trusted. */
    public CompletableFuture<CloudIdentity> registerOfflineIdentity(
            String scopeId,
            UUID profileUuid,
            String displayName
    ) {
        return http.postJson("/v1/identities", offlineIdentityRequest(scopeId, profileUuid, displayName))
                .thenApply(CloudIdentityClient::parseIdentity);
    }

    public CompletableFuture<CloudIdentityChallenge> createChallenge(String providerId, String username, String profileUuid) {
        CloudScopeClient.segment(providerId);
        if (username == null || username.isBlank() || username.length() > 256) {
            throw new IllegalArgumentException("username must be between 1 and 256 characters");
        }
        JsonObject body = new JsonObject();
        body.addProperty("provider_id", providerId);
        body.addProperty("username", username);
        if (profileUuid != null && !profileUuid.isBlank()) body.addProperty("profile_uuid", profileUuid);
        return http.postJson("/v1/auth/challenges", body.toString()).thenApply(CloudIdentityClient::parseChallenge);
    }

    public CompletableFuture<CloudIdentity> completeChallenge(String challengeId) {
        requireChallengeId(challengeId);
        JsonObject body = new JsonObject();
        body.addProperty("challenge_id", challengeId);
        return http.postJson("/v1/auth/challenges/" + CloudScopeClient.segment(challengeId) + "/complete", body.toString())
                .thenApply(CloudIdentityClient::parseIdentity);
    }

    public CompletableFuture<CloudIdentity> joinAndComplete(CloudIdentityChallenge challenge, SessionJoiner joiner) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(joiner, "joiner");
        return joiner.join(challenge).thenCompose(ignored -> completeChallenge(challenge.challengeId()));
    }

    static CloudIdentityChallenge parseChallengeForTest(String body) { return parseChallenge(body); }

    static CloudIdentity parseIdentityForTest(String body) { return parseIdentity(body); }

    static List<CloudIdentity> parseIdentitiesForTest(String body) { return parseIdentities(body); }

    static List<IdentityProvider> parseProvidersForTest(String body) { return parseProviders(body); }

    static String offlineIdentityRequestForTest(String scopeId, UUID profileUuid, String displayName) {
        return offlineIdentityRequest(scopeId, profileUuid, displayName);
    }

    private static String offlineIdentityRequest(String scopeId, UUID profileUuid, String displayName) {
        CloudIdentityRef identity = CloudIdentityRef.offline(scopeId, Objects.requireNonNull(profileUuid, "profileUuid"));
        if (displayName == null || displayName.isBlank() || displayName.length() > 256
                || displayName.indexOf('\r') >= 0 || displayName.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("displayName must be a non-empty single-line value of at most 256 characters");
        }
        JsonObject body = new JsonObject();
        body.addProperty("identity", identity.toWireString());
        body.addProperty("display_name", displayName);
        return body.toString();
    }

    private static List<CloudIdentity> parseIdentities(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("Cloud identities must be an array");
            JsonArray array = root.getAsJsonArray();
            List<CloudIdentity> identities = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Cloud identity must be an object");
                identities.add(parseIdentity(element.getAsJsonObject()));
            }
            return List.copyOf(identities);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud identity catalog");
        }
    }

    private static List<IdentityProvider> parseProviders(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("Cloud identity providers must be an array");
            List<IdentityProvider> providers = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Cloud identity provider must be an object");
                JsonObject object = element.getAsJsonObject();
                if (!object.has("enabled") || !object.get("enabled").getAsBoolean()) continue;
                providers.add(new IdentityProvider(required(object, "provider_id"), required(object, "display_name")));
            }
            return List.copyOf(providers);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud identity provider catalog");
        }
    }

    private static CloudIdentityChallenge parseChallenge(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return new CloudIdentityChallenge(required(root, "challenge_id"), required(root, "provider_id"), required(root, "server_id"), root.get("expires_in_seconds").getAsLong());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud identity challenge response");
        }
    }

    private static CloudIdentity parseIdentity(String body) {
        try {
            return parseIdentity(JsonParser.parseString(body).getAsJsonObject());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud identity response");
        }
    }

    private static CloudIdentity parseIdentity(JsonObject root) {
        return new CloudIdentity(required(root, "identity_id"), required(root, "account_id"), required(root, "identity"), required(root, "display_name"), required(root, "verification_status"));
    }

    private static String required(JsonObject root, String name) {
        if (!root.has(name) || root.get(name).isJsonNull() || root.get(name).getAsString().isBlank()) throw new IllegalArgumentException("Missing Cloud identity field: " + name);
        return root.get(name).getAsString();
    }

    private static void requireChallengeId(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) throw new IllegalArgumentException("challengeId must not be blank");
    }

    @FunctionalInterface
    public interface SessionJoiner {
        CompletableFuture<Void> join(CloudIdentityChallenge challenge);
    }

    public record CloudIdentityChallenge(String challengeId, String providerId, String serverId, long expiresInSeconds) {
        public CloudIdentityChallenge {
            if (challengeId == null || challengeId.isBlank() || providerId == null || providerId.isBlank() || serverId == null || serverId.isBlank() || expiresInSeconds <= 0) {
                throw new IllegalArgumentException("invalid Cloud identity challenge");
            }
        }
    }

    public record CloudIdentity(String identityId, String accountId, String identity, String displayName, String verificationStatus) {
        public CloudIdentity {
            if (identityId == null || identityId.isBlank() || accountId == null || accountId.isBlank()
                    || displayName == null || displayName.isBlank() || verificationStatus == null || verificationStatus.isBlank()) {
                throw new IllegalArgumentException("invalid Cloud identity");
            }
            CloudIdentityRef.parse(identity);
        }

        public CloudIdentityRef identityRef() {
            return CloudIdentityRef.parse(identity);
        }
    }

    public record IdentityProvider(String providerId, String displayName) {
        public IdentityProvider {
            CloudScopeClient.segment(providerId);
            if (displayName == null || displayName.isBlank() || displayName.length() > 256) {
                throw new IllegalArgumentException("invalid Cloud identity provider");
            }
        }
    }
}
