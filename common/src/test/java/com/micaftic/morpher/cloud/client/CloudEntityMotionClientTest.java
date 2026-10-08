package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityMotionClientTest {
    private static final CloudInstanceConfig INSTANCE=CloudInstanceConfig.v1("test",URI.create("https://cloud.example"));
    private static JsonObject fixture(){return JsonParser.parseString("""
        {"entity_uuid":"11111111-1111-4111-8111-111111111111","revision":1,"server_time_unix_ms":1000,"expires_at_unix_ms":61000,
         "resource":{"asset_id":"model-arrow","asset_revision":1,"raw_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","texture_id":"default","format":"ysm"},
         "update":{"world_epoch":"world-reset-3","dimension_id":"minecraft:overworld","entity_kind":"MAID","target_id":"target-maid","binding_revision":1,"appearance_revision":1,"expected_revision":0,
         "motion":{"event_id":"play-1","animation_key":"wave","started_at_unix_ms":1000,"roaming":{"variable.pose":1},"expressions":[],"controllers":{"idle":{"state":"idle","started_at_unix_ms":1000,"variables":{}}}}}}
        """).getAsJsonObject();}
    private static CloudEntityMotionClient client(HttpClient http,int variables){
        var info=new CloudInstanceInfo(INSTANCE,65536,16777216,262144,134217728,128,15,45,false,null,64,Set.of("entity_motion_v1"),8192,variables);
        return new CloudEntityMotionClient(new CloudHttpClient(INSTANCE,http,"test-token"),info);
    }
    @Test void reads_exact_resource_binding_and_stable_event_time(){
        try(var http=HttpClient.newHttpClient()){
            var entry=client(http,32).parse(fixture());
            assertEquals("play-1",entry.update().motion().eventId());assertEquals(1000,entry.update().motion().startedAtUnixMs());
            assertEquals("target-maid",entry.update().targetId());assertEquals(1,entry.selection().ref().revision());
            assertEquals(60000000007L,entry.deadlineNanos(7));
        }
    }
    @Test void rejects_coercion_unknown_fields_excess_variables_and_invalid_receipt_lifetime(){
        try(var http=HttpClient.newHttpClient()){
            var api=client(http,32);var coercion=fixture();coercion.getAsJsonObject("update").getAsJsonObject("motion").addProperty("started_at_unix_ms","1000");
            assertThrows(IllegalArgumentException.class,()->api.parse(coercion));
            var unknown=fixture();unknown.getAsJsonObject("update").getAsJsonObject("motion").addProperty("server_ai",true);
            assertThrows(IllegalArgumentException.class,()->api.parse(unknown));
            assertThrows(IllegalArgumentException.class,()->client(http,0).parse(fixture()));
            var stale=fixture();stale.addProperty("expires_at_unix_ms",61001);assertThrows(IllegalArgumentException.class,()->api.parse(stale));
            var infinite=fixture();infinite.getAsJsonObject("update").getAsJsonObject("motion").getAsJsonObject("roaming").addProperty("bad",1e100);
            assertThrows(IllegalArgumentException.class,()->api.parse(infinite));
        }
    }
}
