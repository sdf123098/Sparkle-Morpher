package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class CoreCompatibilityDefaultsTest {
    private static Map<CoreCompatibilityDefaults.Integration, CoreCompatibilityDefaults.Presence> absent() {
        var result = new EnumMap<CoreCompatibilityDefaults.Integration, CoreCompatibilityDefaults.Presence>(CoreCompatibilityDefaults.Integration.class);
        for (var integration : CoreCompatibilityDefaults.Integration.values()) result.put(integration, CoreCompatibilityDefaults.Presence.ABSENT);
        return result;
    }
    @Test void pinnedAbsentGetterValuesPreserveTextBooleanNumericAndNullTypes() {
        var actual = CoreCompatibilityDefaults.snapshot(absent(), Map.of());
        var expected = new java.util.HashMap<String, MolangValue>();
        for (String field : new String[]{"ctrl.has_sophisticated_backpack", "ctrl.carryon_is_princess", "ctrl.create_hanging_skyhook",
                "ctrl.swem_is_ride", "ctrl.tac_hold_gun", "ctrl.tac_is_fire", "ctrl.tac_is_aim", "ctrl.tac_is_reload", "ctrl.tac_is_melee", "ctrl.tac_is_draw",
                "tlm.is_begging", "tlm.is_sitting", "tlm.has_backpack", "tlm.is_statue", "tlm.is_garage_kit"}) expected.put(field, MolangValue.bool(false));
        for (String field : new String[]{"ctrl.carryon_type", "ctrl.bcombat_attack_animation", "ctrl.iss_animation", "ctrl.parcool_state", "ctrl.slashblade_animation", "ctrl.swem_state",
                "ctrl.tac_gun_type", "ctrl.tac_gun_id", "ctrl.tac_fire_mode", "tlm.task_id", "tlm.schedule", "tlm.activity", "tlm.game_statue", "tlm.backpack_type", "tlm.show_item"}) expected.put(field, MolangValue.text(""));
        for (String field : new String[]{"ctrl.im_pitch", "ctrl.im_volume", "ctrl.im_current", "ctrl.im_delta", "ctrl.im_time", "tlm.favorability_point", "tlm.favorability_level", "tlm.gomoku_win_count"}) expected.put(field, MolangValue.number(0));
        expected.put("tlm.gomoku_rank", MolangValue.number(1));
        expected.put("tlm.is_entity", MolangValue.bool(true));
        expected.put("ysm.dump_curios", MolangValue.missing());
        assertEquals(expected, actual);
        assertThrows(UnsupportedOperationException.class, () -> actual.put("tlm.is_entity", MolangValue.bool(false)));
        assertFalse(actual.containsKey("ysm.has_any_curios")); // Functions are not getter values.
    }
    @Test void everyPresenceMustBeExplicitAndPresentWithoutProviderCannotUseDefaults() {
        var presence = absent();
        presence.remove(CoreCompatibilityDefaults.Integration.TLM);
        assertThrows(IllegalArgumentException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of()));
        presence.put(CoreCompatibilityDefaults.Integration.TLM, CoreCompatibilityDefaults.Presence.UNKNOWN);
        assertThrows(IllegalStateException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of()));
        presence.put(CoreCompatibilityDefaults.Integration.TLM, CoreCompatibilityDefaults.Presence.PRESENT);
        assertThrows(IllegalStateException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of()));
    }
    @Test void presentProviderIsFrozenAndCannotOmitOrInjectGetterKeys() {
        var presence = absent();
        var local = new java.util.HashMap<>(CoreCompatibilityDefaults.snapshot(presence, Map.of()));
        local.keySet().removeIf(key -> !key.startsWith("tlm."));
        local.put("tlm.favorability_point", MolangValue.number(42));
        presence.put(CoreCompatibilityDefaults.Integration.TLM, CoreCompatibilityDefaults.Presence.PRESENT);
        var actual = CoreCompatibilityDefaults.snapshot(presence, Map.of(CoreCompatibilityDefaults.Integration.TLM, local));
        local.put("tlm.favorability_point", MolangValue.number(99));
        assertEquals(MolangValue.number(42), actual.get("tlm.favorability_point"));
        local.remove("tlm.is_entity");
        assertThrows(IllegalArgumentException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of(CoreCompatibilityDefaults.Integration.TLM, local)));
        local.put("tlm.is_entity", MolangValue.bool(true)); local.put("ctrl.run", MolangValue.bool(true));
        assertThrows(IllegalArgumentException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of(CoreCompatibilityDefaults.Integration.TLM, local)));
    }
    @Test void providerForAbsentModAndNullPresenceRejectRatherThanOverrideFacts() {
        var presence = absent();
        assertThrows(IllegalArgumentException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of(CoreCompatibilityDefaults.Integration.TLM, Map.of())));
        presence.put(CoreCompatibilityDefaults.Integration.TLM, null);
        assertThrows(IllegalArgumentException.class, () -> CoreCompatibilityDefaults.snapshot(presence, Map.of()));
    }
}
