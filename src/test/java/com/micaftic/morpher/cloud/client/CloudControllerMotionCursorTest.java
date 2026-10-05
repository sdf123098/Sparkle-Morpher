package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CloudControllerMotionCursorTest {
    @Test void idleEntryUsesOwnerClockAndChoiceWithoutRestartingAtHeartbeat() {
        var cursor = new CloudControllerMotionCursor();
        var entry = new CloudPlayerMotion.Controller("长时间待机", 1200, Map.of("idle_random2", 1f));
        assertTrue(cursor.needsTransition(entry));
        assertTrue(cursor.needsVariables(entry));
        cursor.accept(entry);
        var heartbeat = new CloudPlayerMotion.Controller("长时间待机", 1200, Map.of("idle_random2", 1f));
        assertFalse(cursor.needsTransition(heartbeat));
        assertFalse(cursor.needsVariables(heartbeat));
        var choice = new CloudPlayerMotion.Controller("长时间待机", 1200, Map.of("idle_random2", 0f));
        assertFalse(cursor.needsTransition(choice));
        assertTrue(cursor.needsVariables(choice));
        var reentry = new CloudPlayerMotion.Controller("长时间待机", 1500, Map.of("idle_random2", 1f));
        assertTrue(cursor.needsTransition(reentry));
        cursor.clear(); assertTrue(cursor.needsTransition(entry));
    }
    @Test void latestControllerEntrySharesOneCoherentVariableSnapshot() {
        var state = new CloudPlayerMotionState();
        state.controller("player.post_main", "待机", 1000, Map.of("idle_random2", 1f));
        state.controller("player.face", "表情", 1100, Map.of("idle_random2", 0f));
        assertEquals(0f, state.snapshot().controllers().get("player.post_main").variables().get("idle_random2"));
        assertEquals(1000, state.snapshot().controllers().get("player.post_main").startedAtUnixMs());
    }
}
