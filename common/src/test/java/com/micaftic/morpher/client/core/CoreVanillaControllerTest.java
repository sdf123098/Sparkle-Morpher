package com.micaftic.morpher.client.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class CoreVanillaControllerTest {
    @Test void sourcePriorityProducesExactlyOneMainCondition() {
        var snapshot = CoreVanillaController.snapshot(Set.of("run", "walk", "sneak", "sneaking", "jump"), false, false, false);
        assertTrue(snapshot.get("ctrl.jump").truth());
        assertEquals(1, snapshot.values().stream().filter(value -> value.truth()).count());
        assertTrue(CoreVanillaController.snapshot(Set.of(), false, false, false).get("ctrl.idle").truth());
        assertTrue(CoreVanillaController.snapshot(Set.of("death", "riptide", "swim"), false, false, false).get("ctrl.death").truth());
    }
    @Test void previewAndLivingVehicleSuppressAllConditionsAndUnbridgedParCoolRejects() {
        assertTrue(CoreVanillaController.snapshot(Set.of("death"), true, false, false).values().stream().noneMatch(value -> value.truth()));
        assertTrue(CoreVanillaController.snapshot(Set.of("death"), true, false, true).values().stream().noneMatch(value -> value.truth()));
        assertTrue(CoreVanillaController.snapshot(Set.of("walk"), false, true, false).values().stream().noneMatch(value -> value.truth()));
        assertThrows(IllegalStateException.class, () -> CoreVanillaController.snapshot(Set.of(), false, false, true));
    }
}
