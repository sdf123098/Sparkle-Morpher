package com.micaftic.morpher.cloud.client;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.UUID;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import static org.junit.jupiter.api.Assertions.*;
class CloudPlayerDiscoveryEntryTest {
    @Test void keepsPublisherOnEmptyAppearanceAndRejectsAmbiguousModelContent(){
        var api=new CloudPlayerPresenceClient(new CloudHttpClient(CloudInstanceConfig.v1("test",URI.create("https://cloud.test"))));UUID uuid=UUID.randomUUID();
        var result=api.parseDetailed("{\"entries\":[{\"entity_uuid\":\""+uuid+"\",\"revision\":1,\"selection\":null,\"publisher_id\":\"cloud-offline:publisher:2\",\"identity_kind\":\"CLOUD_OFFLINE\",\"discovery_status\":\"ACTIVE\"}]}");
        assertEquals("cloud-offline:publisher:2",result.get(uuid).publisherId());assertNull(result.get(uuid).selection());
        assertThrows(IllegalArgumentException.class,()->api.parseDetailed("{\"entries\":[{\"entity_uuid\":\""+uuid+"\",\"revision\":-1,\"selection\":null}]}"));
    }
    @Test void sourceChangeInvalidatesOldDownloadEvenWhenModelContentIsIdentical(){
        var state=new CloudPlayerAppearanceState();UUID uuid=UUID.randomUUID();var old=state.publish(uuid,null);state.remove(uuid);var next=state.publish(uuid,null);
        assertFalse(state.isCurrent(old));assertTrue(state.isCurrent(next));
    }
    @Test void aNewPublisherCanApplyRevisionOneAfterThePriorPublisherRevision999(){
        UUID uuid=UUID.randomUUID();var sources=new CloudPlayerDiscoveryEntry.Sources();var motion=new CloudPlayerMotionLedger();var imports=new CloudPlayerAppearanceState();
        Runnable clear=()->{motion.remove(uuid);imports.remove(uuid);};
        sources.accept(uuid,"verified:old",clear);assertTrue(motion.receive(uuid,999,null));var late=imports.publish(uuid,null);
        sources.accept(uuid,"cloud-offline:new:1",clear);assertTrue(motion.receive(uuid,1,null));assertFalse(imports.isCurrent(late));
        sources.accept(uuid,"cloud-offline:new:1",clear);assertFalse(motion.receive(uuid,1,null));
    }
}
