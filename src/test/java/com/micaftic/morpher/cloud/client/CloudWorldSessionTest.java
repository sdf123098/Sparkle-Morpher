package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudWorldSessionTest {
    @Test
    void worldGenerationTracksTheActiveConnectionAndRejectsStaleDisconnects() {
        CloudWorldSession session = new CloudWorldSession();
        Object firstConnection = new Object();
        Object secondConnection = new Object();

        long firstGeneration = session.enter(firstConnection);
        assertTrue(session.isActive());
        assertTrue(session.isCurrent(firstGeneration));
        assertTrue(session.leave(firstConnection));
        assertFalse(session.isCurrent(firstGeneration));

        long secondGeneration = session.enter(secondConnection);
        assertTrue(secondGeneration > firstGeneration);
        assertFalse(session.leave(firstConnection));
        assertTrue(session.isCurrent(secondGeneration));
        assertTrue(session.leaveCurrent());
        assertFalse(session.isActive());
    }

    @Test
    void repeatedJoinOfSameConnectionIsIdempotentAndNoWorldCannotJoinScope() {
        CloudWorldSession session = new CloudWorldSession();
        Object connection = new Object();

        long first = session.enter(connection);
        assertTrue(session.enter(connection) == first);
        assertTrue(session.currentGeneration() == first);
        session.leave(connection);
        assertThrows(IllegalStateException.class, session::currentGeneration);
    }
}
