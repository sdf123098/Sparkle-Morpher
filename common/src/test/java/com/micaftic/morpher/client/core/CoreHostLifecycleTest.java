package com.micaftic.morpher.client.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class CoreHostLifecycleTest {
    @TempDir Path directory;

    @Test void tickAndRenderDriversShareOne60HzSlotAndPauseExcludesWallTime() {
        var clock = new CoreActiveClock();
        assertEquals(0, clock.advance(100, false).slot());
        assertEquals(3, clock.advance(50_000_100, false).slot());
        assertEquals(3, clock.advance(50_000_100, false).slot());
        assertEquals(3, clock.advance(100_000_100, true).slot());
        assertEquals(3, clock.advance(9_000_000_100L, true).slot());
        assertEquals(3, clock.advance(10_000_000_100L, false).slot());
        assertEquals(6, clock.advance(10_050_000_100L, false).slot());
        assertThrows(IllegalArgumentException.class, () -> clock.advance(10, false));
    }

    @Test void retiredWorldTicketCannotCommitAndPreviewOwnsAnIndependentEpoch() {
        var world = new CoreSceneEpoch();
        var preview = new CoreSceneEpoch();
        var ticket = world.ticket();
        assertTrue(world.current(ticket));
        assertFalse(preview.current(ticket));
        world.retire();
        assertFalse(world.current(ticket));
        assertTrue(preview.current(preview.ticket()));
        var replacement = world.ticket();
        assertTrue(world.current(replacement));
        assertTrue(replacement.generation() > ticket.generation());
    }

    @Test void resolverIsConfinedToExplicitModelsParentAndRejectsClosedOwnership() throws Exception {
        Files.writeString(directory.resolve("model.json"), "fixture");
        var resolver = new ParentResourceResolver(directory.resolve("model.json"));
        try (var stream = resolver.open(new dev.spm.core.api.ResourceKey("model.json"))) {
            assertEquals("fixture", new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        Files.writeString(directory.resolve("late.json"), "not in the authorized snapshot");
        assertThrows(java.io.IOException.class, () -> resolver.open(new dev.spm.core.api.ResourceKey("late.json")));
        assertThrows(IllegalArgumentException.class, () -> new dev.spm.core.api.ResourceKey("../outside"));
        resolver.close();
        assertThrows(java.io.IOException.class, () -> resolver.open(new dev.spm.core.api.ResourceKey("model.json")));
    }
}
