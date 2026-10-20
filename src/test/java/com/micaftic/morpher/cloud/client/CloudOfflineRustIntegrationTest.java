package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an explicitly supplied isolated Rust service. Never points at production. */
class CloudOfflineRustIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path credentials;
    private static final HttpClient RAW=HttpClient.newHttpClient();
    private record Account(String id,String access,String key) {
        @Override public String toString(){return "IsolatedAccount["+id+"]";}
    }
    private JsonObject raw(URI base,String method,String path,String token,JsonObject body) throws Exception {
        var request=HttpRequest.newBuilder(base.resolve(path)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        var response=RAW.send(request.method(method,HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(response.statusCode()>=200&&response.statusCode()<300,"Isolated Rust HTTP status "+response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private Account account(URI base,String name) throws Exception {
        String id="offline_"+name+"_"+UUID.randomUUID().toString().replace("-","");
        JsonObject login=new JsonObject();login.addProperty("account_id",id);login.addProperty("password","isolated-integration-password");
        raw(base,"POST","/v1/accounts",null,login);
        String token=raw(base,"POST","/v1/sessions",null,login).get("access_token").getAsString();
        var scope=new CloudOfflineCredentialStore.Scope("offline-integration",URI.create("https://offline.test"),id);
        String key=new CloudOfflineCredentialStore(credentials.resolve("devices.json")).loadOrCreate(scope).offlineKey();
        return new Account(id,token,key);
    }
    private CloudPlayerSelection asset(CloudHttpClient http,String label) throws Exception {
        byte[] bytes=("isolated-rust-http-fixture-"+label).getBytes(StandardCharsets.UTF_8);String sha=CloudAssetCache.sha256(bytes);
        String id="offline-model-"+UUID.randomUUID();
        var response=http.uploadAsset(bytes,id,label,"ysm",sha,UUID.randomUUID().toString(),"PUBLIC").get(15,TimeUnit.SECONDS);
        assertTrue(response.statusCode()>=200&&response.statusCode()<300,"Fixture upload status "+response.statusCode());
        JsonObject result=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
        return new CloudPlayerSelection(http.instance().instanceId(),CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),new CloudAssetRef(id,result.get("revision").getAsLong(),sha),"ysm",label);
    }
    @Test void twoIndependentOfflineAccountsDiscoverEachOthersTexturesAndMotionThroughRealRust() throws Exception {
        String endpoint=System.getenv("SPM_OFFLINE_RUST_TEST_URL");Assumptions.assumeTrue(endpoint!=null&&!endpoint.isBlank(),"Isolated Rust service not configured");
        URI base=URI.create(endpoint);assertEquals("127.0.0.1",base.getHost(),"Integration service must be loopback");
        Account a=account(base,"a"),b=account(base,"b");
        var config=CloudInstanceConfig.v1("offline-integration",URI.create("https://offline.test"));
        var loopback=new CloudPlayerPresenceClientTest.LoopbackClient(HttpClient.newHttpClient(),base);
        var httpA=new CloudHttpClient(config,loopback,a.access());var httpB=new CloudHttpClient(config,loopback,b.access());
        var apiA=new CloudOfflineIdentityClient(httpA);var apiB=new CloudOfflineIdentityClient(httpB);
        var keyA=new CloudOfflineCredentialStore.Credential(a.key(),true);var keyB=new CloudOfflineCredentialStore.Credential(b.key(),true);
        UUID uuidA=UUID.randomUUID(),uuidB=UUID.randomUUID();
        var scopeA=new CloudOfflineCredentialStore.Scope(config.instanceId(),config.origin(),a.id());
        var sessionA=apiA.requestAndActivate(scopeA,keyA,uuidA,"离线A").get(15,TimeUnit.SECONDS);
        var sessionB=apiB.requestAndActivate(new CloudOfflineCredentialStore.Scope(config.instanceId(),config.origin(),b.id()),keyB,uuidB,"离线B").get(15,TimeUnit.SECONDS);
        var modelA=asset(httpA,"贴图A");var modelB=asset(httpB,"贴图B");
        var stateA=new CloudPlayerMotionState();stateA.play("动作A",1000);var stateB=new CloudPlayerMotionState();stateB.play("动作B",2000);
        apiA.publish(sessionA,modelA,stateA.snapshot(),null).get(15,TimeUnit.SECONDS);apiB.publish(sessionB,modelB,stateB.snapshot(),null).get(15,TimeUnit.SECONDS);
        var observedByA=new CloudPlayerPresenceClient(httpA).queryDetailed(List.of(uuidB),true).get(15,TimeUnit.SECONDS).get(uuidB);
        var observedByB=new CloudPlayerPresenceClient(httpB).queryDetailed(List.of(uuidA),true).get(15,TimeUnit.SECONDS).get(uuidA);
        assertEquals("贴图B",observedByA.selection().textureId());assertEquals("动作B",observedByA.selection().motion().animationKey());
        assertEquals("贴图A",observedByB.selection().textureId());assertEquals("动作A",observedByB.selection().motion().animationKey());
        assertNotEquals(observedByA.publisherId(),observedByB.publisherId());
        assertNull(new CloudPlayerPresenceClient(httpA).query(List.of(uuidB)).get(15,TimeUnit.SECONDS).get(uuidB));
        UUID nextUuid=UUID.randomUUID();var next=apiA.activate(scopeA,keyA,nextUuid,"离线A2").get(15,TimeUnit.SECONDS);
        assertEquals(sessionA.identityId(),next.identityId());assertNotEquals(sessionA.sessionId(),next.sessionId());
        var failure=assertThrows(ExecutionException.class,()->apiA.publish(sessionA,modelA,null,null).get(15,TimeUnit.SECONDS));
        assertInstanceOf(CloudHttpException.class,failure.getCause());assertEquals(CloudErrorCode.OFFLINE_BINDING_STALE,((CloudHttpException)failure.getCause()).errorCode());
        assertNull(new CloudPlayerPresenceClient(httpB).queryDetailed(List.of(uuidA),true).get(15,TimeUnit.SECONDS).get(uuidA).selection());
        apiA.withdraw(next).get(15,TimeUnit.SECONDS);apiB.withdraw(sessionB).get(15,TimeUnit.SECONDS);
    }
}
