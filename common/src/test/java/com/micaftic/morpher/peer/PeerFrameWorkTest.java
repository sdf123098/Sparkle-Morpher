package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PeerFrameWorkTest {
    @Test void boundsDeduplicatesCancelsAndSpreadsUploadsWithoutBlocking() {
        var clock=new java.util.concurrent.atomic.AtomicLong();
        var work=new PeerFrameWork(4,2,100,10,clock::get);
        var count=new AtomicInteger(); Object first=new Object();
        assertTrue(work.enqueue(first,40,()->true,()->{count.incrementAndGet();clock.addAndGet(6);}));
        assertTrue(work.enqueue(first,40,()->true,()->fail("duplicate")));
        assertTrue(work.enqueue(new Object(),40,()->false,()->fail("cancelled")));
        assertTrue(work.enqueue(new Object(),40,()->true,()->{count.incrementAndGet();clock.addAndGet(6);}));
        assertTrue(work.enqueue(new Object(),40,()->true,count::incrementAndGet));
        assertFalse(work.enqueue(new Object(),1,()->true,count::incrementAndGet));
        work.frame();assertEquals(2,count.get());assertEquals(1,work.queued());
        work.frame();assertEquals(3,count.get());assertEquals(0,work.queued());
    }
    @Test void oversizedAtomicUploadMakesProgressAloneAndResetCancelsOldWork() {
        var work=new PeerFrameWork(4,2,100,10,()->0L);var count=new AtomicInteger();
        work.enqueue(new Object(),200,()->true,count::incrementAndGet);
        work.enqueue(new Object(),1,()->true,count::incrementAndGet);
        work.frame();assertEquals(1,count.get());assertEquals(1,work.queued());
        work.reset();work.frame();assertEquals(1,count.get());
    }
    @Test void failedUploadCannotStarveOtherOwnersAndColdGeometryIsOnePerFrame() {
        var work=new PeerFrameWork(8,2,100,10,()->0L);var count=new AtomicInteger();
        work.enqueue(new Object(),1,()->true,()->{throw new IllegalStateException("driver failure");});
        work.enqueue(new Object(),1,()->true,count::incrementAndGet);
        work.frame();assertEquals(1,count.get());assertEquals(1,work.failures());
        assertTrue(work.admitModel("peer:a"));assertTrue(work.admitModel("peer:a"));assertFalse(work.admitModel("peer:b"));
        work.frame();assertTrue(work.admitModel("peer:b"));assertTrue(work.admitModel("cloud:a"));
    }
    @Test void frameEvidenceRequiresCurrentSuccessfulFenceAndNeverClaimsPixels() {
        var evidence=new PeerFrameEvidence();long epoch=evidence.epoch();
        evidence.uploaded("peer:a");evidence.draw("owner","peer:a");
        var batch=evidence.seal(7);assertNotNull(batch);
        assertTrue(evidence.describe("owner","peer:a").contains("gpu_completed_frame=unobserved"));
        evidence.complete(batch,true);String done=evidence.describe("owner","peer:a");
        assertTrue(done.contains("gpu_completed_frame=7"));assertTrue(done.contains("screen_pixels=requires_visual_test"));
        evidence.reset();assertNotEquals(epoch,evidence.epoch());evidence.complete(batch,true);
        assertTrue(evidence.describe("owner","peer:a").contains("gpu_completed_frame=unobserved"));
    }
    @Test void trimAndFailureDoNotRetainSuccessfulGpuEvidence() {
        var evidence=new PeerFrameEvidence();evidence.uploaded("peer:a");evidence.draw("owner","peer:a");
        var batch=evidence.seal(1);evidence.complete(batch,false);
        assertTrue(evidence.describe("owner","peer:a").contains("fence_failed"));
        evidence.forget("peer:a");assertTrue(evidence.describe("owner","peer:a").contains("gpu_completed_frame=unobserved"));
        evidence.uploaded("peer:a");evidence.draw("owner","peer:a");var second=evidence.seal(2);evidence.forget("peer:a");
        evidence.uploaded("peer:a");evidence.complete(second,true);
        assertTrue(evidence.describe("owner","peer:a").contains("gpu_completed_frame=unobserved"));
    }
    @Test void rejoiningOwnerCannotBorrowItsPreviousEntityFrameOrClearAnotherOwner() {
        var evidence=new PeerFrameEvidence();evidence.uploaded("peer:a");
        evidence.draw("first","peer:a");evidence.draw("other","peer:a");var batch=evidence.seal(3);
        evidence.removeOwner("first");evidence.complete(batch,true,8);
        assertTrue(evidence.describe("first","peer:a").contains("gpu_completed_frame=unobserved"));
        assertTrue(evidence.describe("other","peer:a").contains("gpu_completed_frame=8"));
        assertTrue(evidence.describe("other","peer:a").contains("gpu_completed_submission_frame=3"));
        evidence.draw("first","peer:a");assertNotNull(evidence.seal(9));
    }
}
