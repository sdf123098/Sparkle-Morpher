package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import java.util.*;
import java.util.concurrent.*;

/** HTTP boundary. Long-lived keys are sent only in activation bodies to their exact scoped origin. */
public final class CloudOfflineIdentityClient {
    public static final String CAPABILITY="cloud_offline_identity_v1";
    private final CloudHttpClient http;
    public CloudOfflineIdentityClient(CloudHttpClient http){this.http=Objects.requireNonNull(http);}
    public record Session(String identityId,String sessionId,String sessionToken,long bindingRevision,long appearanceRevision,UUID entityUuid,long expiresAtUnixMs){
        public Session {
            if(identityId==null||!identityId.matches("coff_[0-9a-f]{32}")||sessionId==null||!sessionId.matches("cos_[0-9a-f]{32}")||sessionToken==null||!sessionToken.matches("[A-Za-z0-9_-]{43}")||bindingRevision<1||appearanceRevision<0||entityUuid==null||expiresAtUnixMs<1)throw new IllegalArgumentException("Invalid offline publication session");
        }
        public Session withRevision(long revision){return new Session(identityId,sessionId,sessionToken,bindingRevision,revision,entityUuid,expiresAtUnixMs);}
        @Override public String toString(){return "OfflineSession["+sessionId+", binding="+bindingRevision+"]";}
    }
    /** Automatic request/approval handshake; no user-entered key and no secret response. */
    public CompletableFuture<Session> requestAndActivate(CloudOfflineCredentialStore.Scope scope,CloudOfflineCredentialStore.Credential credential,UUID entity,String profileName){
        var actual=new CloudOfflineCredentialStore.Scope(http.instance().instanceId(),http.instance().origin(),scope.accountId());
        if(!actual.equals(scope)||!credential.enabled())return CompletableFuture.failedFuture(new IllegalArgumentException("Offline credential scope mismatch"));
        JsonObject body=new JsonObject();body.addProperty("offline_key",credential.offlineKey());body.addProperty("label",profileName);
        return safe(http.visualJson("POST","/v1/offline-identities/request",body.toString(),65536).thenCompose(text->{
            var row=JsonParser.parseString(text).getAsJsonObject();
            if(!scope.accountId().equals(row.get("account_id").getAsString()))throw new CloudHttpException(403,CloudErrorCode.OFFLINE_KEY_INVALID,"Offline Cloud account mismatch");
            switch(row.get("authorization_status").getAsString()){
                case "APPROVED":return activate(scope,credential,entity,profileName);
                case "PENDING":return CompletableFuture.failedFuture(new CloudHttpException(403,CloudErrorCode.OFFLINE_AUTHORIZATION_PENDING,"Awaiting administrator approval"));
                case "REVOKED":return CompletableFuture.failedFuture(new CloudHttpException(403,CloudErrorCode.OFFLINE_AUTHORIZATION_DENIED,"Authorization revoked"));
                default:return CompletableFuture.failedFuture(new IllegalStateException("Invalid offline authorization status"));
            }
        }));
    }
    public CompletableFuture<Session> activate(CloudOfflineCredentialStore.Scope scope,CloudOfflineCredentialStore.Credential credential,UUID entity,String profileName){
        var actual=new CloudOfflineCredentialStore.Scope(http.instance().instanceId(),http.instance().origin(),scope.accountId());
        if(!actual.equals(scope)||!credential.enabled())return CompletableFuture.failedFuture(new IllegalArgumentException("Offline credential scope mismatch"));
        return safe(http.getJson("/v1/offline-identities/me").thenCompose(text->{
            JsonObject root=JsonParser.parseString(text).getAsJsonObject();
            if(!scope.accountId().equals(root.get("account_id").getAsString()))throw new CloudHttpException(403,CloudErrorCode.OFFLINE_KEY_INVALID,"Offline Cloud account mismatch");
            JsonObject match=null;
            for(var item:root.getAsJsonArray("identities")){
                var row=item.getAsJsonObject();
                if(row.has("key_id")&&!row.get("key_id").isJsonNull()&&credential.keyId().equals(row.get("key_id").getAsString())){
                    if(match!=null)throw new IllegalArgumentException("Duplicate offline identity");match=row;
                }
            }
            if(match==null)throw new CloudHttpException(403,CloudErrorCode.OFFLINE_KEY_INVALID,"Offline key is not owned by this Cloud account");
            if(!"ACTIVE".equals(match.get("key_status").getAsString()))throw new CloudHttpException(403,CloudErrorCode.OFFLINE_KEY_REVOKED,"Offline key revoked");
            String expectedIdentity=match.get("identity_id").getAsString();
            JsonObject body=new JsonObject();body.addProperty("offline_key",credential.offlineKey());body.addProperty("entity_uuid",entity.toString());body.addProperty("profile_name",profileName);body.addProperty("expected_binding_revision",number(match,"binding_revision"));
            return http.visualJson("POST","/v1/offline-identities/activate",body.toString(),65536).thenApply(response->{
                JsonObject row=JsonParser.parseString(response).getAsJsonObject();
                Session session=new Session(row.get("identity_id").getAsString(),row.get("session_id").getAsString(),row.get("session_token").getAsString(),number(row,"binding_revision"),number(row,"appearance_revision"),UUID.fromString(row.get("entity_uuid").getAsString()),number(row,"expires_at_unix_ms"));
                if(!session.identityId().equals(expectedIdentity)||!session.entityUuid().equals(entity))throw new IllegalArgumentException("Offline activation binding mismatch");
                return session;
            });
        }));
    }
    public CompletableFuture<Long> publish(Session session,CloudPlayerSelection selection,CloudPlayerMotion motion,JsonObject display){
        JsonObject body=new JsonObject();body.addProperty("binding_revision",session.bindingRevision());body.addProperty("expected_revision",session.appearanceRevision());
        body.add("motion",selection==null||motion==null?JsonNull.INSTANCE:motion.toJson());
        if(selection==null)body.add("asset_id",JsonNull.INSTANCE);
        else{body.addProperty("asset_id",selection.ref().assetId());body.addProperty("asset_revision",selection.ref().revision());body.addProperty("raw_sha256",selection.ref().rawSha256());body.addProperty("texture_id",selection.textureId());if(display!=null)body.add("display_state",display.deepCopy());}
        return safe(http.visualJson("PUT","/v1/offline-player-sessions/"+session.sessionId()+"/appearance",body.toString(),65536,session.sessionToken()).thenApply(text->{
            long revision=number(JsonParser.parseString(text).getAsJsonObject(),"revision");
            if(revision<=session.appearanceRevision())throw new IllegalArgumentException("Offline revision did not advance");return revision;
        }));
    }
    public CompletableFuture<Void> withdraw(Session session){return safe(http.visualJson("DELETE","/v1/offline-player-sessions/"+session.sessionId(),"{}",65536,session.sessionToken()).thenApply(text->null));}
    private static long number(JsonObject row,String field){String value=row.get(field).getAsString();if(!value.matches("[0-9]{1,19}"))throw new IllegalArgumentException("Invalid offline revision");return Long.parseLong(value);}
    private static <T> CompletableFuture<T> safe(CompletableFuture<T> operation){
        return operation.exceptionallyCompose(error->{while(error instanceof CompletionException&&error.getCause()!=null)error=error.getCause();
            if(error instanceof CloudHttpException failure)return CompletableFuture.failedFuture(new CloudHttpException(failure.statusCode(),failure.errorCode(),"Offline Cloud request: "+failure.errorCode()));
            return CompletableFuture.failedFuture(new IllegalStateException("Offline Cloud request failed"));});
    }
}
