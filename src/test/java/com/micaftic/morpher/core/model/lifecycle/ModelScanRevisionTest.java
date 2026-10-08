package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelScanRevisionTest {
    @Test
    void startingANewerScanMakesTheOlderCandidateStale() {
        ModelScanRevision revisions = new ModelScanRevision();

        long slowScan = revisions.begin();
        long fastScan = revisions.begin();

        assertFalse(revisions.isCurrent(slowScan));
        assertTrue(revisions.isCurrent(fastScan));
    }

    @Test
    void invalidatingSourceStateMakesAnInFlightScanStale() {
        ModelScanRevision revisions = new ModelScanRevision();
        long scan = revisions.begin();

        revisions.invalidate();

        assertFalse(revisions.isCurrent(scan));
    }
}
