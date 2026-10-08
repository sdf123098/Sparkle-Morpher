package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CloudPlayerMotionTest {
    @Test void preservesUnicodeActionSettingsExpressionAndIdleChoice() {
        var state = new CloudPlayerMotionState();
        state.play("坐", 1000);
        state.roaming(Map.of("idle", 1f, "bq", 4f));
        state.expression("", List.of(2f, 3f), 1100);
        state.controller("player.post_main", "长时间待机", 1200, Map.of("idle_random2", 1f));
        var original = state.snapshot();
        assertEquals(original, CloudPlayerMotion.fromJson(original.toJson()));
        assertEquals(1f, original.controllers().get("player.post_main").variables().get("idle_random2"));
        assertEquals("坐", original.animationKey());
    }
    @Test void sameActionCanRestartAndStopWithoutReplayingHeartbeat() {
        var state = new CloudPlayerMotionState();
        state.play("坐", 1000); var first = state.snapshot();
        assertSame(first, state.snapshot());
        state.play("坐", 1100); var second = state.snapshot();
        assertNotEquals(first.eventId(), second.eventId());
        state.stop(1200);
        assertEquals("", state.snapshot().animationKey());
        assertEquals(1200, state.snapshot().startedAtUnixMs());
    }
    @Test void controllerHeartbeatKeepsTheOwnersStartTimeAndRandomChoice() {
        var state = new CloudPlayerMotionState();
        state.controller("player.post_main", "长时间待机", 1200, Map.of("idle_random2", 1f));
        var first = state.snapshot();
        state.controller("player.post_main", "长时间待机", 1200, Map.of("idle_random2", 1f));
        assertSame(first, state.snapshot());
        assertEquals(1200, first.controllers().get("player.post_main").startedAtUnixMs());
    }
    @Test void editingAnObservedTimelinePreservesItsClockAndOwnsAnIndependentSnapshot() {
        var publisher = new CloudPlayerMotionState();
        publisher.play("wave", 1000);
        publisher.controller("idle", "roaming", 1050, Map.of("choice", 2f));
        var observed = publisher.snapshot();
        var editor = new CloudPlayerMotionState(); editor.adopt(observed);
        editor.roaming(Map.of("pose", 3f));
        assertEquals(observed.eventId(), editor.snapshot().eventId());
        assertEquals(1000, editor.snapshot().startedAtUnixMs());
        assertEquals(1050, editor.snapshot().controllers().get("idle").startedAtUnixMs());
        assertTrue(publisher.snapshot().roaming().isEmpty());
        editor.play("wave", 1200);
        assertNotEquals(observed.eventId(), editor.snapshot().eventId());
        assertEquals(3f, editor.snapshot().roaming().get("pose"));
    }
    @Test void separatesPlayersAndRejectsStaleOrReplayedMotion() {
        var ledger = new CloudPlayerMotionLedger();
        var a = new CloudPlayerMotionState(); var b = new CloudPlayerMotionState();
        a.play("你好", 1000); b.play("坐", 1100);
        var uuidA = java.util.UUID.randomUUID(); var uuidB = java.util.UUID.randomUUID();
        assertTrue(ledger.receive(uuidA, 2, a.snapshot()));
        assertTrue(ledger.receive(uuidB, 1, b.snapshot()));
        assertFalse(ledger.receive(uuidA, 1, b.snapshot()));
        assertEquals("你好", ledger.get(uuidA).animationKey());
        assertEquals("坐", ledger.get(uuidB).animationKey());
        ledger.clear(); assertNull(ledger.get(uuidA));
    }
    @Test void rejectsUnboundedPayloadAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new CloudPlayerMotionState().roaming(Map.of("x", Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> new CloudPlayerMotionState().play("a".repeat(257), 0));
        assertThrows(IllegalArgumentException.class, () -> CloudPlayerMotion.fromJson(JsonParser.parseString(
                "{\"event_id\":\"x\",\"animation_key\":\"\",\"started_at_unix_ms\":-1}")));
    }
}
