package com.micaftic.morpher.client.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CoreMotionSnapshotTest {
    @Test void pinnedHeadQueryAxesKeepYawUnderXAndPitchUnderY() {
        var head = new CoreHeadSnapshot(35, -12);
        assertEquals(35, head.queryHeadXRotation());
        assertEquals(-12, head.queryHeadYRotation());
    }
    @Test void verticalRateUsesSampledPositionDeltaAndElapsedTimeAndFreezesOneSlot() {
        var history = new CoreMotionSnapshot();
        assertEquals(0, history.sample(100, false, 0, 2, 0).verticalSpeed());
        var next = history.sample(50_000_100, false, 1, 3, 0);
        assertEquals(20, next.verticalSpeed());
        assertEquals(1, next.dy());
        assertSame(next, history.sample(50_000_101, false, 1, 300, 0));
        assertEquals(10, history.sample(150_000_100, false, 2, 4, 0).verticalSpeed());
    }
    @Test void pauseExcludesWallTimeAndNewOwnerDoesNotInheritOldPosition() {
        var history = new CoreMotionSnapshot();
        history.sample(100, false, 0, 0, 0);
        history.sample(50_000_100, false, 0, 1, 0);
        assertEquals(0, history.sample(100_000_100, true, 0, 1, 0).verticalSpeed());
        history.sample(9_000_000_100L, true, 0, 1, 0);
        history.sample(10_000_000_100L, false, 0, 1, 0);
        assertEquals(20, history.sample(10_050_000_100L, false, 0, 2, 0).verticalSpeed());
        assertEquals(0, new CoreMotionSnapshot().sample(20_000_000_100L, false, 0, 500, 0).verticalSpeed());
    }
}
