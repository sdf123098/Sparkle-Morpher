package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.SceneInputs;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual opt-in Host owner with an original, redistributable model. No Minecraft runtime. */
final class CoreHostFixtureTest {
    @Test void originalBedrockFunctionsAndEffectsReachPublishedHostChannels() throws Exception {
        Path model = Path.of(getClass().getResource("/core-host-fixture/model.geo.json").toURI());
        try (var owner = CoreSceneOwner.load(model, CoreSceneOwner.Lane.WORLD)) {
            var inputs = new SceneInputs(Map.of("ground_speed", 2.0));
            var first = owner.advance(100, false, inputs, call -> MolangValue.bool(true));
            var frame = owner.advance(50_000_100, false, inputs, call -> MolangValue.bool(true));
            assertEquals(1, frame.pose().boneCount());
            assertEquals(0x800040FF, frame.pose().colorsRgba()[0]);
            assertEquals(7, frame.pose().glow()[0]);
            assertTrue(frame.pose().rotations()[1] != 0);
            assertSame(frame, owner.advance(50_000_100, false, inputs, call -> fail("Duplicate slot sampled")));
            assertFalse(first.instanceId() == 0);
            var resources = new java.util.HashSet<String>();
            first.effects().forEach(effect -> resources.add(effect.resource()));
            frame.effects().forEach(effect -> resources.add(effect.resource()));
            for (int tick = 2; tick <= 22; tick++) {
                frame = owner.advance(100L + tick * 50_000_000L, false, inputs, call -> MolangValue.bool(true));
                frame.effects().forEach(effect -> resources.add(effect.resource()));
            }
            assertTrue(resources.containsAll(java.util.Set.of("ysm.play_sound", "ysm.particle", "ysm.stop_sound")));
            assertEquals(1.0, frame.variables().get("fixture_initialized"));
            assertTrue(frame.variables().get("fixture_updates") > 1);
        }
    }
}
