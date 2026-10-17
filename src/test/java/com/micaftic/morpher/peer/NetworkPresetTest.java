package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.network.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NetworkPresetTest {
    @AfterEach void reset() { NetworkAccess.update(NetworkPreset.AUTO,false); }
    @Test void legacyConfigPreservesAutomaticLanAndCloud() {
        for(String value : new String[] {null,"","bad"}) { var preset=NetworkPreset.fromConfig(value); assertEquals(NetworkPreset.AUTO,preset); assertTrue(preset.allowsPeer(false)); assertTrue(preset.allowsCloud(false)); }
    }
    @Test void prioritiesUseReadyCandidatesAndPrivacyWins() {
        assertEquals(NetworkPreset.Source.CLOUD,NetworkPreset.CLOUD_FIRST.select(true,true,false));
        assertEquals(NetworkPreset.Source.PEER,NetworkPreset.CLOUD_FIRST.select(true,false,false));
        assertEquals(NetworkPreset.Source.PEER,NetworkPreset.PEER_FIRST.select(true,true,false));
        assertEquals(NetworkPreset.Source.CLOUD,NetworkPreset.PEER_FIRST.select(false,true,false));
        for(var preset:NetworkPreset.values()) assertEquals(NetworkPreset.Source.LOCAL,preset.select(true,true,true));
        assertEquals(NetworkPreset.Source.LOCAL,NetworkPreset.PEER_ONLY.select(false,true,false));
        assertEquals(NetworkPreset.Source.LOCAL,NetworkPreset.CLOUD_ONLY.select(true,false,false));
    }
    @Test void purePeerDoesNotInvokeCloudTransportAndSwitchCancelsLateResult() {
        var calls=new AtomicInteger();NetworkAccess.update(NetworkPreset.PEER_ONLY,false);
        var blocked=NetworkAccess.cloud(()->{calls.incrementAndGet();return CompletableFuture.completedFuture("bad");});
        assertThrows(CompletionException.class,blocked::join);assertEquals(0,calls.get());
        NetworkAccess.update(NetworkPreset.AUTO,false);var upstream=new CompletableFuture<String>();
        var pending=NetworkAccess.cloud(()->upstream);NetworkAccess.update(NetworkPreset.LOCAL_ONLY,false);
        assertTrue(upstream.isCancelled());assertTrue(pending.isCompletedExceptionally());
        NetworkAccess.update(NetworkPreset.AUTO,false);assertEquals("new",NetworkAccess.cloud(()->CompletableFuture.completedFuture("new")).join());
    }
}
