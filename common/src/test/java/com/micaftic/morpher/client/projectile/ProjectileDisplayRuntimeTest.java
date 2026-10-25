package com.micaftic.morpher.client.projectile;

import com.micaftic.morpher.cloud.client.CloudAssetRef;
import com.micaftic.morpher.cloud.client.CloudPlayerSelection;
import com.micaftic.morpher.cloud.client.EntityDisplayContext;
import com.micaftic.morpher.cloud.client.ProjectileFiringCandidates;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileDisplayRuntimeTest {
    private static CloudPlayerSelection selection() {
        return new CloudPlayerSelection("instance", "b".repeat(64),
                new CloudAssetRef("asset", 1, "c".repeat(64)), "ysm", "texture");
    }

    private static ProjectileFiringCandidates.Firing firing(UUID owner, String eventId) {
        return new ProjectileFiringCandidates.Firing(owner, eventId,
                Set.of(ProjectileFiringCandidates.Family.ARROW), 1, "runtime-model", selection(),
                Map.of("variant", 2f), "minecraft:bow", 0, 0, 0, 0, 0, 1);
    }

    @Test
    void worldGenerationChangesOnlyAtAWorldBoundary() {
        var runtime = new ProjectileDisplayRuntime();
        Object level = new Object();
        Object connection = new Object();
        assertNull(runtime.context());
        assertEquals(0, runtime.worldGeneration());

        runtime.enterWorld(level, connection, "minecraft:overworld");
        EntityDisplayContext first = runtime.context();
        assertNotNull(first);
        assertEquals("local", first.instanceId());
        assertEquals("minecraft:overworld", first.dimensionId());
        assertEquals(1, runtime.worldGeneration());

        runtime.enterWorld(level, connection, "minecraft:overworld");
        assertEquals(1, runtime.worldGeneration());
        assertSame(first, runtime.context());

        runtime.enterWorld(new Object(), connection, "minecraft:overworld");
        assertEquals(2, runtime.worldGeneration());
        assertNotSame(first, runtime.context());

        runtime.enterWorld(level, connection, "minecraft:overworld");
        assertEquals(3, runtime.worldGeneration());
    }

    @Test
    void leaveWorldClearsContextButKeepsTheCounterForTheNextWorld() {
        var runtime = new ProjectileDisplayRuntime();
        runtime.enterWorld(new Object(), null, "minecraft:overworld");
        runtime.leaveWorld();
        assertNull(runtime.context());
        assertEquals(1, runtime.worldGeneration());

        runtime.enterWorld(null, null, "minecraft:overworld");
        assertNull(runtime.context());
        assertEquals(1, runtime.worldGeneration());

        runtime.enterWorld(new Object(), null, "minecraft:overworld");
        assertEquals(2, runtime.worldGeneration());
    }

    @Test
    void offersRequireTheExactCurrentWorldContext() {
        var runtime = new ProjectileDisplayRuntime();
        runtime.enterWorld(new Object(), null, "minecraft:overworld");
        EntityDisplayContext context = runtime.context();
        UUID owner = UUID.randomUUID();

        assertFalse(runtime.offer(new EntityDisplayContext("local", "0".repeat(64), "local",
                "world-1", "minecraft:the_nether", 1), firing(owner, "event-a"), "identity", 0));
        assertTrue(runtime.offer(context, firing(owner, "event-a"), "identity", 0));
        assertFalse(runtime.offer(context, firing(owner, "event-b"), "identity", 1));

        runtime.leaveWorld();
        assertFalse(runtime.offer(context, firing(UUID.randomUUID(), "event-c"), "identity", 2));
    }

    @Test
    void offersAreBoundedWithoutDiscardingTheCurrentWorld() {
        var runtime = new ProjectileDisplayRuntime();
        runtime.enterWorld(new Object(), null, "minecraft:overworld");
        EntityDisplayContext context = runtime.context();
        for (int i = 0; i < 64; i++) {
            assertTrue(runtime.offer(context, firing(UUID.randomUUID(), "event-" + i), "identity", 0));
        }
        assertFalse(runtime.offer(context, firing(UUID.randomUUID(), "event-overflow"), "identity", 0));
        assertSame(context, runtime.context());
    }

    @Test
    void modelReferenceAndSnapshotValidateTheirInputs() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectileModelRef(" ", "minecraft:arrow"));
        assertThrows(IllegalArgumentException.class, () -> new ProjectileModelRef("model", "Not An Id"));
        var model = new ProjectileModelRef("model", "minecraft:arrow");

        CloudPlayerSelection withMotion = selection();
        UUID entity = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var snapshot = new ProjectileDisplaySnapshot(entity, owner, "minecraft:arrow", model,
                Map.of("variant", 1f), "minecraft:bow", "event", withMotion, "identity", 1, 0);
        assertNull(snapshot.selection().motion());

        assertThrows(IllegalArgumentException.class, () -> new ProjectileDisplaySnapshot(entity, owner,
                "minecraft:arrow", model, Map.of("bad", Float.NaN), null, "event", null, "identity", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProjectileDisplaySnapshot(entity, owner,
                "minecraft:arrow", model, Map.of(), null, "event", null, "identity", -1, 0));
    }
}
