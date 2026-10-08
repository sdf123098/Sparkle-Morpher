package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCommitFlowTest {

    @Test
    void commitFailureDoesNotPublishCandidate() {
        AtomicInteger publishCalls = new AtomicInteger();
        IOException failure = new IOException("injected persistence failure");

        ImportCommitFlow.Outcome<Path> outcome = ImportCommitFlow.commitThenPublish(
                () -> { throw failure; },
                ignored -> publishCalls.incrementAndGet());

        assertEquals(ImportCommitFlow.State.FAILED_BEFORE_COMMIT, outcome.state());
        assertFalse(outcome.sourceCommitted());
        assertSame(failure, outcome.failure());
        assertEquals(0, publishCalls.get());
    }

    @Test
    void publicationFailureRetainsCommittedSourceForRecovery() {
        Path committed = Path.of("custom", "cirno.glb");
        AtomicInteger publishCalls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("injected runtime publication failure");

        ImportCommitFlow.Outcome<Path> outcome = ImportCommitFlow.commitThenPublish(
                () -> committed,
                ignored -> {
                    publishCalls.incrementAndGet();
                    throw failure;
                });

        assertEquals(ImportCommitFlow.State.SOURCE_COMMITTED_PENDING_PUBLICATION, outcome.state());
        assertTrue(outcome.sourceCommitted());
        assertEquals(committed, outcome.committedSource());
        assertSame(failure, outcome.failure());
        assertEquals(1, publishCalls.get(), "publication is attempted once; retry belongs to recovery flow");
    }

    @Test
    void successfulFlowPublishesExactlyOnce() {
        Path committed = Path.of("custom", "cirno.ysm");
        AtomicInteger publishCalls = new AtomicInteger();

        ImportCommitFlow.Outcome<Path> outcome = ImportCommitFlow.commitThenPublish(
                () -> committed,
                ignored -> publishCalls.incrementAndGet());

        assertEquals(ImportCommitFlow.State.PUBLISHED, outcome.state());
        assertEquals(committed, outcome.committedSource());
        assertTrue(outcome.sourceCommitted());
        assertEquals(1, publishCalls.get());
    }
}
