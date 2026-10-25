package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FakePlayerRouletteProfileStoreTest {
    @TempDir Path root;
    private final UUID bot=UUID.fromString("12345678-1234-1234-1234-123456789012");
    private FakePlayerRouletteProfileStore.Key key(String account,String world,UUID id,String model) {
        return new FakePlayerRouletteProfileStore.Key("official","origin-hash",account,"scope",world,id,"target",model);
    }
    private FakePlayerRouletteProfileStore store() { return new FakePlayerRouletteProfileStore(root.resolve("fake_player_roulette.json")); }
    @Test void restartRestoresTogglesSlidersRadioVariablesAndChosenAction() throws Exception {
        var key=key("alice","world",bot,"cloud_model_a");
        var profile=FakePlayerRouletteProfileStore.Profile.empty()
            .withSetting("v.roaming.coat","v.roaming.coat=0",Map.of("coat",0f))
            .withSetting("v.roaming.size","v.roaming.size=0.75",Map.of("coat",0f,"size",.75f))
            .withSetting("v.roaming.style","v.roaming.style=2;v.roaming.hat=1;",Map.of("coat",0f,"size",.75f,"style",2f,"hat",1f))
            .withAction("dance",Map.of("coat",0f,"size",.75f,"style",2f,"hat",1f));
        store().save(key,profile);
        assertEquals(profile,store().load(key));
        assertEquals(List.of("v.roaming.coat=0","v.roaming.size=0.75","v.roaming.style=2;v.roaming.hat=1;"),new ArrayList<>(store().load(key).settings().values()));
        assertEquals(List.of("fake_player_roulette.json"),Files.list(root).map(p->p.getFileName().toString()).toList());
    }
    @Test void fakePlayersModelsAccountsWorldsInstancesOriginsScopesAndBindingsAreIsolated() throws Exception {
        var k=key("alice","world",bot,"model");
        store().save(k,FakePlayerRouletteProfileStore.Profile.empty().withAction("dance",Map.of()));
        var keys=List.of(key("bob","world",bot,"model"),key("alice","other",bot,"model"),
            key("alice","world",UUID.randomUUID(),"model"),key("alice","world",bot,"other"),
            new FakePlayerRouletteProfileStore.Key("other",k.origin(),k.account(),k.scope(),k.world(),bot,k.target(),k.model()),
            new FakePlayerRouletteProfileStore.Key(k.instance(),"other",k.account(),k.scope(),k.world(),bot,k.target(),k.model()),
            new FakePlayerRouletteProfileStore.Key(k.instance(),k.origin(),k.account(),"other",k.world(),bot,k.target(),k.model()),
            new FakePlayerRouletteProfileStore.Key(k.instance(),k.origin(),k.account(),k.scope(),k.world(),bot,"other",k.model()));
        for(var other:keys)assertNull(store().load(other));
    }
    @Test void stopIsPersistedAndLatestValueReplacesEarlierSliderValue() throws Exception {
        var key=key("alice","world",bot,"model");
        var profile=FakePlayerRouletteProfileStore.Profile.empty().withAction("dance",Map.of())
            .withSetting("v.size","v.size=1",Map.of()).withSetting("v.size","v.size=2",Map.of())
            .withAction("",Map.of("size",2f));
        store().save(key,profile);
        assertEquals("",store().load(key).action());
        assertEquals(Map.of("v.size","v.size=2"),store().load(key).settings());
        assertNull(FakePlayerRouletteProfileStore.Profile.empty().action());
    }
    @Test void rejectsUnboundedOrNonFiniteStateAndPreservesCorruptDocuments() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new FakePlayerRouletteProfileStore.Profile("",Map.of("bad",Float.NaN),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new FakePlayerRouletteProfileStore.Profile("x".repeat(257),Map.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new FakePlayerRouletteProfileStore.Profile("",Map.of(),Map.of("control","x".repeat(2049))));
        Path file=root.resolve("fake_player_roulette.json");Files.writeString(file,"broken");
        assertThrows(java.io.IOException.class,()->store().save(key("alice","world",bot,"model"),FakePlayerRouletteProfileStore.Profile.empty()));
        assertEquals("broken",Files.readString(file));
    }
    @Test void failedWriteDoesNotReplaceAnAcknowledgedInMemoryPreference() throws Exception {
        var store=store();var key=key("alice","world",bot,"model");
        var profile=FakePlayerRouletteProfileStore.Profile.empty().withAction("dance",Map.of());store.save(key,profile);
        Files.writeString(root.resolve("fake_player_roulette.json"),"broken");
        assertThrows(java.io.IOException.class,()->store.save(key,profile.withAction("",Map.of())));
        assertEquals(profile,store.load(key));
        assertEquals("broken",Files.readString(root.resolve("fake_player_roulette.json")));
    }
    @Test void restoreRunsOncePerCapabilityAndRetriesOnlyTransientFailures() {
        var gate=new FakePlayerRouletteRestoreGate();Object loaded=new Object(),respawned=new Object();
        assertTrue(gate.begin(loaded,0));assertFalse(gate.begin(loaded,1));
        assertTrue(gate.complete(loaded,false,false,2));assertFalse(gate.begin(loaded,4_000_000_000L));
        assertTrue(gate.begin(loaded,6_000_000_000L));assertTrue(gate.complete(loaded,true,false,6_000_000_001L));
        assertFalse(gate.begin(loaded,100_000_000_000L));assertTrue(gate.begin(respawned,100_000_000_000L));
        assertFalse(gate.complete(loaded,true,false,100_000_000_001L));
        assertTrue(gate.complete(respawned,false,true,100_000_000_002L));assertFalse(gate.begin(respawned,999_000_000_000L));
    }
}
