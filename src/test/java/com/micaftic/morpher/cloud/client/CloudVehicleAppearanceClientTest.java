package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CloudVehicleAppearanceClientTest {
    private static final CloudInstanceConfig INSTANCE=CloudInstanceConfig.v1("test",URI.create("https://cloud.example"));
    private static JsonObject fixture(){return JsonParser.parseString("""
        {"entity_uuid":"11111111-1111-4111-8111-111111111111","revision":1,
         "server_time_unix_ms":1000,"expires_at_unix_ms":61000,
         "binding":{"world_epoch":"world-reset-3","dimension_id":"minecraft:overworld",
          "entity_kind":"minecraft:horse","target_id":"target-horse","expected_revision":0,
          "resource":{"asset_id":"model-arrow","asset_revision":1,
           "raw_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
           "texture_id":"default","format":"ysm"},"variables":{"seat_style":1}}}
        """).getAsJsonObject();}
    private static CloudVehicleAppearanceClient client(HttpClient http,long vars){
        var info=new CloudInstanceInfo(INSTANCE,65536,16777216,262144,134217728,128,15,45,false,null,64,Set.of("vehicle_appearance_v1"),8192,vars);
        return new CloudVehicleAppearanceClient(new CloudHttpClient(INSTANCE,http,"test-token"),info);
    }
    @Test void parses_exact_binding_and_preserves_unknown_unbound_state(){
        try(var http=HttpClient.newHttpClient()){
            var api=client(http,32);var row=fixture();var binding=api.parse(row);
            assertEquals(1,binding.selection().ref().revision());assertEquals("minecraft:horse",binding.entityKind());
            assertEquals(1f,binding.variables().get("seat_style"));assertEquals(60000000007L,binding.deadlineNanos(7));
            row.getAsJsonObject("binding").add("resource",JsonNull.INSTANCE);row.getAsJsonObject("binding").add("variables",new JsonObject());
            assertNull(api.parse(row).selection());
            row.getAsJsonObject("binding").getAsJsonObject("variables").addProperty("bad",1);
            assertThrows(IllegalArgumentException.class,()->api.parse(row));
        }
    }
    @Test void rejects_coerced_revision_unbounded_lease_and_negotiated_variable_overflow(){
        try(var http=HttpClient.newHttpClient()){
            var api=client(http,32);var coerced=fixture();coerced.addProperty("revision","1");assertThrows(IllegalArgumentException.class,()->api.parse(coerced));
            var late=fixture();late.addProperty("expires_at_unix_ms",61001);assertThrows(IllegalArgumentException.class,()->api.parse(late));
            assertThrows(IllegalArgumentException.class,()->client(http,0).parse(fixture()));
            var context=new EntityDisplayContext("test","b".repeat(64),"test-scope","world-reset-3","minecraft:overworld",1);
            assertThrows(IllegalArgumentException.class,()->api.query(context,java.util.List.of()));
        }
    }
    @Test void same_revision_acknowledgements_cannot_change_bound_content(){
        try(var http=HttpClient.newHttpClient()){
            var api=client(http,32);var row=fixture();var original=api.parse(row);
            row.addProperty("server_time_unix_ms",2000);row.addProperty("expires_at_unix_ms",62000);
            assertTrue(original.sameBinding(api.parse(row)));
            row.getAsJsonObject("binding").getAsJsonObject("variables").addProperty("seat_style",2);
            assertFalse(original.sameBinding(api.parse(row)));
            row.getAsJsonObject("binding").getAsJsonObject("variables").addProperty("seat_style",1);
            row.getAsJsonObject("binding").addProperty("target_id","other-horse");
            assertFalse(original.sameBinding(api.parse(row)));
        }
    }
}
