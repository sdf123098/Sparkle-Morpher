package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.*;

class CloudProjectileSnapshotClientTest {
    private static final CloudInstanceConfig INSTANCE=CloudInstanceConfig.v1("test",URI.create("https://cloud.example"));
    private static CloudProjectileSnapshotClient client(HttpClient http,int variables){
        return new CloudProjectileSnapshotClient(new CloudHttpClient(INSTANCE,http,"test-token"),
            new CloudInstanceInfo(INSTANCE,65536,16777216,262144,134217728,128,15,45,false,null,64,Set.of("projectile_snapshot_v1"),8192,variables));
    }
    private static JsonObject fixture(){return JsonParser.parseString("""
        {"snapshot":{"world_epoch":"world-reset-3","dimension_id":"minecraft:overworld","entity_uuid":"11111111-1111-4111-8111-111111111111",
         "entity_kind":"minecraft:arrow","source_identity_id":"identity-1","source_entity_uuid":"22222222-2222-4222-8222-222222222222","event_id":"fire-1",
         "resource":{"asset_id":"model-arrow","asset_revision":1,"raw_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","texture_id":"default","format":"ysm"},
         "projectile_bundle_key":"minecraft:arrow","variables":{"variable.pose":1},"firing_item_id":"minecraft:bow"},
         "lease_revision":1,"received_at_unix_ms":1000,"server_time_unix_ms":1000,"expires_at_unix_ms":601000,"absolute_expires_at_unix_ms":86401000}
        """).getAsJsonObject();}
    @Test void keeps_exact_firing_resource_equipment_and_bounded_monotonic_deadline(){
        try(var http=HttpClient.newHttpClient()){
            var lease=client(http,32).parseLease(fixture().toString());
            assertEquals("minecraft:bow",lease.snapshot().firingItemId());assertEquals(1,lease.snapshot().selection().ref().revision());
            assertEquals(600000000007L,lease.deadlineNanos(7));
            var unknown=fixture();unknown.getAsJsonObject("snapshot").add("firing_item_id",JsonNull.INSTANCE);
            assertNull(client(http,32).parseLease(unknown.toString()).snapshot().firingItemId());
            var expired=fixture();expired.addProperty("expires_at_unix_ms",1000);assertEquals(7,client(http,32).parseLease(expired.toString()).deadlineNanos(7));
            var configured=fixture();configured.addProperty("expires_at_unix_ms",3601000);assertEquals(3600000000007L,client(http,32).parseLease(configured.toString()).deadlineNanos(7));
        }
    }
    @Test void rejects_coercion_extra_fields_missing_unknown_marker_and_overextended_leases(){
        try(var http=HttpClient.newHttpClient()){
            var api=client(http,32);
            var coercion=fixture();coercion.getAsJsonObject("snapshot").addProperty("firing_item_id",42);assertThrows(IllegalArgumentException.class,()->api.parseLease(coercion.toString()));
            var unknown=fixture();unknown.getAsJsonObject("snapshot").addProperty("owner_current_hand","minecraft:bow");assertThrows(IllegalArgumentException.class,()->api.parseLease(unknown.toString()));
            var missing=fixture();missing.getAsJsonObject("snapshot").remove("firing_item_id");assertThrows(IllegalArgumentException.class,()->api.parseLease(missing.toString()));
            var fractional=fixture();fractional.addProperty("lease_revision",1.5);assertThrows(ArithmeticException.class,()->api.parseLease(fractional.toString()));
            var longIdle=fixture();longIdle.addProperty("expires_at_unix_ms",86401001);longIdle.addProperty("absolute_expires_at_unix_ms",604801000);assertThrows(IllegalArgumentException.class,()->api.parseLease(longIdle.toString()));
            var longAbsolute=fixture();longAbsolute.addProperty("absolute_expires_at_unix_ms",604801001);assertThrows(IllegalArgumentException.class,()->api.parseLease(longAbsolute.toString()));
            assertThrows(IllegalArgumentException.class,()->client(http,0).parseLease(fixture().toString()));
            var infinite=fixture();infinite.getAsJsonObject("snapshot").getAsJsonObject("variables").addProperty("bad",1e100);assertThrows(IllegalArgumentException.class,()->api.parseLease(infinite.toString()));
        }
    }
    @Test void native_uuid_query_and_frozen_publish_renew_withdraw_use_authenticated_scoped_routes() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var requests=new CopyOnWriteArrayList<String[]>();
        var returned=new AtomicReference<>(fixture());
        server.createContext("/v1/scopes/scope-1/projectiles",exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            requests.add(new String[]{exchange.getRequestMethod(),exchange.getRequestURI().getPath(),exchange.getRequestHeaders().getFirst("Authorization"),body});
            var receipt=returned.get().deepCopy();String response;
            if(exchange.getRequestURI().getPath().endsWith("/query"))response="{\"entries\":["+receipt+"]}";
            else{
                boolean lease=exchange.getRequestURI().getPath().endsWith("/lease");
                receipt.addProperty("lease_revision",lease?JsonParser.parseString(body).getAsJsonObject().get("expected_revision").getAsLong()+1:1);
                if(exchange.getRequestMethod().equals("DELETE"))receipt.addProperty("expires_at_unix_ms",1000);
                response=receipt.toString();
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try(var delegate=HttpClient.newHttpClient()){
            var transport=new CloudPlayerPresenceClientTest.LoopbackClient(delegate,URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            var api=client(transport,32);String origin=CloudAssetCache.sha256(INSTANCE.origin().toString().getBytes(StandardCharsets.UTF_8));
            var context=new EntityDisplayContext("test",origin,"scope-1","world-reset-3","minecraft:overworld",1);
            var snapshot=api.parseLease(fixture().toString()).snapshot();
            var created=api.publish(context,snapshot).get(10,TimeUnit.SECONDS);
            assertEquals(snapshot,created.snapshot());
            assertEquals(created,api.query(context,List.of(snapshot.entityUuid())).get(10,TimeUnit.SECONDS).get(snapshot.entityUuid()));
            var renewed=api.renew(context,created).get(10,TimeUnit.SECONDS);assertEquals(2,renewed.revision());assertEquals(snapshot,renewed.snapshot());
            var withdrawn=api.withdraw(context,renewed).get(10,TimeUnit.SECONDS);assertEquals(3,withdrawn.revision());assertEquals(7,withdrawn.deadlineNanos(7));
            assertEquals(List.of("PUT","POST","PUT","DELETE"),requests.stream().map(r->r[0]).toList());
            for(var request:requests)assertEquals("Bearer test-token",request[2]);
            assertEquals("/v1/scopes/scope-1/projectiles/"+snapshot.entityUuid()+"/lease",requests.get(3)[1]);
            assertEquals("minecraft:bow",JsonParser.parseString(requests.get(0)[3]).getAsJsonObject().get("firing_item_id").getAsString());
            var unexpected=fixture();unexpected.getAsJsonObject("snapshot").addProperty("entity_uuid",UUID.randomUUID().toString());returned.set(unexpected);
            assertThrows(CompletionException.class,()->api.query(context,List.of(snapshot.entityUuid())).join());
            var altered=fixture();altered.getAsJsonObject("snapshot").addProperty("firing_item_id","minecraft:crossbow");returned.set(altered);
            assertThrows(CompletionException.class,()->api.publish(context,snapshot).join());
            var wrongWorld=new EntityDisplayContext("test",origin,"scope-1","world-reset-4","minecraft:overworld",1);
            assertThrows(IllegalArgumentException.class,()->api.renew(wrongWorld,created));
        }finally{server.stop(0);}
    }
}
