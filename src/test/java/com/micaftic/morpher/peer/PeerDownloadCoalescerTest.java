package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PeerDownloadCoalescerTest {
    @Test void controlBurstIsBoundedAndRefillsForNormalSnapshotPolling(){
        var commands=new PeerControlBudget(0);for(int i=0;i<256;i++)assertTrue(commands.admit(0,0));assertFalse(commands.admit(0,0));
        assertTrue(commands.admit(0,250_000_000L));for(int i=0;i<30;i++)assertTrue(commands.admit(0,250_000_000L));
        assertFalse(commands.admit(65536,250_000_000L));assertTrue(commands.admit(65536,500_000_000L));
    }
    @Test void byteJobIsSharedButWaiterCancellationAndProfileRemainIndependent()throws Exception {
        var root=PeerContentRoot.digest(new byte[]{1});var current=new AtomicBoolean(true);
        try(var jobs=new PeerDownloadCoalescer();var first=jobs.join(new PeerModelIdentity(root,"ysm-v1"),current::get);var second=jobs.join(new PeerModelIdentity(root,"ysm-v1"),()->true);var other=jobs.join(new PeerModelIdentity(root,"ysm-zip-v1"),()->true)) {
            assertTrue(first.leader());assertFalse(second.leader());assertTrue(other.leader());assertSame(first.result(),second.result());assertNotSame(first.result(),other.result());assertEquals(2,jobs.activeJobs());
            current.set(false);assertTrue(first.current());second.close();assertFalse(first.current());first.fail(new IOException("revoked"));assertTrue(first.result().isCompletedExceptionally());assertEquals(1,jobs.activeJobs());
        }
    }
    @Test void boundedColdGroupsFailExplicitlyAndCloseCompletesWaiters()throws Exception {
        var jobs=new PeerDownloadCoalescer();PeerDownloadCoalescer.Ticket last=null;
        for(int i=0;i<10;i++)last=jobs.join(new PeerModelIdentity(PeerContentRoot.digest(new byte[]{(byte)i}),"ysm-v1"),()->true);
        assertThrows(PeerFailure.class,()->jobs.join(new PeerModelIdentity(PeerContentRoot.digest(new byte[]{11}),"ysm-v1"),()->true));jobs.close();assertEquals(0,jobs.activeJobs());assertTrue(last.result().isCompletedExceptionally());
    }
}
