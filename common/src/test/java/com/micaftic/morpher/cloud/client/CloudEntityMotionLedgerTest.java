package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityMotionLedgerTest {
    private final UUID id=UUID.randomUUID();
    private final CloudPlayerSelection model=new CloudPlayerSelection("test","a".repeat(64),new CloudAssetRef("model",1,"b".repeat(64)),"ysm","default");
    private EntityDisplayContext context(long generation){return new EntityDisplayContext("test","a".repeat(64),"scope","world","minecraft:overworld",generation);}
    private CloudEntityMotionClient.Entry entry(long revision,String action){return new CloudEntityMotionClient.Entry(id,
        new CloudEntityMotion("world","minecraft:overworld","MAID","target",2,3,new CloudPlayerMotion(action,"wave",1000,Map.of(),List.of(),Map.of())),model,revision,1000,61000);}
    private CloudEntityMotionLedger.Observation observed(long binding){return new CloudEntityMotionLedger.Observation(id,"MAID","target",binding,3,model.runtimeModelId());}
    @Test void rejects_old_revisions_changed_same_revision_and_context_or_binding_mismatches(){
        var ledger=new CloudEntityMotionLedger();var context=context(1);ledger.reset(context);
        assertTrue(ledger.receive(context,observed(2),entry(2,"play-2"),10));
        assertFalse(ledger.receive(context,observed(2),entry(1,"play-1"),20));
        assertFalse(ledger.receive(context,observed(2),entry(2,"other-event"),20));
        assertFalse(ledger.receive(context(2),observed(2),entry(3,"play-3"),20));
        assertFalse(ledger.receive(context,observed(4),entry(3,"play-3"),20));
        assertEquals("play-2",ledger.get(context,observed(2),30).update().motion().eventId());
    }
    @Test void duplicate_reads_do_not_extend_lease_and_unload_discards_state(){
        var ledger=new CloudEntityMotionLedger();var context=context(1);ledger.reset(context);
        assertTrue(ledger.receive(context,observed(2),entry(1,"play"),10));
        assertTrue(ledger.receive(context,observed(2),entry(1,"play"),1000000000));
        assertNull(ledger.get(context,observed(2),60000000010L));
        assertTrue(ledger.receive(context,observed(2),entry(2,"stop"),20));ledger.retain(Set.of());
        assertNull(ledger.get(context,observed(2),30));
    }
}
