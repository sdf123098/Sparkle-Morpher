package com.micaftic.morpher.cloud.client;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CloudOfflineIdentityClientTest {
    private static final String ID="coff_"+"a".repeat(32), SID="cos_"+"b".repeat(32), KID="cok_"+"c".repeat(32), SECRET="d".repeat(43);
    @Test void activationAndPublicationUseExactAccountBindingDualCredentialsAndUtf8() throws Exception {
        UUID uuid=UUID.randomUUID();List<String[]> requests=new CopyOnWriteArrayList<>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/",exchange->{
            String path=exchange.getRequestURI().getPath();String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            requests.add(new String[]{exchange.getRequestMethod(),path,exchange.getRequestHeaders().getFirst("Authorization"),exchange.getRequestHeaders().getFirst("X-SPM-Offline-Session"),body});
            String response=path.endsWith("/request")?"{\"account_id\":\"owner\",\"authorization_status\":\"APPROVED\"}":path.endsWith("/me")?"{\"account_id\":\"owner\",\"identities\":[{\"identity_id\":\""+ID+"\",\"key_id\":\""+KID+"\",\"key_status\":\"ACTIVE\",\"binding_revision\":3}]}"
                :path.endsWith("/activate")?"{\"identity_id\":\""+ID+"\",\"session_id\":\""+SID+"\",\"session_token\":\""+SECRET+"\",\"entity_uuid\":\""+uuid+"\",\"binding_revision\":4,\"appearance_revision\":7,\"expires_at_unix_ms\":60000}"
                :path.endsWith("/appearance")?"{\"revision\":8}":"{\"ok\":true}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}
        });server.start();
        try {
            var config=CloudInstanceConfig.v1("test",URI.create("https://cloud.test"));
            var loopback=new CloudPlayerPresenceClientTest.LoopbackClient(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            var api=new CloudOfflineIdentityClient(new CloudHttpClient(config,loopback,"access-token"));
            var credential=new CloudOfflineCredentialStore.Credential("spm-offline-v1."+KID+"."+"e".repeat(43),true);
            var session=api.requestAndActivate(new CloudOfflineCredentialStore.Scope("test",config.origin(),"owner"),credential,uuid,"离线角色").get(10,TimeUnit.SECONDS);
            assertEquals(8,api.publish(session,null,null,null).get(10,TimeUnit.SECONDS));api.withdraw(session).get(10,TimeUnit.SECONDS);
            assertEquals(5,requests.size());for(var request:requests)assertEquals("Bearer access-token",request[2]);
            assertEquals("/v1/offline-identities/request",requests.get(0)[1]);assertNull(requests.get(0)[3]);assertNull(requests.get(2)[3]);assertEquals("POST",requests.get(2)[0]);
            var activation=JsonParser.parseString(requests.get(2)[4]).getAsJsonObject();assertEquals("离线角色",activation.get("profile_name").getAsString());assertEquals(3,activation.get("expected_binding_revision").getAsInt());
            assertEquals(SECRET,requests.get(3)[3]);assertEquals(SECRET,requests.get(4)[3]);
            assertFalse(requests.get(3)[4].contains(credential.offlineKey()));
            assertFalse(requests.get(3)[4].contains("entity_uuid"));assertFalse(session.toString().contains(SECRET));
            var wrong=new CloudOfflineCredentialStore.Scope("test",URI.create("https://other.test"),"owner");
            assertThrows(ExecutionException.class,()->api.activate(wrong,credential,uuid,"A").get());assertEquals(5,requests.size());
        }finally{server.stop(0);}
    }
    @Test void offlineErrorsRemainSpecificAndSessionHeadersCannotEscapeTheirEndpoints(){
        var http=new CloudHttpClient(CloudInstanceConfig.v1("test",URI.create("https://cloud.test")),"access-token");
        assertThrows(IllegalArgumentException.class,()->http.visualJson("PUT","/v1/players/me/appearance","{}",1024,SECRET));
    }
}
