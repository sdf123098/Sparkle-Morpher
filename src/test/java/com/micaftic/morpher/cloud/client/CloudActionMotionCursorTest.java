package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CloudActionMotionCursorTest {
    @Test void heartbeatsVariableEditsRepeatedClipsStopsAndReentryHaveDistinctPlaybackSemantics() {
        var cursor = new CloudActionMotionCursor(); var source = new CloudPlayerMotionState();
        source.play("wave", 1000); var first = source.snapshot();
        assertTrue(cursor.update(first));
        assertFalse(cursor.update(first));
        source.roaming(Map.of("pose", 3f)); assertFalse(cursor.update(source.snapshot()));
        source.controller("idle", "waiting", 1100, Map.of("choice", 2f)); assertFalse(cursor.update(source.snapshot()));
        source.play("wave", 1200); assertTrue(cursor.update(source.snapshot()));
        assertFalse(cursor.update(source.snapshot()));
        source.stop(1300); assertTrue(cursor.update(source.snapshot()));
        assertFalse(cursor.update(source.snapshot()));
        assertFalse(cursor.update(null));
        assertTrue(cursor.update(source.snapshot()));
    }
}
