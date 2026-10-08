package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.core.display.PlayerDisplayState;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudPlayerDisplayStateTest {
    private static final PlayerDisplayState STATE = new PlayerDisplayState(42, 18.5f, 20f, 17, java.util.Map.of("minecraft:speed", 2), false, .25f, 0f, 1f, false);
    @Test void jsonPreservesUnknownsAndRejectsImplicitNumericConversions() {
        var dto = new CloudPlayerDisplayState("scope", "epoch", "minecraft:overworld", STATE, 1000, 61000);
        JsonObject json = dto.toJson(8192, 32);
        json.addProperty("server_time_unix_ms", 1000); json.addProperty("expires_at_unix_ms", 61000);
        assertEquals(dto, CloudPlayerDisplayState.fromJson(json));
        json.getAsJsonObject("state").addProperty("experience_level", "42");
        assertThrows(IllegalArgumentException.class, () -> CloudPlayerDisplayState.fromJson(json));
        assertThrows(IllegalArgumentException.class, () -> dto.toJson(10, 32));
        assertThrows(IllegalArgumentException.class, () -> dto.toJson(8192, 0));
    }
    @Test void receiptLifetimeCannotBeRenewedByReplayingAQueryAndContextChangesHideInputs() {
        var ledger = new CloudPlayerDisplayLedger(); UUID uuid = UUID.randomUUID();
        var dto = new CloudPlayerDisplayState("scope", "epoch", "minecraft:overworld", STATE, 1000, 2000);
        assertTrue(ledger.receive(uuid, 3, dto, 0));
        assertEquals(STATE, ledger.get(uuid, "scope", "epoch", "minecraft:overworld", 1));
        assertEquals(PlayerDisplayState.UNKNOWN, ledger.get(uuid, "scope", "other", "minecraft:overworld", 1));
        assertTrue(ledger.receive(uuid, 3, dto, 900_000_000));
        assertEquals(PlayerDisplayState.UNKNOWN, ledger.get(uuid, "scope", "epoch", "minecraft:overworld", 1_000_000_000));
        assertFalse(ledger.receive(uuid, 2, dto, 1_000_000_001));
        ledger.clear(); assertEquals(PlayerDisplayState.UNKNOWN, ledger.get(uuid, "scope", "epoch", "minecraft:overworld", 1));
    }
}
