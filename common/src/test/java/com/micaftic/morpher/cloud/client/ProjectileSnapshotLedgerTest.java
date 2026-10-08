package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSnapshotLedgerTest {
    private final UUID arrow=UUID.randomUUID(), owner=UUID.randomUUID();
    private final String sha="a".repeat(64);
    private EntityDisplayContext context(long generation) { return new EntityDisplayContext("instance",sha,"scope","world-3","minecraft:overworld",generation); }
    private ProjectileAppearanceSnapshot shot(String texture) {
        return new ProjectileAppearanceSnapshot("world-3","minecraft:overworld",arrow,"minecraft:arrow","identity",owner,"shot-1",
                new CloudPlayerSelection("instance",sha,new CloudAssetRef("asset",1,sha),"ysm",texture),"minecraft:arrow",Map.of("variant",2f),null);
    }
    @Test void ownerAndTypeMustComeFromTheLoadedEntityAndOldWorldRepliesAreRejected() {
        var ledger=new ProjectileSnapshotLedger(2);var context=context(1);ledger.enter(context);
        assertFalse(ledger.accept(context,shot("default"),1,100,0));
        ledger.observe(arrow,new ProjectileSnapshotLedger.Observation("minecraft:arrow",UUID.randomUUID()));
        assertFalse(ledger.accept(context,shot("default"),1,100,0));
        ledger.observe(arrow,new ProjectileSnapshotLedger.Observation("minecraft:arrow",owner));
        assertTrue(ledger.accept(context,shot("default"),1,100,0));
        ledger.enter(context(2));assertEquals(0,ledger.size());
        ledger.observe(arrow,new ProjectileSnapshotLedger.Observation("minecraft:arrow",owner));
        assertFalse(ledger.accept(context,shot("default"),1,100,0));
    }
    @Test void sourceChangingModelsCannotAmendTheShotAndRenewalNeedsAHigherRevision() {
        var ledger=new ProjectileSnapshotLedger(2);var context=context(1);ledger.enter(context);
        ledger.observe(arrow,new ProjectileSnapshotLedger.Observation("minecraft:arrow",owner));
        assertTrue(ledger.accept(context,shot("default"),1,100,0));
        assertFalse(ledger.accept(context,shot("other"),2,200,0));
        assertFalse(ledger.accept(context,shot("default"),1,200,0));
        assertTrue(ledger.accept(context,shot("default"),2,200,0));
        assertFalse(ledger.accept(context,shot("default"),1,100,0));
        assertNotNull(ledger.get(arrow,199));assertNull(ledger.get(arrow,200));
    }
    @Test void unloadingOnlyClearsThisClientAndStateIsBounded() {
        var ledger=new ProjectileSnapshotLedger(1);var context=context(1);ledger.enter(context);
        ledger.observe(arrow,new ProjectileSnapshotLedger.Observation("minecraft:arrow",owner));
        assertTrue(ledger.accept(context,shot("default"),1,100,0));
        ledger.unload(arrow);assertEquals(0,ledger.size());
        assertFalse(ledger.accept(context,shot("default"),1,100,0));
        var unknown=shot("default");assertNull(unknown.firingItemId());
        assertThrows(UnsupportedOperationException.class,()->unknown.variables().put("late",3f));
    }
}
